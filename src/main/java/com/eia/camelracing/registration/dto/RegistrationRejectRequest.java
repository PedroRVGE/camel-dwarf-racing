package com.eia.camelracing.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El cuerpo de PATCH /api/registrations/{id}/reject.
 *
 * POR QUE RECHAZAR TIENE CUERPO Y APROBAR NO
 * El enunciado lo pide en una linea: "rejected registrations must include a clear
 * reason". Y tiene sentido practico: al participante hay que poder explicarle por
 * que quedo afuera, y sin obligar a escribirlo el campo termina vacio siempre.
 *
 * Aprobar, en cambio, no necesita justificacion: la aprobacion se explica sola.
 */
@Schema(description = "Motivo por el que se rechaza la inscripcion")
public record RegistrationRejectRequest(

        @Schema(description = "Motivo del rechazo, que se le muestra al participante",
                example = "El equipo no llego al minimo de integrantes activos")
        @NotBlank(message = "Hay que explicar por que se rechaza la inscripcion")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres")
        String reason
) {
}
