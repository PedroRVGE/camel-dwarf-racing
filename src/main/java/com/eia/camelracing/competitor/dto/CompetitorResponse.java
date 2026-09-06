package com.eia.camelracing.competitor.dto;

import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lo que la API devuelve de un competidor.
 *
 * El equipo se APLANA en dos campos sueltos, teamId y teamName, en vez de anidar
 * un TeamResponse completo. Si se anidara, para armar la respuesta habria que
 * cargar el equipo con todos sus integrantes, y cada integrante volveria a traer
 * a su equipo: el JSON se vuelve recursivo y las consultas se multiplican. Con el
 * id y el nombre alcanza para mostrar la ficha y para que el frontend arme el
 * link al equipo.
 *
 * @param age fecha de nacimiento ya convertida a anios. El enunciado pide "fecha
 *            de nacimiento o edad aproximada"; se guarda la fecha, que no
 *            envejece, y se calcula la edad al momento de responder, que es el
 *            dato que la interfaz quiere mostrar.
 */
@Schema(description = "Datos de un competidor")
public record CompetitorResponse(

        UUID id,
        String name,
        String nickname,
        CompetitorType type,
        LocalDate dateOfBirth,

        @Schema(description = "Edad en anios, calculada a partir de la fecha de nacimiento",
                example = "7")
        int age,

        BigDecimal weightKg,
        BigDecimal heightCm,
        String countryOfOrigin,
        CompetitorStatus status,
        LocalDateTime registeredAt,

        @Schema(description = "Id del equipo, o null si compite solo")
        UUID teamId,

        @Schema(description = "Nombre del equipo, o null si compite solo")
        String teamName,

        int victories,
        int defeats,
        int completedRaces
) {
}
