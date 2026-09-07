package com.eia.camelracing.race.dto;

import com.eia.camelracing.race.entity.RaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * El cuerpo de PATCH /api/races/{id}/status.
 *
 * Este endpoint es el que ejecuta la maquina de estados de la carrera: abrir
 * inscripciones, cerrarlas, largar, terminar o cancelar. No cualquier destino es
 * valido desde cualquier origen; las transiciones permitidas estan declaradas en
 * RaceStatus.transicionesPosibles().
 */
@Schema(description = "Nuevo estado de la carrera")
public record RaceStatusRequest(

        @Schema(description = "Estado al que pasa la carrera", example = "OPEN_FOR_REGISTRATION")
        @NotNull(message = "El estado es obligatorio")
        RaceStatus status
) {
}
