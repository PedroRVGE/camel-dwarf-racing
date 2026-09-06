package com.eia.camelracing.team.mapper;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.team.dto.TeamMemberResponse;
import com.eia.camelracing.team.dto.TeamRequest;
import com.eia.camelracing.team.dto.TeamResponse;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.entity.TeamStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Traduce entre la entidad Team y sus DTOs.
 */
public class TeamMapper {

    private TeamMapper() {}

    /**
     * Arma el equipo a partir del request.
     *
     * Nace ACTIVE y sin integrantes: se le suma gente despues, con
     * POST /api/teams/{teamId}/members/{competitorId}, que es donde viven las
     * reglas de cuanta gente entra y de que nadie este en dos equipos.
     */
    public static Team toEntity(TeamRequest request) {
        if (request == null) return null;
        return Team.builder()
                .name(request.name())
                .description(request.description())
                .coach(request.coach())
                .status(TeamStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Copia los campos editables sobre un equipo que ya existe.
     *
     * Ni el estado ni los integrantes se tocan: los dos tienen sus propios
     * endpoints. Eso es lo que hace que un PUT para corregir el nombre del
     * entrenador no pueda vaciar el equipo por accidente.
     */
    public static void updateEntity(Team team, TeamRequest request) {
        if (team == null || request == null) return;
        team.setName(request.name());
        team.setDescription(request.description());
        team.setCoach(request.coach());
    }

    /**
     * Arma la respuesta que ve el cliente de la API.
     *
     * Recorre team.getMembers(), que es LAZY, y de cada integrante lee campos
     * propios: necesita un @Transactional o el @EntityGraph del repositorio.
     *
     * El orden por apodo es para que la respuesta sea estable. Sin un ORDER BY
     * explicito, la base puede devolver las filas en cualquier orden y en la
     * practica lo cambia: dos llamadas seguidas devuelven la misma lista barajada
     * distinto, y en la pantalla los integrantes saltan de lugar al recargar.
     */
    public static TeamResponse toResponse(Team team) {
        if (team == null) return null;
        List<TeamMemberResponse> members = team.getMembers()
                .stream()
                .map(TeamMapper::toMemberResponse)
                .sorted(Comparator.comparing(TeamMemberResponse::nickname))
                .toList();
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getCoach(),
                team.getStatus(),
                team.getCreatedAt(),
                members.size(),
                members,
                team.getVictories(),
                team.getDefeats()
        );
    }

    /** La version reducida de un competidor que se muestra dentro del equipo. */
    public static TeamMemberResponse toMemberResponse(Competitor competitor) {
        if (competitor == null) return null;
        return new TeamMemberResponse(
                competitor.getId(),
                competitor.getName(),
                competitor.getNickname(),
                competitor.getType(),
                competitor.getStatus()
        );
    }
}
