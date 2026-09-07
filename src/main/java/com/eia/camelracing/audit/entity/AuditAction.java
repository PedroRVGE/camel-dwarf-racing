package com.eia.camelracing.audit.entity;

/**
 * Las acciones que quedan registradas en la bitacora.
 *
 * POR QUE ES UN ENUM Y NO UN TEXTO LIBRE
 * La alternativa seria guardar un String cualquiera en cada llamada. Con texto
 * libre, tarde o temprano conviven "RACE_CANCELLED", "race_cancelled" y "Carrera
 * cancelada" para el mismo hecho, y filtrar la bitacora por accion se vuelve
 * adivinanza. Con un enum, el compilador es el que garantiza que la lista sea
 * cerrada, y el filtro del endpoint puede ofrecer los valores validos.
 *
 * El nombre de cada valor dice ENTIDAD_QUE_PASO, en pasado, porque la bitacora
 * registra hechos ya ocurridos: nunca se anota una intencion.
 *
 * El enunciado pide registrar "login, user creation, competitor changes, race
 * cancellation, registration decisions and result modifications". La creacion de
 * usuarios no aparece en esta lista por un motivo concreto: los usuarios no se
 * crean en esta aplicacion, se crean en Keycloak, que lleva su propia bitacora.
 */
public enum AuditAction {

    /**
     * El usuario empezo a usar la API.
     *
     * NO es el login: el login pasa en Keycloak, que es quien valida la
     * contrasena y emite el token, y esta aplicacion nunca lo ve. Lo que si ve es
     * la primera vez que ese token se usa contra la API, que es cuando la interfaz
     * grafica pide /api/auth/profile para saber quien entro. Eso es lo que se
     * registra, y por eso el nombre es honesto solo a medias: es el login visto
     * desde el lado del backend.
     */
    LOGIN,

    COMPETITOR_CREATED,
    COMPETITOR_UPDATED,
    COMPETITOR_STATUS_CHANGED,
    COMPETITOR_RETIRED,

    TEAM_CREATED,
    TEAM_UPDATED,
    TEAM_STATUS_CHANGED,
    TEAM_DEACTIVATED,
    TEAM_MEMBER_ADDED,
    TEAM_MEMBER_REMOVED,

    RACE_CREATED,
    RACE_UPDATED,
    RACE_STATUS_CHANGED,
    RACE_CANCELLED,

    REGISTRATION_CREATED,
    REGISTRATION_APPROVED,
    REGISTRATION_REJECTED,
    REGISTRATION_CANCELLED,

    RESULT_RECORDED,
    RESULT_UPDATED
}
