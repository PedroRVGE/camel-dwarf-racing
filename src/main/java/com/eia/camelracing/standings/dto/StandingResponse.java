package com.eia.camelracing.standings.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Una linea de la tabla de posiciones.
 *
 * EL MISMO RECORD SIRVE PARA COMPETIDORES Y PARA EQUIPOS
 * Podrian ser dos, uno con el apodo y el tipo de competidor y otro con el
 * entrenador. Es uno solo porque una tabla de posiciones responde siempre la misma
 * pregunta —quien va ganando y con cuantos puntos— y esa pregunta no cambia segun
 * quien compita. Los datos propios de cada participante estan a un GET de
 * distancia por su id, y meterlos aca obligaria a mantener dos DTOs casi iguales.
 */
@Schema(description = "Una linea de la tabla de posiciones")
public record StandingResponse(

        @Schema(description = "Puesto en la tabla, contando desde 1 sobre el total y no sobre la pagina",
                example = "1")
        int position,

        @Schema(description = "Id del competidor o del equipo")
        UUID participantId,

        @Schema(description = "Apodo del competidor, o nombre del equipo",
                example = "El Camello Cuantico")
        String participantName,

        @Schema(description = "Puntos acumulados. La escala es 10-7-5-3-1 para los cinco primeros puestos",
                example = "27")
        long points,

        @Schema(description = "Carreras terminadas", example = "4")
        long racesFinished,

        @Schema(description = "Carreras ganadas", example = "2")
        long victories,

        @Schema(description = "Carreras corridas sin ganar, incluidos abandonos y descalificaciones",
                example = "2")
        long defeats
) {
}
