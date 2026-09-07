package com.eia.camelracing.registration.dto;

import com.eia.camelracing.registration.entity.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lo que la API devuelve de una inscripcion.
 *
 * Tanto la carrera como el participante se APLANAN en id + nombre, en vez de
 * anidar los DTOs completos. Si se anidara el competidor, arrastraria su equipo;
 * si se anidara el equipo, arrastraria sus cinco integrantes; y si se anidara la
 * carrera, cada fila de la lista de inscripciones repetiria la carrera entera.
 *
 * Segun el tipo de inscripcion, uno de los dos pares (competitor* o team*) viene
 * en null. Cual de los dos es lo dice participantName, que trae directamente el
 * nombre a mostrar y le ahorra al frontend tener que averiguarlo.
 *
 * @param participantName apodo del competidor o nombre del equipo, segun el caso
 * @param validationNotes el motivo del rechazo, o las notas de validacion
 */
@Schema(description = "Una inscripcion en una carrera")
public record RegistrationResponse(

        UUID id,
        UUID raceId,
        String raceName,

        @Schema(description = "Id del competidor, o null si la inscripcion es de un equipo")
        UUID competitorId,
        String competitorNickname,

        @Schema(description = "Id del equipo, o null si la inscripcion es individual")
        UUID teamId,
        String teamName,

        @Schema(description = "Nombre a mostrar del participante, sea competidor o equipo",
                example = "The Five Exceptions")
        String participantName,

        RegistrationStatus status,

        @Schema(description = "Carril de largada, o null si todavia no se asigno", example = "3")
        Integer lane,

        LocalDateTime registeredAt,

        @Schema(description = "Usuario que hizo la inscripcion", example = "organizer")
        String registeredBy,

        String validationNotes
) {
}
