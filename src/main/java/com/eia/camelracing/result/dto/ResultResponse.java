package com.eia.camelracing.result.dto;

import com.eia.camelracing.result.entity.ResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un resultado, como lo ve quien consulta la clasificacion.
 *
 * Trae el nombre del participante ademas de su id, igual que
 * RegistrationResponse, para que una tabla de resultados se pueda dibujar con una
 * sola llamada y no con una por fila.
 *
 * Hay dos campos que no existen en la entidad y se calculan al mapear:
 * totalTimeSeconds y points. Estan aca porque son lo que el usuario quiere leer, y
 * que se calculen en vez de guardarse evita el clasico dato derivado que queda
 * desincronizado.
 */
@Schema(description = "Resultado oficial de un participante en una carrera")
public record ResultResponse(

        UUID id,

        UUID raceId,
        String raceName,

        UUID registrationId,

        @Schema(description = "Id del competidor, o null si el resultado es de un equipo")
        UUID competitorId,
        String competitorNickname,

        @Schema(description = "Id del equipo, o null si el resultado es individual")
        UUID teamId,
        String teamName,

        @Schema(description = "Nombre a mostrar del participante", example = "El Camello Cuantico")
        String participantName,

        @Schema(description = "Carril desde el que largo", example = "3")
        Integer startingPosition,

        @Schema(description = "Puesto en el que termino, o null si no termino", example = "1")
        Integer finalPosition,

        @Schema(description = "Tiempo de carrera en segundos", example = "184")
        Integer completionTimeSeconds,

        @Schema(description = "Segundos de penalizacion", example = "10")
        int penaltyTimeSeconds,

        @Schema(description = "Tiempo de carrera mas penalizacion. Es el que define el orden de llegada",
                example = "194")
        Integer totalTimeSeconds,

        ResultStatus status,

        @Schema(description = "Puntos que suma este resultado para la tabla de posiciones",
                example = "10")
        int points,

        String notes,

        @Schema(description = "Usuario que cargo el resultado", example = "organizer")
        String recordedBy,

        LocalDateTime recordedAt,
        LocalDateTime updatedAt
) {
}
