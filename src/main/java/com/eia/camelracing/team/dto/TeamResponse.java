package com.eia.camelracing.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la API devuelve de un equipo, con sus integrantes.
 *
 * @param memberCount cuantos integrantes tiene. Es redundante con members.size(),
 *                    y esta igual porque la pantalla de listado de equipos muestra
 *                    "5 integrantes" sin dibujar la lista: asi el frontend no
 *                    depende de que el arreglo venga completo para mostrar el
 *                    numero.
 */
@Schema(description = "Datos de un equipo con sus integrantes")
public record TeamResponse(

        UUID id,
        String name,
        String description,
        String coach,
        com.eia.camelracing.team.entity.TeamStatus status,
        LocalDateTime createdAt,

        @Schema(description = "Cantidad de integrantes", example = "5")
        int memberCount,

        @Schema(description = "Los integrantes, ordenados por apodo")
        List<TeamMemberResponse> members,

        int victories,
        int defeats
) {
}
