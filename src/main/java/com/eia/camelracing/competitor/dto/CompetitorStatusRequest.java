package com.eia.camelracing.competitor.dto;

import com.eia.camelracing.competitor.entity.CompetitorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * El cuerpo de PATCH /api/competitors/{id}/status.
 *
 * Es un record de un solo campo, y podria evitarse mandando el estado como
 * parametro de la URL. Se hace asi igual por dos motivos:
 *
 *   - Un cambio de estado es una modificacion, y los datos de una modificacion
 *     van en el cuerpo. En la URL quedarian en los logs del servidor y en el
 *     historial del navegador.
 *   - Deja lugar para crecer. Si manana se quiere pedir el motivo de una
 *     suspension, se agrega un campo aca sin cambiar la ruta ni romper a quien ya
 *     la esta usando.
 */
@Schema(description = "Nuevo estado del competidor")
public record CompetitorStatusRequest(

        @Schema(description = "Estado al que pasa: ACTIVE, INJURED, SUSPENDED o RETIRED",
                example = "INJURED")
        @NotNull(message = "El estado es obligatorio")
        CompetitorStatus status
) {
}
