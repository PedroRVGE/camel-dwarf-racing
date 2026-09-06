package com.eia.camelracing.competitor.dto;

import com.eia.camelracing.competitor.entity.CompetitorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que se manda para crear o editar un competidor.
 *
 * No se recibe la entidad Competitor directamente. Si se hiciera, quien llame a
 * la API podria mandar un id inventado, o pisar las victorias y las derrotas, que
 * son datos que solo el sistema tiene derecho a tocar. El DTO define exactamente
 * que campos se aceptan y el resto ni existe como posibilidad.
 *
 * TRES CAMPOS DE LA ENTIDAD NO ESTAN ACA, A PROPOSITO:
 *
 *   - status: se cambia por PATCH /api/competitors/{id}/status y no por PUT.
 *     Cambiar de estado no es "editar los datos", es una transicion con reglas
 *     propias: suspender a alguien o retirarlo son decisiones, y merecen quedar
 *     registradas como tales en la auditoria. Metido adentro del PUT, un cambio de
 *     estado se confundiria con haberle corregido el apellido.
 *   - registeredAt: lo pone el servidor. Si viniera del cliente, cualquiera podria
 *     antedatar un alta.
 *   - victories, defeats, completedRaces: los mantiene el modulo de resultados. Si
 *     se pudieran mandar, la clasificacion seria editable a mano.
 */
@Schema(description = "Datos para crear o editar un competidor")
public record CompetitorRequest(

        @Schema(description = "Nombre del competidor", example = "Byte")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
        String name,

        @Schema(description = "Apodo con el que se lo anuncia. No se puede repetir",
                example = "El Camello Cuantico")
        @NotBlank(message = "El apodo es obligatorio")
        @Size(min = 2, max = 100, message = "El apodo debe tener entre 2 y 100 caracteres")
        String nickname,

        @Schema(description = "Categoria: DWARF, CAMEL, MEDIUM u OTHER", example = "CAMEL")
        @NotNull(message = "La categoria es obligatoria")
        CompetitorType type,

        // @Past y no @PastOrPresent: alguien que nacio hoy no esta en condiciones
        // de correr un kilometro.
        @Schema(description = "Fecha de nacimiento. Tiene que ser anterior a hoy",
                example = "2019-04-12")
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento tiene que estar en el pasado")
        LocalDate dateOfBirth,

        // @Positive y no @Min(0): el enunciado dice "weight and height must be
        // positive", y un competidor de 0 kg no es un caso limite, es un dato mal
        // cargado. @Digits corta valores como 12.999, que la columna no puede
        // guardar con sus dos decimales.
        @Schema(description = "Peso en kilogramos", example = "612.50")
        @NotNull(message = "El peso es obligatorio")
        @Positive(message = "El peso tiene que ser mayor que cero")
        @Digits(integer = 3, fraction = 2, message = "El peso admite hasta 3 enteros y 2 decimales")
        BigDecimal weightKg,

        @Schema(description = "Altura en centimetros", example = "195.00")
        @NotNull(message = "La altura es obligatoria")
        @Positive(message = "La altura tiene que ser mayor que cero")
        @Digits(integer = 3, fraction = 2, message = "La altura admite hasta 3 enteros y 2 decimales")
        BigDecimal heightCm,

        @Schema(description = "Pais o lugar de origen", example = "Envigado")
        @NotBlank(message = "El origen es obligatorio")
        @Size(min = 2, max = 100, message = "El origen debe tener entre 2 y 100 caracteres")
        String countryOfOrigin,

        // Sin @NotNull: el equipo es opcional. El enunciado dice "optional team",
        // porque hay competidores individuales.
        @Schema(description = "Id del equipo al que se suma. Opcional: puede competir solo",
                example = "3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708")
        UUID teamId
) {
}
