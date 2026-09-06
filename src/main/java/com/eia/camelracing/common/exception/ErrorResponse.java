package com.eia.camelracing.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * La forma que tiene TODA respuesta de error de esta API.
 *
 * Que sea siempre la misma es lo que permite que el frontend tenga un solo lugar
 * donde traducir errores a mensajes para el usuario. Si cada endpoint devolviera
 * su propio formato, esa traduccion habria que escribirla una vez por endpoint y
 * se romperia sola cada vez que alguien agrega uno nuevo.
 *
 * LOS CINCO CAMPOS DEL ENUNCIADO
 * El enunciado muestra este JSON exacto y en este orden:
 *
 *   {
 *     "timestamp": "2026-08-15T14:30:00",
 *     "status": 404,
 *     "error": "Not Found",
 *     "message": "Competitor with ID 45 was not found",
 *     "path": "/api/competitors/45"
 *   }
 *
 * Los records de Java serializan sus campos en el orden en que estan declarados,
 * asi que el orden de abajo NO es decorativo: cambiarlo cambia el JSON.
 *
 * POR QUE HAY UN SEXTO CAMPO
 * validationErrors no esta en el enunciado y se agrega igual, porque el punto 7
 * pide "understandable field-level validation messages": mensajes por campo. Con
 * los cinco campos de arriba, un formulario con tres campos mal solo puede
 * recibir un texto suelto, y el frontend no tiene forma de saber al lado de que
 * input mostrar cada cosa.
 *
 * Va ultimo y con @JsonInclude(NON_NULL), asi que en los errores que no son de
 * validacion —un 404, un 403— directamente no aparece en la respuesta y el JSON
 * queda igual al del enunciado, letra por letra.
 *
 * @param timestamp        cuando ocurrio
 * @param status           codigo HTTP (400, 401, 403, 404, 409, 500...)
 * @param error            el nombre del codigo HTTP ("Not Found", "Conflict"...)
 * @param message          descripcion legible, pensada para mostrarle a una persona
 * @param path             la ruta que se pidio
 * @param validationErrors detalle campo -> mensaje, solo en errores de validacion
 */
@Schema(description = "Respuesta de error. Todos los errores de la API tienen esta forma")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Momento en que ocurrio el error", example = "2026-08-15T14:30:00")
        LocalDateTime timestamp,

        @Schema(description = "Codigo HTTP", example = "404")
        int status,

        @Schema(description = "Nombre del codigo HTTP", example = "Not Found")
        String error,

        @Schema(description = "Descripcion legible del error",
                example = "Competitor with ID 45 was not found")
        String message,

        @Schema(description = "Ruta que se estaba pidiendo", example = "/api/competitors/45")
        String path,

        @Schema(description = """
                Detalle por campo, solo en errores de validacion (400). En el resto de los
                errores este campo no aparece en la respuesta.""",
                example = "{\"weight\": \"El peso debe ser positivo\"}")
        Map<String, String> validationErrors
) {

    /**
     * Constructor corto para los errores que no son de validacion, que son la
     * mayoria. Evita tener que escribir un null suelto al final en cada handler,
     * que es de esas cosas que despues alguien copia y pega en el lugar
     * equivocado.
     */
    public ErrorResponse(LocalDateTime timestamp, int status, String error,
                         String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
