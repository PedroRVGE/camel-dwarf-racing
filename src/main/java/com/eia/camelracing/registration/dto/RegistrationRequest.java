package com.eia.camelracing.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Lo que se manda para inscribir a alguien en una carrera.
 *
 * La carrera no va en el cuerpo: va en la URL
 * (POST /api/races/{raceId}/registrations). Es lo que corresponde, porque la
 * inscripcion es un sub-recurso de la carrera: no existe una inscripcion "suelta"
 * que despues se asigne a una carrera.
 *
 * Quien inscribe tampoco va en el cuerpo: sale del token.
 */
@Schema(description = "Datos para inscribir un participante en una carrera")
public record RegistrationRequest(

        @Schema(description = "Id del competidor, si es una inscripcion individual")
        UUID competitorId,

        @Schema(description = "Id del equipo, si es una inscripcion de equipo")
        UUID teamId,

        // Opcional. Si no viene, el carril se asigna solo al aprobar la
        // inscripcion, tomando el primero libre de esa carrera.
        @Schema(description = "Carril o posicion de largada. Opcional: si no viene, se asigna al aprobar",
                example = "3")
        @Positive(message = "El carril tiene que ser un numero positivo")
        Integer lane
) {

    /**
     * Tiene que venir UNO de los dos ids, y exactamente uno.
     *
     * Una inscripcion es de un competidor individual o de un equipo. Sin ninguno no
     * se sabe a quien se esta inscribiendo; con los dos, tampoco.
     *
     * Se valida aca y no en el servicio porque es una regla de FORMA del pedido, no
     * de estado del sistema: no depende de que exista el competidor, de si la
     * carrera esta abierta ni de nada que este en la base. Por eso corresponde un
     * 400 y no un 409, y por eso se resuelve con una anotacion de validacion.
     *
     * El nombre del metodo es el que aparece como clave en validationErrors, asi
     * que dice de que se trata.
     */
    @Schema(hidden = true)
    @AssertTrue(message = "Hay que mandar competitorId o teamId, y solo uno de los dos")
    public boolean isParticipanteUnico() {
        return (competitorId == null) != (teamId == null);
    }
}
