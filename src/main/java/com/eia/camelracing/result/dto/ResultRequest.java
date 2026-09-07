package com.eia.camelracing.result.dto;

import com.eia.camelracing.result.entity.ResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Lo que se manda para cargar o corregir el resultado de un participante.
 *
 * EL MISMO RECORD SIRVE PARA EL POST Y PARA EL PUT
 * Se podria tener un ResultUpdateRequest aparte, sin registrationId, ya que al
 * corregir un resultado la inscripcion no cambia. Esta hecho con un solo record
 * porque el PUT del enunciado es una actualizacion COMPLETA: se manda el recurso
 * entero, y de que participante es forma parte del recurso.
 *
 * Que el campo este tambien en el PUT no significa que se pueda cambiar. El
 * servicio verifica que coincida con el de la inscripcion original y devuelve 409
 * si no: cambiarle el participante a un resultado no es una correccion, es
 * inventar otro resultado.
 *
 * Lo que NO viaja en el cuerpo:
 *   - startingPosition: se copia del carril de la inscripcion. Es un dato de la
 *     grilla de largada, no algo que el juez decida al cargar la llegada.
 *   - recordedBy y recordedAt: salen del token y del reloj.
 */
@Schema(description = "Resultado de un participante en una carrera")
public record ResultRequest(

        @Schema(description = "Inscripcion a la que corresponde el resultado. Tiene que estar APPROVED")
        @NotNull(message = "Hay que decir de que inscripcion es el resultado")
        UUID registrationId,

        @Schema(description = "Como termino: FINISHED, DISQUALIFIED, DID_NOT_FINISH o DID_NOT_START",
                example = "FINISHED")
        @NotNull(message = "El estado del resultado es obligatorio")
        ResultStatus status,

        // "Completion time must be positive", regla del enunciado. @Positive sobre
        // un Integer solo se aplica cuando el valor viene: los que no terminaron lo
        // mandan en null y no disparan el error.
        @Schema(description = "Tiempo de carrera en segundos. Obligatorio si FINISHED, null en los demas casos",
                example = "184")
        @Positive(message = "El tiempo de carrera tiene que ser mayor que cero")
        Integer completionTimeSeconds,

        @Schema(description = "Segundos de penalizacion. Opcional, por defecto 0", example = "10")
        @PositiveOrZero(message = "La penalizacion no puede ser negativa")
        Integer penaltyTimeSeconds,

        @Schema(description = "Puesto en el que termino. Obligatorio si FINISHED, null en los demas casos",
                example = "1")
        @Positive(message = "La posicion final tiene que ser mayor que cero")
        Integer finalPosition,

        @Schema(description = "Observaciones del juez",
                example = "Se colgo del camello en el kilometro 800")
        @Size(max = 500, message = "Las notas no pueden pasar de 500 caracteres")
        String notes
) {

    /**
     * El estado y los datos de llegada tienen que decir lo mismo.
     *
     * ES LA REGLA QUE IMPIDE LOS RESULTADOS ABSURDOS
     * Sin ella se puede cargar un descalificado que salio primero, o un FINISHED
     * sin tiempo ni puesto. Las dos son contradicciones: si lo descalificaron no
     * tiene puesto, y si termino tiene que decir en que puesto y en cuanto tiempo.
     *
     * De aca sale, ademas, la regla del enunciado "a disqualified participant
     * cannot win": un DISQUALIFIED no puede traer posicion, asi que tampoco puede
     * traer la posicion 1.
     *
     * Va como @AssertTrue y no como anotacion de campo porque mira TRES campos a la
     * vez, y ninguna anotacion sobre un atributo suelto puede hacer eso. El nombre
     * del metodo es el que aparece como clave en validationErrors, por eso describe
     * la regla y no es "check2".
     */
    @Schema(hidden = true)
    @AssertTrue(message = "Un resultado FINISHED necesita tiempo y posicion final; "
            + "los demas estados no pueden tener ninguno de los dos")
    public boolean isCoherenteConElEstado() {
        if (status == null) return true; // Ya lo reporta @NotNull; no se pisa su mensaje.

        if (status.admitePosicionYTiempo()) {
            return completionTimeSeconds != null && finalPosition != null;
        }
        return completionTimeSeconds == null && finalPosition == null;
    }
}
