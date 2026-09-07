import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import keycloak, { iniciarKeycloak } from './keycloak.js';
import { PERMISOS } from './permisos.js';
import { instalarProveedorDeToken } from '../api/cliente.js';

// =============================================================================
//  La sesion del usuario, disponible en toda la aplicacion
// =============================================================================
//  El enunciado pide "state management: stores session, user role and relevant
//  screen data". Esta es la parte de la sesion: quien entro, con que roles, y
//  como entrar y salir. Los datos de cada pantalla los maneja cada pantalla.
//
//  DONDE VIVE EL TOKEN, Y POR QUE AHI
//  El token queda en memoria, adentro del objeto de keycloak-js. NO se guarda en
//  localStorage ni en una cookie que JavaScript pueda leer.
//
//  La razon es concreta: cualquier script que llegue a ejecutarse en la pagina
//  puede leer localStorage entero y llevarse el token, que es una credencial
//  valida por si sola. En memoria, el token muere al cerrar la pestana, y la
//  sesion se recupera al recargar preguntandole a Keycloak si la cookie de SESION
//  sigue viva, que es lo que hace el check-sso silencioso.
//
//  El costo es esa consulta de ida y vuelta en cada recarga. Es barato, y es lo
//  que el enunciado llama "store and send the authentication token securely".
// =============================================================================

const ContextoDeAutenticacion = createContext(null);

export function AuthProvider({ children }) {
    // "listo" separa dos situaciones que no se pueden confundir: todavia no
    // sabemos si hay sesion, y ya sabemos que no hay. Sin esta bandera, la
    // aplicacion mostraria por un instante la pantalla de bienvenida a alguien
    // que si estaba autenticado, y eso se ve como un parpadeo en cada recarga.
    const [listo, setListo] = useState(false);
    const [autenticado, setAutenticado] = useState(false);

    useEffect(() => {
        iniciarKeycloak()
            .then((sesion) => setAutenticado(sesion))
            .catch((error) => {
                // Si Keycloak no contesta, la aplicacion queda utilizable en su
                // pantalla de bienvenida en vez de morir en blanco. El usuario ve
                // que "Entrar" falla, que es mas informacion que una pagina vacia.
                console.error('No se pudo hablar con Keycloak', error);
                setAutenticado(false);
            })
            .finally(() => setListo(true));
    }, []);

    /**
     * Le entrega al cliente de la API un token siempre fresco.
     *
     * updateToken(30) quiere decir: "si al token le quedan menos de 30 segundos
     * de vida, renovalo antes de devolvermelo". Sin ese margen, un pedido que
     * sale con un token al que le quedaba un segundo llega vencido y vuelve 401,
     * y el usuario ve un error sin haber hecho nada mal.
     *
     * Si la renovacion falla es porque la sesion se termino de verdad; ahi si
     * corresponde volver a la pantalla de entrada.
     */
    useEffect(() => {
        instalarProveedorDeToken(async () => {
            if (!keycloak.authenticated) {
                return null;
            }
            try {
                await keycloak.updateToken(30);
                return keycloak.token;
            } catch {
                setAutenticado(false);
                return null;
            }
        });
    }, []);

    // Los roles se releen cuando cambia la autenticacion, que es el momento en
    // que el token aparece o desaparece.
    const roles = useMemo(
        () => keycloak.tokenParsed?.realm_access?.roles ?? [],
        [autenticado, listo]
    );

    /**
     * Responde si el usuario puede hacer una accion de la tabla de permisos.
     *
     * Los roles salen del token y no de /api/auth/profile a proposito: la
     * interfaz necesita saber que botones dibujar ANTES de hacer ningun pedido, y
     * el token ya trae esa informacion firmada por Keycloak.
     */
    const puede = useCallback((accion) => {
        const permitidos = PERMISOS[accion];
        if (!permitidos) {
            // Una accion mal escrita se niega en vez de permitirse. Si el nombre
            // tiene un error de tipeo el boton desaparece y se nota enseguida; al
            // reves pasaria desapercibido hasta que alguien se coma un 403.
            console.warn('Accion desconocida en la tabla de permisos: ' + accion);
            return false;
        }
        return permitidos.some((rol) => roles.includes(rol));
    }, [roles]);

    const entrar = useCallback(() => keycloak.login(), []);

    // Al salir, Keycloak cierra tambien SU sesion, no solo la de esta pestana. Si
    // solo borraramos el token de memoria, el siguiente "Entrar" volveria a entrar
    // sin pedir contrasena, porque la cookie de Keycloak seguiria viva, y el
    // usuario creeria que cerro sesion cuando en realidad no.
    const salir = useCallback(
        () => keycloak.logout({ redirectUri: window.location.origin }),
        []
    );

    const valor = useMemo(() => ({
        listo,
        autenticado,
        roles,
        usuario: keycloak.tokenParsed?.preferred_username ?? null,
        nombre: keycloak.tokenParsed?.name || keycloak.tokenParsed?.preferred_username || null,
        puede,
        entrar,
        salir
    }), [listo, autenticado, roles, puede, entrar, salir]);

    return (
        <ContextoDeAutenticacion.Provider value={valor}>
            {children}
        </ContextoDeAutenticacion.Provider>
    );
}

/** El acceso a la sesion desde cualquier componente. */
export function useAuth() {
    const contexto = useContext(ContextoDeAutenticacion);
    if (contexto === null) {
        throw new Error('useAuth se uso fuera de <AuthProvider>');
    }
    return contexto;
}
