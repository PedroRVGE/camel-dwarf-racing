package com.eia.camelracing.team.dto;

import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Un integrante, visto desde la ficha del equipo.
 *
 * Es una version reducida de CompetitorResponse a proposito: cinco campos en vez
 * de dieciseis. Un equipo de cinco enanos con la ficha completa de cada uno
 * devolveria ochenta campos para dibujar una lista donde solo se ven el apodo y
 * el estado.
 *
 * Ademas evita la recursion: si aca fuera un CompetitorResponse, ese traeria su
 * teamId y teamName, o sea el equipo que ya se esta mirando.
 *
 * El estado va incluido porque es lo que decide si el equipo puede correr: un
 * equipo de cinco donde cuatro estan lesionados se ve distinto de uno de cinco
 * activos, y esa diferencia tiene que notarse en la pantalla.
 */
@Schema(description = "Un integrante del equipo, en version resumida")
public record TeamMemberResponse(
        UUID id,
        String name,
        String nickname,
        CompetitorType type,
        CompetitorStatus status
) {
}
