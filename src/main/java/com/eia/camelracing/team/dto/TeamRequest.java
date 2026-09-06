package com.eia.camelracing.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se manda para crear o editar un equipo.
 *
 * Los integrantes NO estan aca, y es la decision de diseno de este record.
 *
 * Se podria recibir una lista de ids de competidores y sincronizar el equipo con
 * ella en cada PUT. El problema es que entonces un PUT que solo quiere corregir
 * el nombre del entrenador tiene que reenviar la lista completa de integrantes, y
 * el dia que alguien la manda vacia —o se olvida el campo— el equipo se queda sin
 * nadie sin que nadie haya pedido eso.
 *
 * Sumar y sacar gente son operaciones propias, con reglas propias (el maximo de
 * integrantes, que el competidor no este ya en otro equipo), y tienen sus propios
 * endpoints:
 *
 *   POST   /api/teams/{teamId}/members/{competitorId}
 *   DELETE /api/teams/{teamId}/members/{competitorId}
 */
@Schema(description = "Datos para crear o editar un equipo")
public record TeamRequest(

        @Schema(description = "Nombre del equipo. No se puede repetir",
                example = "The Five Exceptions")
        @NotBlank(message = "El nombre del equipo es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        String name,

        @Schema(description = "De que se trata el equipo",
                example = "Cinco enanos con una estrategia y varios discursos motivacionales")
        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 500, message = "La descripcion no puede pasar de 500 caracteres")
        String description,

        @Schema(description = "Entrenador o responsable del equipo", example = "Mr. Abandonado")
        @NotBlank(message = "El equipo tiene que tener un responsable")
        @Size(min = 2, max = 150, message = "El nombre del responsable debe tener entre 2 y 150 caracteres")
        String coach
) {
}
