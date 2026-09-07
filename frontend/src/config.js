/**
 * Lee la configuracion que dejo /config.js en window, con valores por defecto.
 *
 * Los valores por defecto apuntan al stack local levantado con docker compose,
 * asi que la aplicacion arranca aunque /config.js no se haya cargado. Es
 * deliberado: preferimos que en desarrollo funcione sin ceremonia antes que
 * fallar con una pantalla en blanco y ningun mensaje.
 */
const puesta = window.__CONFIG__ ?? {};

export const config = {
    apiUrl: puesta.apiUrl ?? 'http://localhost:8080',
    keycloakUrl: puesta.keycloakUrl ?? 'http://localhost:8180',
    keycloakRealm: puesta.keycloakRealm ?? 'camelracing',
    keycloakClientId: puesta.keycloakClientId ?? 'camelracing-web'
};
