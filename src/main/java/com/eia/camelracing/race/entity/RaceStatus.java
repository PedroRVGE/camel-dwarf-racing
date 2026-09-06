package com.eia.camelracing.race.entity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * En que punto de su vida esta una carrera.
 *
 * ESTO NO ES UNA LISTA DE ETIQUETAS, ES UNA MAQUINA DE ESTADOS
 * Los seis valores tienen un orden y no se puede saltar de cualquiera a
 * cualquiera. El enunciado lo pide explicitamente con dos reglas:
 *
 *   "A completed race cannot be edited"
 *   "A completed race cannot magically return to DRAFT, even if the camel
 *    requests a rematch"
 *
 * Las transiciones validas se declaran ACA ADENTRO, en el propio enum, y no
 * repartidas en ifs dentro del servicio. La diferencia es concreta: cuando la
 * regla vive en un if, agregar un estado nuevo compila perfecto y nadie se entera
 * de los cinco lugares que habia que tocar. Aca, el valor nuevo se declara al lado
 * de sus transiciones y es imposible olvidarse.
 */
@Schema(description = "Estado de la carrera")
public enum RaceStatus {

    /**
     * Borrador. La carrera se esta armando y todavia no se anuncio.
     * Es el estado en el que nace toda carrera.
     */
    DRAFT,

    /** Anunciada y con inscripciones abiertas. Es el UNICO estado que admite inscripciones. */
    OPEN_FOR_REGISTRATION,

    /**
     * Inscripciones cerradas. La lista de participantes ya no cambia, pero la
     * carrera todavia no largo: es el momento de aprobar o rechazar lo pendiente y
     * de asignar los carriles.
     */
    CLOSED_FOR_REGISTRATION,

    /** En curso. Es el unico estado en el que se pueden cargar resultados. */
    IN_PROGRESS,

    /**
     * Terminada, con resultados oficiales. Es un estado FINAL: desde aca no se
     * sale, y la carrera tampoco se puede editar.
     */
    COMPLETED,

    /** Cancelada. Tambien es final. No recibe inscripciones ni resultados. */
    CANCELLED;

    /**
     * A que estados se puede pasar desde este.
     *
     * Decisiones que vale la pena explicar:
     *
     * - Desde CLOSED_FOR_REGISTRATION se puede volver a OPEN_FOR_REGISTRATION.
     *   Cerrar las inscripciones antes de tiempo es un error humano comun y
     *   perfectamente reversible mientras la carrera no haya largado.
     *
     * - Desde IN_PROGRESS NO se puede volver atras. Una vez que largo, la unica
     *   salida es terminarla o cancelarla: retroceder implicaria que existieron
     *   tiempos de una carrera que "todavia no empezo".
     *
     * - COMPLETED y CANCELLED devuelven el conjunto vacio. Son finales, y ahi se
     *   cumple la regla del camello que pide revancha: no hay forma de volver a
     *   DRAFT.
     *
     * - Se puede cancelar desde cualquier estado no final. Una carrera se puede
     *   caer por lluvia, por falta de camellos o porque nadie encontro el
     *   kilometro.
     */
    public Set<RaceStatus> transicionesPosibles() {
        return switch (this) {
            case DRAFT -> Set.of(OPEN_FOR_REGISTRATION, CANCELLED);
            case OPEN_FOR_REGISTRATION -> Set.of(CLOSED_FOR_REGISTRATION, CANCELLED);
            case CLOSED_FOR_REGISTRATION -> Set.of(OPEN_FOR_REGISTRATION, IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    /** Si desde este estado se puede pasar al otro. */
    public boolean puedePasarA(RaceStatus destino) {
        return transicionesPosibles().contains(destino);
    }

    /** Si la carrera admite inscripciones nuevas. */
    public boolean admiteInscripciones() {
        return this == OPEN_FOR_REGISTRATION;
    }

    /**
     * Si la carrera ya no se puede modificar.
     *
     * Cubre la regla "a completed race cannot be edited", y suma CANCELLED por el
     * mismo razonamiento: editarle la distancia a una carrera que no se corrio ni
     * se va a correr solo sirve para ensuciar el historial.
     */
    public boolean esFinal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
