// =============================================================================
//  Quien puede hacer que
// =============================================================================
//  Esta tabla es un ESPEJO de las reglas de SecurityConfig.java. Sirve para que
//  la interfaz esconda los botones que el usuario no podria usar, que es lo que
//  pide el enunciado: "buttons and menu options must be hidden or disabled when
//  the role lacks permission".
//
//  HAY QUE TENER CLARO QUE ESTO NO ES SEGURIDAD.
//  Todo este archivo lo descarga el navegador y cualquiera puede modificarlo con
//  las herramientas de desarrollo para hacer aparecer los botones. Eso no le
//  daria ningun permiso: el pedido igual sale hacia la API con su token, y la
//  API vuelve a decidir por su cuenta. Si las dos tablas se desincronizaran, gana
//  la de Java, y el sintoma es un 403 que la interfaz muestra como mensaje.
//
//  La utilidad de esta copia es de experiencia de uso, no de proteccion: es peor
//  mostrarle a un espectador un boton "Crear carrera" que lo lleva a un
//  formulario que va a terminar en error, que no mostrarselo.
// =============================================================================

/** Los tres roles del realm, tal cual vienen adentro del token. */
export const ROLES = {
    ADMIN: 'admin',
    ORGANIZADOR: 'organizer',
    ESPECTADOR: 'viewer'
};

/**
 * Cada accion de la interfaz y los roles que la habilitan.
 *
 * Los nombres son de ACCIONES y no de pantallas a proposito: una misma pantalla
 * puede mostrarse entera en modo lectura y tener adentro un boton reservado. El
 * detalle de una carrera lo ve cualquiera; cargar un resultado, no.
 */
export const PERMISOS = {
    // Competidores y equipos son el catalogo maestro: los administra el
    // administrador. Un organizador arma carreras con los competidores que ya
    // existen, pero no da de alta gente.
    gestionarCompetidores: [ROLES.ADMIN],
    gestionarEquipos: [ROLES.ADMIN],

    // La operacion de las carreras es del organizador, y el administrador puede
    // todo lo que puede el organizador.
    gestionarCarreras: [ROLES.ADMIN, ROLES.ORGANIZADOR],
    gestionarInscripciones: [ROLES.ADMIN, ROLES.ORGANIZADOR],
    cargarResultados: [ROLES.ADMIN, ROLES.ORGANIZADOR],

    // La bitacora es la unica seccion exclusiva del administrador: dice quien
    // hizo cada cosa, y eso es informacion de control, no de operacion.
    verAuditoria: [ROLES.ADMIN],

    // Leer lo puede hacer cualquiera que este autenticado, incluido el espectador.
    consultar: [ROLES.ADMIN, ROLES.ORGANIZADOR, ROLES.ESPECTADOR]
};

/** Nombre legible de cada rol, para mostrarlo en el perfil y en la barra. */
export const NOMBRE_DE_ROL = {
    admin: 'Administrador',
    organizer: 'Organizador de carreras',
    viewer: 'Espectador'
};
