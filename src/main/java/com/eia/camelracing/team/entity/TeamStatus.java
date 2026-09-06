package com.eia.camelracing.team.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * En que situacion esta el equipo.
 *
 * Son tres y no dos por el mismo motivo que en los competidores: "no puede
 * correr" tiene causas distintas, y el enunciado las trata distinto. Un equipo
 * SUSPENDED esta sancionado y puede volver; uno INACTIVE se dio de baja.
 */
@Schema(description = "Situacion actual del equipo")
public enum TeamStatus {

    /** Puede inscribirse en carreras. Es el unico estado que lo permite. */
    ACTIVE,

    /**
     * Dado de baja. Es el estado al que van a parar los equipos que se "eliminan":
     * el enunciado prohibe borrar un equipo con historial de carreras, porque eso
     * dejaria resultados oficiales apuntando a un equipo que ya no existe.
     */
    INACTIVE,

    /** Sancionado por la organizacion. El enunciado es explicito: no puede entrar a una carrera. */
    SUSPENDED;

    /** Si en este estado el equipo puede entrar a una carrera. */
    public boolean puedeCompetir() {
        return this == ACTIVE;
    }
}
