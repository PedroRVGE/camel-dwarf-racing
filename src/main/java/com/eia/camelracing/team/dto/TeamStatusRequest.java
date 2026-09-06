package com.eia.camelracing.team.dto;

import com.eia.camelracing.team.entity.TeamStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * El cuerpo de PATCH /api/teams/{id}/status.
 *
 * Este endpoint no figura entre los que sugiere el enunciado para equipos, pero
 * hace falta: el enunciado SI dice que "un equipo suspendido no puede entrar a una
 * carrera", y sin una forma de suspenderlo esa regla no se puede ejercer nunca.
 *
 * Se resuelve extendiendo el patron que el propio enunciado usa en competidores y
 * carreras, un PATCH sobre /status, en vez de inventar una ruta distinta.
 */
@Schema(description = "Nuevo estado del equipo")
public record TeamStatusRequest(

        @Schema(description = "Estado al que pasa: ACTIVE, INACTIVE o SUSPENDED",
                example = "SUSPENDED")
        @NotNull(message = "El estado es obligatorio")
        TeamStatus status
) {
}
