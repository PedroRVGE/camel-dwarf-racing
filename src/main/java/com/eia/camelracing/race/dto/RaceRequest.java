package com.eia.camelracing.race.dto;

import com.eia.camelracing.race.entity.RaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * Lo que se manda para crear o editar una carrera.
 *
 * CUATRO CAMPOS DE LA ENTIDAD NO ESTAN ACA:
 *
 *   - status: se cambia por PATCH /api/races/{id}/status, porque no es un dato
 *     sino una transicion con reglas propias (ver RaceStatus).
 *   - organizer: sale del token de quien crea la carrera. Si viniera en el
 *     cuerpo, cualquiera podria atribuirle la organizacion a otra persona.
 *   - createdAt y updatedAt: los pone Hibernate solo, con @PrePersist y
 *     @PreUpdate.
 */
@Schema(description = "Datos para crear o editar una carrera")
public record RaceRequest(

        @Schema(description = "Nombre de la carrera", example = "Gran Premio Alto de Las Palmas")
        @NotBlank(message = "El nombre de la carrera es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        String name,

        @Schema(description = "De que se trata la carrera",
                example = "Un kilometro de camello contra equipo de enanos")
        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 500, message = "La descripcion no puede pasar de 500 caracteres")
        String description,

        // @Future cubre la regla del enunciado "a race cannot be created in the
        // past", y vale tambien al editar, que es lo correcto: no existe ningun
        // motivo legitimo para programar una carrera para ayer. Si una carrera
        // quedo vieja en borrador, corregirla es ponerle una fecha nueva.
        @Schema(description = "Cuando larga. Tiene que ser en el futuro",
                example = "2026-12-20T10:00:00")
        @NotNull(message = "La fecha de la carrera es obligatoria")
        @Future(message = "La carrera no puede programarse en el pasado")
        LocalDateTime scheduledAt,

        @Schema(description = "Donde larga", example = "Zuniga, Envigado")
        @NotBlank(message = "El lugar de largada es obligatorio")
        @Size(max = 150, message = "El lugar de largada no puede pasar de 150 caracteres")
        String startLocation,

        @Schema(description = "Donde termina", example = "Alto de Las Palmas")
        @NotBlank(message = "El lugar de llegada es obligatorio")
        @Size(max = 150, message = "El lugar de llegada no puede pasar de 150 caracteres")
        String finishLocation,

        @Schema(description = "Distancia en metros", example = "1000")
        @Positive(message = "La distancia tiene que ser mayor que cero")
        int distanceMeters,

        // Minimo 2, y no 1, porque el enunciado exige al menos dos participantes
        // para largar: un cupo maximo de 1 daria una carrera que nunca podria
        // empezar. El maximo de 50 evita que alguien escriba un cupo absurdo por
        // error de tipeo.
        @Schema(description = "Cupo maximo de participantes", example = "8")
        @Min(value = 2, message = "El cupo tiene que ser de al menos 2 participantes")
        @Max(value = 50, message = "El cupo no puede pasar de 50 participantes")
        int maxParticipants,

        @Schema(description = "Tipo de carrera: INDIVIDUAL, TEAM o MIXED", example = "MIXED")
        @NotNull(message = "El tipo de carrera es obligatorio")
        RaceType type,

        @Schema(description = "Hasta cuando se puede inscribir gente. Tiene que ser antes de la largada",
                example = "2026-12-18T23:59:00")
        @NotNull(message = "El cierre de inscripciones es obligatorio")
        @Future(message = "El cierre de inscripciones no puede estar en el pasado")
        LocalDateTime registrationDeadline
) {

    /**
     * El cierre de inscripciones tiene que ser ANTES de la largada.
     *
     * Es una regla del enunciado, y es de las que no se pueden expresar con una
     * anotacion sobre un campo suelto: depende de DOS campos a la vez. Para eso
     * existe @AssertTrue, que valida un metodo en vez de un atributo.
     *
     * El nombre del metodo importa: Jakarta Validation lo trata como la propiedad
     * "cierreAntesDeLaLargada", y ese es el nombre que aparece como clave en el
     * mapa validationErrors de la respuesta 400. Por eso se llama asi y no
     * "check1".
     *
     * Devuelve true cuando algun campo es null para no pisar el mensaje del
     * @NotNull correspondiente: si falta la fecha, el error util es "la fecha es
     * obligatoria", no "el cierre tiene que ser anterior a una fecha que no
     * mandaste".
     */
    @Schema(hidden = true)
    @AssertTrue(message = "El cierre de inscripciones tiene que ser anterior a la largada")
    public boolean isCierreAntesDeLaLargada() {
        if (scheduledAt == null || registrationDeadline == null) return true;
        return registrationDeadline.isBefore(scheduledAt);
    }
}
