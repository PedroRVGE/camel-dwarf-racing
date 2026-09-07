// =============================================================================
//  Configuracion de ejecucion (valores para desarrollo)
// =============================================================================
//  Este archivo se carga desde index.html ANTES del paquete de la aplicacion, y
//  deja la configuracion colgada del objeto window.
//
//  En Docker no se usa este: el contenedor genera uno equivalente al arrancar,
//  con los valores del .env (ver frontend/docker/config.sh). Los de aca son los
//  que sirven para "npm run dev" contra el stack levantado con docker compose.
//
//  Aca NO va ningun secreto, y no es una omision: este archivo lo descarga el
//  navegador de cualquiera que entre. El identificador del cliente de Keycloak no
//  es un secreto (es un cliente publico, por eso usa PKCE); una clave si lo seria.
// =============================================================================
window.__CONFIG__ = {
    apiUrl: 'http://localhost:8080',
    keycloakUrl: 'http://localhost:8180',
    keycloakRealm: 'camelracing',
    keycloakClientId: 'camelracing-web'
};
