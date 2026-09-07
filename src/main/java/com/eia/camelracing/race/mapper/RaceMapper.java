package com.eia.camelracing.race.mapper;

import com.eia.camelracing.race.dto.RaceRequest;
import com.eia.camelracing.race.dto.RaceResponse;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;

/**
 * Traduce entre la entidad Race y sus DTOs.
 */
public class RaceMapper {

    private RaceMapper() {}

    /**
     * Arma la carrera a partir del request.
     *
     * Nace en DRAFT, siempre. Una carrera no se anuncia en el mismo acto en que se
     * crea: primero se arma, se revisa la fecha y el recorrido, y recien despues
     * alguien la abre a inscripciones con un PATCH. Si naciera abierta, cualquier
     * borrador a medio escribir seria visible y anotable.
     *
     * El organizador llega ya resuelto desde el token; el mapper no sabe nada de
     * seguridad.
     */
    public static Race toEntity(RaceRequest request, String organizer) {
        if (request == null) return null;
        return Race.builder()
                .name(request.name())
                .description(request.description())
                .scheduledAt(request.scheduledAt())
                .startLocation(request.startLocation())
                .finishLocation(request.finishLocation())
                .distanceMeters(request.distanceMeters())
                .maxParticipants(request.maxParticipants())
                .type(request.type())
                .status(RaceStatus.DRAFT)
                .organizer(organizer)
                .registrationDeadline(request.registrationDeadline())
                .build();
    }

    /**
     * Copia los campos editables sobre una carrera que ya existe.
     *
     * Ni el estado ni el organizador se tocan: el estado tiene su propio endpoint
     * con su maquina de transiciones, y el organizador es quien la creo, un dato
     * historico que no se reescribe. createdAt y updatedAt los maneja Hibernate.
     */
    public static void updateEntity(Race race, RaceRequest request) {
        if (race == null || request == null) return;
        race.setName(request.name());
        race.setDescription(request.description());
        race.setScheduledAt(request.scheduledAt());
        race.setStartLocation(request.startLocation());
        race.setFinishLocation(request.finishLocation());
        race.setDistanceMeters(request.distanceMeters());
        race.setMaxParticipants(request.maxParticipants());
        race.setType(request.type());
        race.setRegistrationDeadline(request.registrationDeadline());
    }

    /**
     * Arma la respuesta del detalle.
     *
     * Los dos conteos llegan como parametros y no se calculan aca adentro, por la
     * misma razon por la que el mapper no toca repositorios: se los pasa el
     * servicio, que es quien sabe consultar la base. Asi este metodo se puede
     * probar con un par de numeros inventados, sin levantar nada.
     */
    public static RaceResponse toResponse(Race race, long approvedCount, long pendingCount) {
        if (race == null) return null;
        return new RaceResponse(
                race.getId(),
                race.getName(),
                race.getDescription(),
                race.getScheduledAt(),
                race.getStartLocation(),
                race.getFinishLocation(),
                race.getDistanceMeters(),
                race.getMaxParticipants(),
                race.getType(),
                race.getStatus(),
                race.getOrganizer(),
                race.getRegistrationDeadline(),
                race.getCreatedAt(),
                race.getUpdatedAt(),
                approvedCount,
                pendingCount
        );
    }
}
