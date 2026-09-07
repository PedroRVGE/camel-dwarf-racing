package com.eia.camelracing.race.dto;

import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.entity.RaceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una carrera en el LISTADO, con lo justo para dibujar una fila.
 *
 * Se construye DIRECTAMENTE desde la consulta JPQL con un constructor expression
 * (SELECT new ...RaceSummaryResponse(...)), asi que el orden y el tipo de los
 * parametros tienen que coincidir exacto con el SELECT. Si se cambia uno hay que
 * cambiar el otro, y el error aparece al arrancar la aplicacion, no al compilar.
 *
 * approvedCount sale de una subconsulta con COUNT dentro del mismo SELECT. Es la
 * alternativa a mapear una coleccion de inscripciones en Race, que traeria todos
 * los problemas de paginacion sobre colecciones y ninguna ventaja: aca hace falta
 * un numero, no la lista.
 */
@Schema(description = "Una carrera en el listado")
public record RaceSummaryResponse(

        UUID id,
        String name,
        LocalDateTime scheduledAt,
        RaceType type,
        RaceStatus status,
        int distanceMeters,
        int maxParticipants,

        @Schema(description = "Participantes confirmados", example = "6")
        long approvedCount
) {
}
