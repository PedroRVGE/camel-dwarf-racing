package com.eia.camelracing.race.dto;

import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.entity.RaceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * El detalle de una carrera.
 *
 * No trae la lista de inscripciones. Se piden aparte, con
 * GET /api/races/{raceId}/registrations, que ademas viene paginado. Anidarlas aca
 * significaria que consultar una carrera de cincuenta participantes devuelva
 * cincuenta objetos con su competidor y su equipo adentro, cuando la pantalla de
 * detalle solo necesita los datos de la carrera y un par de numeros.
 *
 * @param approvedCount inscripciones aprobadas: los participantes confirmados
 * @param pendingCount  inscripciones esperando decision del organizador
 */
@Schema(description = "Detalle de una carrera")
public record RaceResponse(

        UUID id,
        String name,
        String description,
        LocalDateTime scheduledAt,
        String startLocation,
        String finishLocation,
        int distanceMeters,
        int maxParticipants,
        RaceType type,
        RaceStatus status,

        @Schema(description = "Usuario que organiza la carrera", example = "organizer")
        String organizer,

        LocalDateTime registrationDeadline,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        @Schema(description = "Participantes confirmados", example = "6")
        long approvedCount,

        @Schema(description = "Inscripciones esperando aprobacion", example = "2")
        long pendingCount
) {
}
