#!/bin/sh
# =============================================================================
#  Genera /config.js al arrancar el contenedor
# =============================================================================
#  EL PROBLEMA QUE RESUELVE
#  Vite reemplaza las variables de entorno cuando CONSTRUYE, no cuando ejecuta.
#  Si la direccion de la API entrara por ese camino, quedaria escrita adentro del
#  paquete de JavaScript, y cambiar de puerto o de maquina obligaria a construir
#  la imagen de nuevo. La misma imagen no serviria para dos entornos, que es
#  justamente lo que una imagen de Docker deberia permitir.
#
#  La solucion es este archivo suelto, que index.html carga ANTES del paquete y
#  que se escribe recien ahora, cuando ya se conocen las variables de entorno.
#
#  La imagen de nginx ejecuta todo lo que encuentre en /docker-entrypoint.d/
#  antes de levantar el servidor, asi que alcanza con dejar el guion ahi.
#
#  ACA NO VA NINGUN SECRETO. Este archivo lo descarga el navegador de cualquiera
#  que entre al sitio. Lleva direcciones y el identificador del cliente publico de
#  Keycloak, que no son secretos. Una clave si lo seria, y por eso no hay ninguna:
#  este frontend nunca necesita una (para eso usa PKCE).
# =============================================================================

set -e

DESTINO=/usr/share/nginx/html/config.js

cat > "$DESTINO" <<FIN
window.__CONFIG__ = {
    apiUrl: '${API_BASE_URL:-http://localhost:8080}',
    keycloakUrl: '${KEYCLOAK_PUBLIC_URL:-http://localhost:8180}',
    keycloakRealm: '${KEYCLOAK_REALM:-camelracing}',
    keycloakClientId: '${KEYCLOAK_CLIENT_ID:-camelracing-web}'
};
FIN

echo "config.js generado apuntando a la API en ${API_BASE_URL:-http://localhost:8080}"
