# =============================================================================
# ETAPA 1: BUILD
# =============================================================================
#
# La imagen base DEBE traer el mismo Java que pide el toolchain de build.gradle
# (languageVersion = 25). No alcanza con poner un JDK menor confiando en que
# Gradle se baje solo el 25: la descarga automatica de toolchains necesita el
# plugin "foojay-resolver-convention" declarado en settings.gradle, que este
# proyecto no tiene. Sin el, Gradle falla con:
#   "No matching toolchains found for requested specification: {languageVersion=25}"
#
# Alpine en vez de la imagen de Ubuntu: pesa menos y, sobre todo, tiene que ser
# la MISMA familia que la etapa de runtime de abajo. Alpine usa musl en lugar de
# glibc, y el runtime de Java que se arma mas abajo con jlink queda enlazado
# contra la musl de ESTA imagen: si las dos puntas no son compatibles, el
# contenedor no arranca.
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# 1) Primero SOLO los archivos de build: Docker cachea esta capa y no la vuelve
#    a ejecutar mientras no toques build.gradle.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# 2) Despues el codigo fuente, para que al cambiar una clase no se invaliden
#    las capas anteriores.
COPY src src

# --mount=type=cache guarda el cache de Gradle (~/.gradle) entre builds, asi no
# se vuelven a descargar todas las dependencias cada vez.
#
# bootJar y no "build -x test", por dos motivos:
#
#   - "build" corre los tests, y los de este proyecto levantan contenedores con
#     Testcontainers. Adentro de un "docker build" no hay un Docker donde
#     levantarlos, asi que fallarian siempre. Los tests se corren aparte, con
#     ./gradlew test, no durante la construccion de la imagen.
#   - "build" genera DOS jars: el ejecutable y un camelracing-0.0.1-SNAPSHOT-plain.jar
#     de unos 70 KB con solo las clases del proyecto. Como mas abajo se copia con
#     un comodin (*.jar), Docker tendria dos archivos para elegir y no esta
#     definido cual gana: si agarra el "plain", la imagen se construye igual y el
#     contenedor muere al arrancar con "no main manifest attribute".
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

# 3) Se parte el jar ejecutable en capas.
#
# Adentro del .jar conviven dos cosas muy distintas: decenas de MB de
# dependencias (Spring, Hibernate, Flyway, el driver de Postgres...) que casi
# nunca cambian, y unos cientos de KB de clases propias que cambian en cada
# commit. Copiandolo entero como un solo archivo, tocar una linea de codigo
# invalida la capa completa y hay que volver a subir y bajar todo.
#
# Separadas, un cambio de codigo solo mueve la capa chica.
# OJO: esto NO achica la imagen final (la suma es la misma). Lo que mejora es lo
# que se transfiere en cada push/pull y el tiempo de reconstruccion.
RUN java -Djarmode=tools -jar build/libs/*.jar \
        extract --layers --launcher --destination extracted

# 4) Se arma un runtime de Java a medida con jlink.
#
# ACA ESTA EL GRUESO DEL AHORRO. Un JDK completo trae el compilador (javac),
# jshell, herramientas de debug y profiling: nada de eso hace falta para
# ejecutar un .jar. jlink arma un Java que solo tiene los modulos pedidos.
# Resultado: unos 60 MB en vez de los ~450 MB de una imagen con JRE completo.
#
# Sobre --add-modules: se pide "java.se", que es el agregador de TODA la API
# estandar de Java. Se puede afinar mas listando modulo por modulo
# (java.sql, java.naming, java.xml...) y bajar a ~40 MB, pero es peligroso aca:
# Spring y Hibernate cargan clases por reflexion, asi que un modulo que falte no
# se nota al compilar ni al arrancar, sino cuando alguien pega en el endpoint que
# lo usaba. java.se evita esa clase de bug y el ahorro extra seria chico.
# Los jdk.* que se agregan aparte no estan dentro de java.se:
#   - jdk.unsupported  -> sun.misc.Unsafe, que usan Spring y Hibernate
#   - jdk.crypto.ec    -> curvas elipticas, necesarias para TLS moderno y para
#                         verificar la firma de los tokens de Keycloak
#   - jdk.management   -> metricas de la JVM, que es de donde Actuator saca los
#                         datos de /actuator/health
#   - jdk.jfr          -> Java Flight Recorder, para diagnosticar en produccion
#
# Las tres opciones de recorte sacan simbolos de debug, headers de C y paginas
# de manual, que en un contenedor no los usa nadie. --compress=zip-6 comprime
# los modulos (en Java 21+ reemplazo al viejo --compress=2).
RUN jlink \
      --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.jfr \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --compress=zip-6 \
      --output /javaruntime

# =============================================================================
# ETAPA 2: RUNTIME
# =============================================================================
#
# Alpine pelado: ~10 MB, contra los ~450 MB de una imagen con JRE completo. Como
# el Java lo trae la etapa anterior via jlink, esta imagen no necesita ningun
# paquete de Java instalado.
#
# Ver la nota de la etapa 1 sobre musl: esta version y la del builder tienen que
# ser compatibles. Hoy temurin:25-jdk-alpine resuelve a Alpine 3.23 y aca se usa
# 3.22; las dos traen musl 1.2.5, asi que la combinacion funciona. Lo prolijo es
# dejar las dos fijas en el mismo numero para que no se separen solas cuando
# temurin actualice su imagen.
FROM alpine:3.22

# curl hace falta para el healthcheck de compose.yml, que consulta
# /actuator/health. Alpine no lo trae, y sin el el contenedor queda marcado
# "unhealthy" para siempre aunque la aplicacion este perfecta: Docker no puede
# ejecutar el comando del healthcheck y lo cuenta como fallo. Son ~2 MB.
RUN apk add --no-cache curl

# El runtime a medida se copia a /opt/java y se pone en el PATH para poder
# invocar "java" a secas en el ENTRYPOINT.
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=builder /javaruntime $JAVA_HOME

WORKDIR /app

# No correr como root: si alguien logra ejecutar codigo dentro del contenedor,
# queda limitado a un usuario sin privilegios.
# En Alpine no existe useradd (es de shadow, que no viene instalado): el
# equivalente de BusyBox es adduser/addgroup, y -S crea cuentas de sistema.
RUN addgroup -S spring && adduser -S -G spring -s /sbin/nologin spring
USER spring

# El orden va de menos a mas volatil, porque Docker cachea capa por capa: las
# dependencias primero (casi nunca cambian) y el codigo al final, asi un commit
# tuyo solo invalida la ultima capa.
COPY --from=builder --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/application/ ./

EXPOSE 8080

# Con el jar partido en capas ya no hay un app.jar suelto que ejecutar: se
# arranca el launcher de Spring Boot, que arma el classpath desde los
# directorios copiados arriba.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
