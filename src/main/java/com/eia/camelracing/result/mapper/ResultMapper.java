package com.eia.camelracing.result.mapper;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.result.dto.ResultRequest;
import com.eia.camelracing.result.dto.ResultResponse;
import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.team.entity.Team;

/**
 * Traduce entre la entidad RaceResult y sus DTOs.
 */
public class ResultMapper {

    private ResultMapper() {}

    /**
     * Arma el resultado a partir de la inscripcion ya validada.
     *
     * La carrera y la posicion de largada NO se leen del request: se copian de la
     * inscripcion. Son datos que ya existen y que el juez no elige al cargar la
     * llegada; pedirlos por el cuerpo abriria la puerta a que no coincidan.
     *
     * La penalizacion admite null en el request y entra como cero. Es lo razonable:
     * el caso normal es que no haya penalizacion, y obligar a mandar un 0 explicito
     * en cada carga seria ruido.
     */
    public static RaceResult toEntity(ResultRequest request, RaceRegistration registration,
                                      String recordedBy) {
        if (request == null) return null;

        return RaceResult.builder()
                .race(registration.getRace())
                .registration(registration)
                .startingPosition(registration.getLane())
                .finalPosition(request.finalPosition())
                .completionTimeSeconds(request.completionTimeSeconds())
                .penaltyTimeSeconds(request.penaltyTimeSeconds() == null ? 0 : request.penaltyTimeSeconds())
                .status(request.status())
                .notes(request.notes())
                .recordedBy(recordedBy)
                .build();
    }

    /**
     * Aplica una correccion sobre un resultado que ya existe.
     *
     * NO se tocan cuatro cosas, y cada una por su motivo:
     *   - race y registration: cambiarlas no es corregir este resultado, es
     *     escribir otro. El servicio ya rechaza el intento.
     *   - startingPosition: es la grilla de largada de una carrera ya corrida, un
     *     hecho historico.
     *   - recordedBy y recordedAt: dicen quien firmo el resultado original. Quien
     *     hizo la correccion queda en la bitacora, que es donde corresponde.
     */
    public static void updateEntity(RaceResult result, ResultRequest request) {
        if (result == null || request == null) return;

        result.setStatus(request.status());
        result.setFinalPosition(request.finalPosition());
        result.setCompletionTimeSeconds(request.completionTimeSeconds());
        result.setPenaltyTimeSeconds(request.penaltyTimeSeconds() == null ? 0 : request.penaltyTimeSeconds());
        result.setNotes(request.notes());
    }

    /**
     * Arma la respuesta.
     *
     * Igual que RegistrationMapper.toResponse, necesita la carrera y el
     * participante ya cargados: se llama dentro de una transaccion o con las
     * relaciones traidas por @EntityGraph, porque open-in-view esta apagado.
     *
     * totalTimeSeconds y points se calculan aca en vez de guardarse como columnas.
     * Son datos derivados, y un derivado guardado es un derivado que algun dia va a
     * contradecir a sus fuentes.
     */
    public static ResultResponse toResponse(RaceResult result) {
        if (result == null) return null;

        Race race = result.getRace();
        RaceRegistration registration = result.getRegistration();
        Competitor competitor = registration != null ? registration.getCompetitor() : null;
        Team team = registration != null ? registration.getTeam() : null;

        String participantName = competitor != null
                ? competitor.getNickname()
                : (team != null ? team.getName() : null);

        return new ResultResponse(
                result.getId(),
                race != null ? race.getId() : null,
                race != null ? race.getName() : null,
                registration != null ? registration.getId() : null,
                competitor != null ? competitor.getId() : null,
                competitor != null ? competitor.getNickname() : null,
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                participantName,
                result.getStartingPosition(),
                result.getFinalPosition(),
                result.getCompletionTimeSeconds(),
                result.getPenaltyTimeSeconds(),
                result.tiempoTotalSegundos(),
                result.getStatus(),
                result.puntos(),
                result.getNotes(),
                result.getRecordedBy(),
                result.getRecordedAt(),
                result.getUpdatedAt()
        );
    }
}
