import Keycloak from 'keycloak-js';
import { config } from '../config.js';

// =============================================================================
//  La conexion con Keycloak
// =============================================================================
//  ESTA APLICACION NO TIENE PANTALLA DE CONTRASENA, Y ES A PROPOSITO
//
//  El backend no expone /api/auth/login: delega la autenticacion entera en
//  Keycloak y solo verifica el token que le llega. El frontend hace lo mismo.
//  Cuando el usuario aprieta "Entrar", el navegador se va a la pantalla de
//  Keycloak, escribe ahi su contrasena y vuelve con un codigo que se canjea por
//  un token.
//
//  La consecuencia practica es la que importa: la contrasena NUNCA pasa por este
//  codigo. No hay ningun formulario nuestro que la reciba, asi que no hay forma
//  de que la registremos, la mandemos a la API o la dejemos en un log por error.
//
//  POR QUE PKCE
//  El cliente camelracing-web es un cliente "publico": todo su codigo lo descarga
//  el navegador, asi que no puede guardar ninguna clave. PKCE resuelve eso: el
//  navegador inventa un valor al azar, manda su huella al pedir el login y el
//  valor original recien al canjear el codigo. Si alguien interceptara el codigo
//  de la barra de direcciones, sin ese valor no le sirve de nada.
// =============================================================================

const keycloak = new Keycloak({
    url: config.keycloakUrl,
    realm: config.keycloakRealm,
    clientId: config.keycloakClientId
});

/**
 * La inicializacion, protegida contra la doble ejecucion.
 *
 * React en modo desarrollo monta cada componente DOS VECES a proposito, para
 * hacer visibles los efectos que no se limpian bien. Keycloak, en cambio, no
 * admite que le llamen init() dos veces: la segunda corta con "A 'Keycloak'
 * instance can only be initialized once".
 *
 * Guardar la promesa en una variable de modulo lo resuelve: la segunda llamada
 * recibe la misma promesa que la primera en vez de empezar de nuevo.
 */
let inicializacion = null;

export function iniciarKeycloak() {
    if (inicializacion === null) {
        inicializacion = keycloak.init({
            // check-sso y no login-required: la aplicacion tiene que poder
            // mostrar su pantalla de bienvenida a alguien que no entro todavia.
            // Con login-required, entrar a la direccion mandaria directo a
            // Keycloak sin que el usuario entienda por que.
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
            pkceMethod: 'S256'
        });
    }
    return inicializacion;
}

export default keycloak;
