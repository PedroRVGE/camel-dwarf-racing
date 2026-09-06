package com.eia.camelracing.registration.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * En que situacion esta una inscripcion.
 *
 * Una inscripcion no es un si o un no: se pide, alguien la revisa y recien ahi
 * queda firme. Por eso hay cuatro estados y no un booleano.
 */
@Schema(description = "Situacion de la inscripcion")
public enum RegistrationStatus {

    /** Pedida, esperando que un organizador la apruebe o la rechace. */
    PENDING,

    /**
     * Aprobada. Es el unico estado que cuenta como participante de verdad: para
     * ocupar cupo, para el minimo de dos para largar y para poder recibir un
     * resultado.
     */
    APPROVED,

    /**
     * Rechazada por la organizacion. El enunciado exige que lleve un motivo
     * escrito, y por eso rechazar es un endpoint con cuerpo obligatorio y no un
     * simple cambio de estado.
     */
    REJECTED,

    /** Dada de baja, normalmente porque el propio participante se borro. */
    CANCELLED;

    /**
     * Si esta inscripcion ocupa un lugar en la carrera.
     *
     * Las pendientes cuentan a proposito. Si no contaran, una carrera de diez
     * lugares podria juntar cincuenta pendientes y recien al aprobarlas se
     * descubriria que cuarenta sobran. Reservar el lugar desde que se pide hace
     * que el cupo signifique algo mientras la inscripcion esta abierta.
     */
    public boolean ocupaLugar() {
        return this == PENDING || this == APPROVED;
    }
}
