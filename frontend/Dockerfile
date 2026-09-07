# =============================================================================
#  IMAGEN DEL FRONTEND
# =============================================================================
#  Construccion en dos etapas, con el mismo criterio que el Dockerfile de la API:
#  la etapa que COMPILA no es la que se publica.
#
#  La diferencia con el backend es que aca la separacion es todavia mas marcada.
#  Node hace falta para construir y despues no hace falta nunca mas: el resultado
#  es un puñado de archivos estaticos. La imagen final no tiene Node, ni npm, ni
#  node_modules, ni el codigo fuente. Tiene nginx y una carpeta con HTML, CSS y
#  JavaScript ya empaquetados.
#
#  El ahorro no es solo de tamano (unos 50 MB contra mas de 1 GB): es de
#  superficie. Lo que no esta en la imagen no se puede explotar ni hay que
#  parchearlo.
# =============================================================================


# -----------------------------------------------------------------------------
#  ETAPA 1: construir
# -----------------------------------------------------------------------------
FROM node:24-alpine AS construccion

WORKDIR /app

# Primero SOLO los archivos de dependencias, y despues el codigo. El orden
# importa por la cache de Docker: mientras package.json no cambie, esta capa se
# reutiliza y no se vuelven a bajar las dependencias. Copiando todo junto,
# cualquier cambio en un componente obligaria a reinstalar todo de cero.
COPY package.json package-lock.json ./

# "npm ci" y no "npm install": instala EXACTAMENTE las versiones del
# package-lock.json y falla si el lock no coincide con el package.json. Es lo que
# hace que la imagen que se construye hoy tenga las mismas dependencias que la
# que se construyo la semana pasada. "npm install" puede actualizar versiones
# menores por su cuenta y arruinar esa garantia justo antes de una entrega.
RUN npm ci

COPY . .

# Deja en /app/dist el sitio ya empaquetado: HTML, CSS y JavaScript minificados,
# con el nombre de cada archivo incluyendo una huella de su contenido.
RUN npm run build


# -----------------------------------------------------------------------------
#  ETAPA 2: servir
# -----------------------------------------------------------------------------
FROM nginx:1.29-alpine

# La configuracion del servidor. Lo importante que resuelve esta explicado
# adentro del archivo: sin ella, recargar la pagina en cualquier direccion que no
# sea la raiz devuelve 404.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# El sitio construido en la etapa anterior. Esta es la unica linea que trae algo
# de aquella imagen: todo lo demas (Node, las dependencias, el codigo fuente)
# queda afuera.
COPY --from=construccion /app/dist /usr/share/nginx/html

# El guion que genera /config.js con las direcciones que correspondan al entorno.
# La imagen de nginx ejecuta todo lo que encuentra en esta carpeta antes de
# arrancar el servidor, asi que no hace falta reemplazar el punto de entrada.
COPY docker/config.sh /docker-entrypoint.d/40-config.sh
RUN chmod +x /docker-entrypoint.d/40-config.sh

EXPOSE 80

# El healthcheck le da a compose una forma de saber si el sitio esta realmente
# respondiendo, y no solo si el proceso existe. wget viene en la imagen de
# Alpine; curl no.
#
# VA 127.0.0.1 Y NO localhost, Y NO ES INDISTINTO.
# Adentro del contenedor, "localhost" resuelve primero a la direccion IPv6 (::1),
# y este nginx escucha solo en IPv4. El resultado es que wget corta con
# "can't connect to remote host: Connection refused" y el contenedor queda marcado
# como unhealthy para siempre, mientras desde afuera el sitio se ve perfecto.
#
# Cuesta encontrarlo porque el sintoma apunta al lugar equivocado: parece que
# nginx no esta levantado cuando en realidad esta atendiendo sin problemas en la
# otra familia de direcciones. La alternativa seria agregarle "listen [::]:80" al
# nginx.conf; se prefiere fijar la direccion aca porque no cambia el
# comportamiento del servidor solo para conformar a una prueba.
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
