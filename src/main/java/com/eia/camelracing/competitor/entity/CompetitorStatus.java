package com.eia.camelracing.competitor.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * En que situacion esta el competidor.
 *
 * De esto depende la regla mas usada del sistema: "solo los competidores ACTIVE
 * pueden inscribirse en carreras nuevas". Los otros tres estados son las tres
 * formas distintas de no poder correr, y conviene que sean distintas porque no
 * significan lo mismo ni se resuelven igual.
 */
@Schema(description = "Situacion actual del competidor")
public enum CompetitorStatus {

    /** Puede inscribirse y competir. Es el unico estado que lo permite. */
    ACTIVE,

    /** Lesionado. No compite hasta recuperarse; se espera que vuelva a ACTIVE. */
    INJURED,

    /** Sancionado por la organizacion. Tampoco compite, pero por decision, no por salud. */
    SUSPENDED,

    /**
     * Retirado. Es el final del camino, y ademas cumple una segunda funcion: es el
     * estado al que van a parar los competidores que se "eliminan".
     *
     * El enunciado prohibe borrar fisicamente a un competidor que tenga resultados
     * oficiales, porque eso reescribiria carreras que ya pasaron: desapareceria el
     * ganador de una carrera terminada y las clasificaciones dejarian de cuadrar.
     * En vez de eso se lo retira, y sigue figurando en su historial.
     */
    RETIRED;

    /**
     * Si en este estado se puede entrar a una carrera.
     *
     * La regla vive en el enum y no repetida en cada servicio que la necesita. Si
     * manana se agrega un estado nuevo, hay un solo lugar donde decidir si compite
     * o no, y el compilador no deja olvidarse porque el valor nuevo se declara
     * aca mismo.
     */
    public boolean puedeCompetir() {
        return this == ACTIVE;
    }
}
