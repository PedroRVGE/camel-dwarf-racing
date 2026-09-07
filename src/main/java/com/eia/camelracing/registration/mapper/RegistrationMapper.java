package com.eia.camelracing.registration.mapper;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.registration.dto.RegistrationRequest;
import com.eia.camelracing.registration.dto.RegistrationResponse;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.team.entity.Team;

import java.time.LocalDateTime;

/**
 * Traduce entre la entidad RaceRegistration y sus DTOs.
 */
public class RegistrationMapper {

    private RegistrationMapper() {}

    /**
     * Arma la inscripcion.
     *
     * La carrera, el competidor y el equipo llegan YA CARGADOS desde la base: el
     * mapper no busca nada, esa es tarea del servicio. Uno de los dos participantes
     * viene en null, segun si la inscripcion es individual o de equipo.
     *
     * Nace PENDING, nunca aprobada. Inscribirse es pedir un lugar, no obtenerlo: el
     * enunciado tiene todo un circuito de aprobacion y rechazo que quedaria sin
     * sentido si las inscripciones entraran ya aceptadas.
     */
    public static RaceRegistration toEntity(RegistrationRequest request, Race race,
                                            Competitor competitor, Team team,
                                            String registeredBy) {
        if (request == null) return null;
        return RaceRegistration.builder()
                .race(race)
                .competitor(competitor)
                .team(team)
                .status(RegistrationStatus.PENDING)
                .lane(request.lane())
                .registeredAt(LocalDateTime.now())
                .registeredBy(registeredBy)
                .build();
    }

    /**
     * Arma la respuesta que ve el cliente de la API.
     *
     * Lee la carrera y el participante, que son relaciones LAZY: tiene que
     * ejecutarse dentro de un @Transactional o con las relaciones ya traidas por
     * @EntityGraph. En este proyecto es seguro que falla si no, porque
     * application.yml apaga open-in-view.
     *
     * participantName resuelve aca cual de los dos participantes es el que hay que
     * mostrar. Podria dejarse esa decision al frontend, que tiene los dos campos y
     * uno en null, pero entonces cada pantalla que liste inscripciones repetiria el
     * mismo if. Resuelto una vez, del lado del servidor, todas muestran lo mismo.
     */
    public static RegistrationResponse toResponse(RaceRegistration registration) {
        if (registration == null) return null;

        Race race = registration.getRace();
        Competitor competitor = registration.getCompetitor();
        Team team = registration.getTeam();

        String participantName = competitor != null
                ? competitor.getNickname()
                : (team != null ? team.getName() : null);

        return new RegistrationResponse(
                registration.getId(),
                race != null ? race.getId() : null,
                race != null ? race.getName() : null,
                competitor != null ? competitor.getId() : null,
                competitor != null ? competitor.getNickname() : null,
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                participantName,
                registration.getStatus(),
                registration.getLane(),
                registration.getRegisteredAt(),
                registration.getRegisteredBy(),
                registration.getValidationNotes()
        );
    }
}
