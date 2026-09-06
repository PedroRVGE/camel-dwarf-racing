package com.eia.camelracing.race.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Que clase de participantes admite la carrera.
 *
 * De esto depende una de las reglas del enunciado: "el tipo de inscripcion tiene
 * que coincidir con el tipo de carrera". No es un dato decorativo, es lo que
 * decide si una inscripcion se acepta o se rechaza.
 */
@Schema(description = "Tipo de carrera, que determina quien puede inscribirse")
public enum RaceType {

    /** Solo competidores individuales. Un equipo no se puede inscribir. */
    INDIVIDUAL,

    /** Solo equipos. Un competidor suelto no se puede inscribir. */
    TEAM,

    /**
     * Los dos. Es la carrera clasica de esta liga: un camello contra un equipo de
     * cinco enanos.
     */
    MIXED;

    /** Si admite la inscripcion de un competidor individual. */
    public boolean admiteIndividuales() {
        return this == INDIVIDUAL || this == MIXED;
    }

    /** Si admite la inscripcion de un equipo. */
    public boolean admiteEquipos() {
        return this == TEAM || this == MIXED;
    }
}
