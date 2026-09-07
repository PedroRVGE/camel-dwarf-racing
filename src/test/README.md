# Las pruebas del proyecto

71 pruebas automatizadas, repartidas en tres capas. El enunciado pide al menos 15
"significativas" y aclara que las que solo verifican getters y setters no cuentan:
acá no hay ninguna de esas. Cada prueba comprueba una regla de negocio, una
consulta contra la base, una restricción del esquema o una regla de acceso.

## Cómo se corren

```bash
./gradlew test                      # las tres capas
./gradlew test -Pcapa=unit          # solo las unitarias (segundos, sin Docker)
./gradlew test -Pcapa=integracion    # las de base de datos (necesitan Docker)
./gradlew test -Pcapa=e2e            # el sistema completo (necesita Docker)
```

El informe HTML queda en `build/reports/tests/test/index.html`.

**Las dos últimas necesitan Docker corriendo.** Testcontainers levanta los
contenedores solo y los apaga al terminar; no hay que preparar nada a mano. La
primera corrida baja las imágenes (`postgres:15-alpine` y `keycloak:26.4`) y tarda
bastante más que las siguientes.

En Windows, si `./gradlew` no encuentra Java:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
```

## Las tres capas

| Capa | Cuántas | Qué necesita | Cuánto tarda | Qué prueba |
|---|---|---|---|---|
| `unit` | 44 | nada | segundos | Las reglas de negocio, con los colaboradores reemplazados por mocks |
| `integracion` | 24 | Docker | ~1 min | Las consultas y las restricciones, contra Postgres de verdad |
| `e2e` | 3 | Docker | ~3 min | El sistema completo, con Keycloak emitiendo tokens firmados |

Las etiquetas son constantes en `support/Capas.java` y no texto suelto: una
etiqueta mal escrita no falla, simplemente deja el test fuera de la corrida sin
avisar, y esa es la peor forma de perder una prueba.

### `unit` — sin Spring, sin Docker

Prueban las decisiones: cuándo un servicio deja pasar, cuándo corta y en qué estado
deja las cosas. El repositorio es un mock, así que un test verde acá **no** dice
nada sobre si la consulta funciona contra Postgres. Para eso está la capa de abajo.

También prueban las validaciones de los DTO construyendo el validador de Jakarta a
mano, sin levantar Spring: esas reglas las declaran las anotaciones del record y no
dependen del framework.

### `integracion` — Postgres de verdad, nunca H2

El enunciado permite H2 para pruebas y acá **no se usa**, ni siquiera como atajo.
Hay tres razones concretas, y las tres están puestas a prueba en esta carpeta:

1. La consulta de la tabla de posiciones agrupa con `SUM` y `CASE`. H2 es mucho más
   permisivo que Postgres con el `GROUP BY`, así que un test verde en H2 no
   probaría que la consulta anda en producción.
2. La consulta de la bitácora usa `COALESCE` para esquivar un problema de tipos que
   es **específico de Postgres** (`could not determine data type of parameter`). En
   H2 ese problema no existe, y el test que lo cuida no cuidaría nada.
3. Las migraciones de Flyway usan sintaxis que H2 no entiende. Habría que mantener
   un segundo esquema solo para los tests, y un segundo esquema es un esquema que
   se desactualiza.

El contenedor se levanta **una sola vez** para todas las clases (patrón "singleton
container", en `support/PostgresTestBase.java`), y cada test corre dentro de una
transacción que se deshace al terminar. Se comparte el contenedor, no el contenido.

### `e2e` — el stack completo

La aplicación arriba en un puerto al azar, su propio Postgres, y un Keycloak real
importando **el mismo `realm-camelracing.json` que usa `docker compose`** (por eso
`build.gradle` suma la carpeta `keycloak/` al classpath de los tests: con una copia
aparte, estos tests terminarían validando una configuración que ya no es la que
corre en serio).

Es la única capa donde se prueba la cadena completa de autenticación: Keycloak
valida la contraseña y firma el token, la aplicación baja la clave pública,
verifica la firma, compara el emisor y traduce `realm_access.roles` a los
`ROLE_...` de Spring Security. Cualquiera de esos pasos puede estar mal y la
aplicación igual arranca; el síntoma aparece recién cuando todo devuelve 401 o 403.

**Tiene su propia base de datos, separada de la de integración.** No es un
descuido: una llamada HTTP de verdad no se puede deshacer, así que estos tests
dejan datos. Compartiendo contenedor, los competidores que crea el recorrido
completo aparecían en la tabla de posiciones que cuenta `StandingsRepositoryIT`, y
ese test fallaba **solo al correr la suite completa**, que es la peor forma de
fallar: cada clase por separado pasaba y el error parecía intermitente.

## El andamiaje: `support/`

| Archivo | Para qué |
|---|---|
| `Capas.java` | Los nombres de las tres etiquetas, como constantes |
| `Datos.java` | Fábricas de objetos válidos, para que cada test cambie solo lo que le importa |
| `PostgresTestBase.java` | El contenedor de Postgres compartido por la capa de integración |
| `E2ETestBase.java` | El stack completo (Postgres propio + Keycloak) y el pedido de tokens |

Sobre `Datos.sinId(...)`: las fábricas asignan un id porque los tests unitarios lo
necesitan para configurar los mocks, pero una entidad con id ya puesto es, para
Hibernate, una entidad *desprendida*, y persistirla corta con `detached entity
passed to persist`. Antes de guardar hay que sacárselo.

## Dónde está cada prueba que pide el enunciado

| Enunciado | Dónde | Capa |
|---|---|---|
| Create a valid competitor | `CompetitorServiceTest.creaUnCompetidorValido` | unit |
| Reject a competitor with invalid weight | `CompetitorRequestValidacionTest.rechazaPesoInvalido` | unit |
| Reject a duplicated nickname | `CompetitorServiceTest.rechazaApodoRepetido` + `CompetitorRepositoryIT.elApodoEsUnicoEnLaBase` | unit + integración |
| Create a valid race | `RaceServiceTest.creaUnaCarreraValida` | unit |
| Reject a race scheduled in the past | `RaceRequestValidacionTest.rechazaCarreraEnElPasado` | unit |
| Register an active competitor successfully | `RegistrationServiceTest.inscribeUnCompetidorActivo` | unit |
| Reject a suspended competitor | `RegistrationServiceTest.rechazaCompetidorSuspendido` | unit |
| Reject a duplicated registration | `RegistrationServiceTest.rechazaInscripcionRepetida` | unit |
| Reject registration after the deadline | `RegistrationServiceTest.rechazaInscripcionFueraDePlazo` | unit |
| Record a valid result | `ResultServiceTest.cargaUnResultadoValido` | unit |
| Reject two winners in one race | `ResultServiceTest.noPuedeHaberDosGanadores` + `RaceResultRepositoryIT.noPuedeHaberDosGanadores` | unit + integración |
| Prevent a viewer from creating a race | `SeguridadIT.elViewerNoPuedeCrearCarreras` + `RecorridoCompletoE2ETest.elViewerLeePeroNoCrea` | integración + e2e |
| Allow an administrator to create a race | `SeguridadIT.elAdminSiPuedeCrearCarreras` | integración |
| Return 401 without a valid token | `SeguridadIT.sinTokenDevuelve401` + `RecorridoCompletoE2ETest.sinTokenNoSePasa` | integración + e2e |
| Return 404 for a missing resource | `SeguridadIT.unIdInexistenteDevuelve404` | integración |

Las cuatro reglas que aparecen **dos veces** están duplicadas a propósito, porque
son las que están implementadas dos veces: una en el servicio, que es la que da el
mensaje entendible, y otra como restricción de la base o como validación de la
firma, que es la que garantiza que la regla no se pueda violar por fuera del camino
feliz de la API.

## Las reglas propias del proyecto que también se prueban

Más allá de la lista del enunciado:

- **La carrera no se puede dar por terminada si falta cargar resultados**, y el
  mensaje dice cuántos faltan (`RaceServiceTest`, y de punta a punta en el e2e).
- **Las estadísticas se recalculan desde cero**, nunca se incrementan
  (`ResultServiceTest.cargaUnResultadoValido` mira el competidor guardado).
- **La escala de puntos está escrita dos veces**, en Java y en SQL, porque la suma
  la tiene que hacer la base para poder ordenar y paginar. `ResultStatusTest` fija
  la versión de Java y `StandingsRepositoryIT` la de SQL, **contra los mismos
  números**: si alguien cambia el puntaje en un solo lado, uno de los dos se pone
  rojo.
- **El que no largó no suma derrota**: no compitió, así que no perdió. El
  descalificado y el que abandonó sí.
- **La baja de un competidor es lógica**, no física: pasa a `RETIRED` y su
  historial sigue existiendo.
- **La paginación de la tabla de posiciones** cuenta participantes y no filas de
  resultado (por eso la consulta declara su propio `countQuery` con
  `COUNT(DISTINCT ...)`).

## Un detalle de Spring Boot 4 que hace perder tiempo

Las anotaciones de test cambiaron de paquete al partirse `spring-boot-autoconfigure`
en un módulo por tecnología, así que cualquier ejemplo de Boot 3 que se copie de
internet no compila:

| | Boot 3 | Boot 4 |
|---|---|---|
| `@DataJpaTest` | `...boot.test.autoconfigure.orm.jpa` | `...boot.data.jpa.test.autoconfigure` |
| `TestEntityManager` | `...boot.test.autoconfigure.orm.jpa` | `...boot.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `...boot.test.autoconfigure.jdbc` | `...boot.jdbc.test.autoconfigure` |
| `@AutoConfigureMockMvc` | `...boot.test.autoconfigure.web.servlet` | `...boot.webmvc.test.autoconfigure` |

Es el mismo reacomodamiento que obliga a declarar `spring-boot-flyway` a mano en
`build.gradle`.

Dos cosas más de la misma familia, por si aparecen:

- `TestRestTemplate` **no se autoconfigura**. Los tests e2e arman su propio cliente
  HTTP, y de paso resuelven que la fábrica de pedidos soporte `PATCH`: la que se
  elige por defecto se apoya en `HttpURLConnection`, que no conoce ese verbo y corta
  con `Invalid HTTP method: PATCH`. Este proyecto usa `PATCH` para todos los cambios
  de estado.
- Keycloak 26 en modo desarrollo tarda **más de un minuto** en levantar, y el plazo
  por defecto de Testcontainers es de 60 segundos. Sin subirlo, el test falla con un
  mensaje que apunta al lugar equivocado (`Timed out waiting for URL to be
  accessible .../health/ready`), que parece un problema del endpoint de salud cuando
  en realidad Keycloak todavía estaba arrancando.
