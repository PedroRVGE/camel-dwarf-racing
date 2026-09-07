package com.eia.camelracing.result.service;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.result.dto.ResultRequest;
import com.eia.camelracing.result.dto.ResultResponse;
import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.result.mapper.ResultMapper;
import com.eia.camelracing.result.repository.IRaceResultRepository;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.repository.ITeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Los resultados oficiales: cargarlos, corregirlos y mantener las estadisticas al
 * dia.
 *
 * ES EL SERVICIO CON LAS REGLAS MAS DELICADAS DEL SISTEMA
 * No porque sean complicadas de programar, sino por lo que pasa si fallan. Un
 * error en una inscripcion se arregla dando de baja y anotando de nuevo. Un error
 * en un resultado corrompe la clasificacion de la carrera y, a traves de las
 * estadisticas, la tabla de posiciones de todo el campeonato.
 *
 * El enunciado lo resume en una frase que vale por toda la especificacion: "the
 * system must prevent two winners, three second places and a camel finishing
 * before the race began". Las tres cosas estan cubiertas, y cada una en el lugar
 * donde se puede garantizar de verdad:
 *
 *   dos ganadores          constraint unica (race_id, final_position) + chequeo
 *                          previo con mensaje claro
 *   tres segundos puestos  la misma constraint
 *   llegar antes de largar imposible de escribir: no se guarda una hora de
 *                          llegada sino una duracion, que ademas tiene que ser
 *                          positiva (ver el comentario en RaceResult)
 *
 * Y se agrega una cuarta, que el enunciado no pide pero que es de la misma
 * familia: que los puestos y los tiempos no se contradigan entre si.
 */
@Service
@RequiredArgsConstructor
public class ResultService {

    private final IRaceResultRepository resultRepository;
    private final IRaceRepository raceRepository;
    private final IRaceRegistrationRepository registrationRepository;
    private final ICompetitorRepository competitorRepository;
    private final ITeamRepository teamRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * La clasificacion de una carrera.
     * GET /api/races/{raceId}/results
     *
     * Se verifica que la carrera exista antes de listar. Sin eso, pedir los
     * resultados de una carrera inventada devolveria una pagina vacia con 200, que
     * es una respuesta que miente: no es que la carrera no tenga resultados, es que
     * la carrera no existe.
     */
    @Transactional(readOnly = true)
    public PageResponse<ResultResponse> getByRace(UUID raceId, Pageable pageable) {
        findRaceOrThrow(raceId);
        return PageResponse.of(resultRepository.findByRaceId(raceId, pageable),
                ResultMapper::toResponse);
    }

    /** Un resultado puntual. GET /api/results/{id} */
    @Transactional(readOnly = true)
    public ResultResponse getById(UUID id) {
        return ResultMapper.toResponse(findResultOrThrow(id));
    }

    // ------------------------------------------------------------------
    // CARGAR
    // ------------------------------------------------------------------

    /**
     * Carga el resultado de un participante.
     * POST /api/races/{raceId}/results
     *
     * El orden de las validaciones es el de siempre: primero lo que descalifica la
     * operacion entera (la carrera no esta corriendose), despues lo del participante
     * puntual. Asi el primer error que ve el usuario es el que explica el problema
     * de fondo.
     */
    @Transactional
    public ResultResponse record(UUID raceId, ResultRequest request) {
        Race race = findRaceOrThrow(raceId);

        validarQueLaCarreraAdmiteResultados(race);

        RaceRegistration registration = findRegistrationOrThrow(request.registrationId());
        validarQueLaInscripcionEsDeLaCarrera(registration, raceId);
        validarQueElParticipanteEstaAprobado(registration);
        validarQueNoTieneResultado(registration);

        validarPosicionLibre(raceId, request.finalPosition(), null);
        validarCoherenciaDeTiempos(raceId, null, request);

        RaceResult result = ResultMapper.toEntity(request, registration, currentUser.username());
        result = resultRepository.save(result);

        recalcularEstadisticas(registration);

        auditService.registrar(AuditAction.RESULT_RECORDED, "RaceResult", result.getId(),
                "Se cargo el resultado de '" + nombreDelParticipante(registration)
                        + "' en la carrera '" + race.getName() + "'",
                null, resumen(result));

        return ResultMapper.toResponse(result);
    }

    /**
     * Corrige un resultado ya cargado.
     * PUT /api/results/{id}
     *
     * SE PUEDE CORREGIR INCLUSO CON LA CARRERA YA TERMINADA, Y ES A PROPOSITO
     * Podria parecer contradictorio con la regla "a completed race cannot be
     * edited", pero esa regla es sobre los DATOS DE LA CARRERA: la fecha, la
     * distancia, el recorrido. Cambiar eso despues de corrida falsea las
     * condiciones en las que se compitio.
     *
     * Un resultado es otra cosa. Las correcciones despues de la llegada son parte
     * normal de cualquier competencia: un jurado revisa una maniobra y descalifica
     * al que habia ganado, o aparece un error de cronometraje. Prohibirlas
     * significaria que un error se vuelve permanente.
     *
     * Lo que hace que esto no sea una puerta trasera es que cada correccion queda en
     * la bitacora con el antes, el despues y quien la hizo. Y ademas se recalculan
     * las estadisticas, que es lo que el enunciado pide con "updating a result must
     * update statistics consistently".
     */
    @Transactional
    public ResultResponse update(UUID id, ResultRequest request) {
        RaceResult result = findResultOrThrow(id);
        Race race = result.getRace();
        RaceRegistration registration = result.getRegistration();

        validarQueLaCarreraAdmiteCorrecciones(race);
        validarQueNoCambiaElParticipante(result, request);

        validarPosicionLibre(race.getId(), request.finalPosition(), id);
        validarCoherenciaDeTiempos(race.getId(), id, request);

        String antes = resumen(result);

        ResultMapper.updateEntity(result, request);
        result = resultRepository.save(result);

        recalcularEstadisticas(registration);

        auditService.registrar(AuditAction.RESULT_UPDATED, "RaceResult", result.getId(),
                "Se corrigio el resultado de '" + nombreDelParticipante(registration)
                        + "' en la carrera '" + race.getName() + "'",
                antes, resumen(result));

        return ResultMapper.toResponse(result);
    }

    // ==================================================================
    // Las reglas, una por metodo
    // ==================================================================

    /**
     * "Results may only be entered for an IN_PROGRESS race."
     *
     * Antes de que largue no hay nada que medir. Despues de terminada, agregar un
     * participante nuevo a la clasificacion cambiaria un resultado que ya es
     * oficial: para eso esta la correccion, que trabaja sobre un resultado que ya
     * existia.
     */
    private void validarQueLaCarreraAdmiteResultados(Race race) {
        if (race.getStatus() != RaceStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus()
                            + ": los resultados solo se cargan mientras esta IN_PROGRESS");
        }
    }

    /** Corregir se puede con la carrera en curso o ya terminada, no antes ni si se cancelo. */
    private void validarQueLaCarreraAdmiteCorrecciones(Race race) {
        if (race.getStatus() != RaceStatus.IN_PROGRESS && race.getStatus() != RaceStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus()
                            + ": no se pueden corregir resultados de una carrera en ese estado");
        }
    }

    /**
     * La inscripcion tiene que ser de ESTA carrera.
     *
     * Es la clase de error que solo aparece cuando alguien copia un id de otra
     * pantalla. Sin este control, el resultado se guardaria con la carrera de la
     * inscripcion y no con la de la URL, y aparecerian resultados en carreras que
     * nadie cargo.
     */
    private void validarQueLaInscripcionEsDeLaCarrera(RaceRegistration registration, UUID raceId) {
        if (!registration.getRace().getId().equals(raceId)) {
            throw new BusinessRuleException(
                    "La inscripcion " + registration.getId() + " no pertenece a esta carrera");
        }
    }

    /**
     * "Only approved participants may receive results."
     *
     * Un pendiente nunca fue confirmado, un rechazado fue expresamente excluido y
     * un dado de baja se borro de la grilla. Ninguno de los tres corrio, asi que
     * ninguno puede tener tiempo.
     */
    private void validarQueElParticipanteEstaAprobado(RaceRegistration registration) {
        if (registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BusinessRuleException(
                    "La inscripcion esta " + registration.getStatus()
                            + ": solo los participantes APPROVED pueden tener resultado");
        }
    }

    /** Un participante, un resultado. Cargarlo dos veces lo contaria dos veces en las estadisticas. */
    private void validarQueNoTieneResultado(RaceRegistration registration) {
        if (resultRepository.existsByRegistrationId(registration.getId())) {
            throw new BusinessRuleException(
                    "El participante '" + nombreDelParticipante(registration)
                            + "' ya tiene resultado cargado en esta carrera. Para cambiarlo, corregilo");
        }
    }

    /**
     * El participante de un resultado no se cambia.
     *
     * El PUT manda el recurso completo, asi que registrationId viaja en el cuerpo,
     * pero eso no lo vuelve editable: mover un resultado de un participante a otro
     * no es corregirlo, es borrar uno e inventar otro, y ademas dejaria las
     * estadisticas de los dos mal.
     */
    private void validarQueNoCambiaElParticipante(RaceResult result, ResultRequest request) {
        if (!result.getRegistration().getId().equals(request.registrationId())) {
            throw new BusinessRuleException(
                    "Un resultado no puede cambiar de participante. Si el resultado es de otro, "
                            + "corregi el que corresponde");
        }
    }

    /**
     * "Final positions cannot be duplicated" y "only one official winner is
     * allowed".
     *
     * La garantia real la da la constraint unica (race_id, final_position) de la
     * base: entre que este metodo consulta y el INSERT se ejecuta puede colarse otro
     * request, y en ese caso la constraint rechaza el segundo y
     * GlobalExceptionHandler lo traduce a 409.
     *
     * Este chequeo existe igual porque el mensaje que da la base es
     * "duplicate key value violates unique constraint uk_results_race_position", que
     * no le dice nada a nadie. Aca se puede decir cual es el puesto repetido.
     *
     * Los que no terminaron no entran: tienen la posicion en null y no compiten por
     * ningun puesto.
     */
    private void validarPosicionLibre(UUID raceId, Integer posicion, UUID excluirId) {
        if (posicion == null) return;

        if (resultRepository.posicionOcupada(raceId, posicion, excluirId)) {
            throw new BusinessRuleException(
                    "El puesto " + posicion + " ya esta asignado en esta carrera"
                            + (posicion == 1 ? ": no puede haber dos ganadores" : ""));
        }
    }

    /**
     * Los puestos y los tiempos tienen que contar la misma historia.
     *
     * ESTA REGLA NO ESTA EN EL ENUNCIADO, PERO ES DE LA MISMA FAMILIA QUE LAS QUE SI
     * ESTAN. El enunciado prohibe dos ganadores y camellos que llegan antes de
     * largar, o sea resultados internamente contradictorios. Este es el mismo
     * problema con otra cara: cargar al segundo puesto con un tiempo mejor que el
     * del primero. Nada de lo anterior lo detecta —el puesto 2 esta libre, el tiempo
     * es positivo— y sin embargo la clasificacion queda diciendo un absurdo.
     *
     * Se compara el tiempo TOTAL (carrera mas penalizacion), que es el que define el
     * orden de llegada: justamente lo que permite explicar por que alguien que cruzo
     * primero terminó segundo.
     *
     * Los empates se rechazan. En una carrera con puestos numerados, dos tiempos
     * identicos no dicen quien fue primero; si de verdad hubo un empate, la
     * herramienta para desempatar es la penalizacion o una nota del juez.
     *
     * Solo se controlan los que terminaron: comparar el tiempo de un abandono no
     * tiene sentido, no tiene tiempo.
     */
    private void validarCoherenciaDeTiempos(UUID raceId, UUID excluirId, ResultRequest request) {
        if (request.status() != ResultStatus.FINISHED) return;

        int total = request.completionTimeSeconds()
                + (request.penaltyTimeSeconds() == null ? 0 : request.penaltyTimeSeconds());
        int posicion = request.finalPosition();

        List<RaceResult> llegados = resultRepository.findByRaceIdAndStatus(raceId, ResultStatus.FINISHED);

        for (RaceResult otro : llegados) {
            if (excluirId != null && otro.getId().equals(excluirId)) {
                continue; // Es el que se esta corrigiendo: no se compara consigo mismo.
            }
            if (otro.getFinalPosition() == null || otro.tiempoTotalSegundos() == null) {
                continue;
            }

            boolean vaAdelante = posicion < otro.getFinalPosition();
            int otroTotal = otro.tiempoTotalSegundos();

            if (vaAdelante && total >= otroTotal) {
                throw new BusinessRuleException(
                        "El puesto " + posicion + " tiene un tiempo total de " + total
                                + "s, que no es mejor que el del puesto " + otro.getFinalPosition()
                                + " (" + otroTotal + "s)");
            }
            if (!vaAdelante && total <= otroTotal) {
                throw new BusinessRuleException(
                        "El puesto " + posicion + " tiene un tiempo total de " + total
                                + "s, que no es peor que el del puesto " + otro.getFinalPosition()
                                + " (" + otroTotal + "s)");
            }
        }
    }

    // ==================================================================
    // ESTADISTICAS
    // ==================================================================

    /**
     * Rehace las estadisticas del participante despues de tocar un resultado.
     *
     * SE RECALCULA DESDE CERO, NO SE SUMA UNO
     * Lo tentador es hacer competitor.setVictories(victories + 1) al cargar una
     * victoria. Es un error clasico, y el enunciado lo anticipa cuando pide que
     * "updating a result must update statistics consistently": si despues se corrige
     * ese resultado y el ganador pasa a descalificado, hay que acordarse de restar.
     * Con dos o tres correcciones encadenadas, los contadores empiezan a mentir y no
     * hay forma de saber cuando se rompieron.
     *
     * Recalcular es contar de nuevo todos los resultados del participante. Es un
     * poco mas caro y es imposible que quede inconsistente: el numero SIEMPRE
     * refleja lo que dice la tabla de resultados. Y es barato en la practica, porque
     * un competidor tiene decenas de carreras, no millones.
     *
     * A QUIEN SE LE ATRIBUYE UN RESULTADO
     * Al que figura en la inscripcion. Si corrio un competidor solo, es suyo; si
     * corrio un equipo, es del equipo.
     *
     * La alternativa seria repartir el resultado del equipo entre sus cinco
     * integrantes. Se descarto porque contaria la misma carrera seis veces (una por
     * enano y una por el equipo) y volveria incomparable la tabla de competidores:
     * quien corre en un buen equipo acumularia puntos sin haber ganado nada por su
     * cuenta. El precio de esta decision, y hay que decirlo, es que en su ficha
     * individual un enano que solo corre en equipo figura con cero carreras; lo que
     * hizo con el equipo se ve en la ficha del equipo.
     */
    private void recalcularEstadisticas(RaceRegistration registration) {
        if (registration.getCompetitor() != null) {
            Competitor competitor = registration.getCompetitor();
            List<RaceResult> resultados = resultRepository.resultadosDeCompetidor(competitor.getId());

            competitor.setVictories(contarVictorias(resultados));
            competitor.setDefeats(contarDerrotas(resultados));
            competitor.setCompletedRaces(contarTerminadas(resultados));
            competitorRepository.save(competitor);

        } else if (registration.getTeam() != null) {
            Team team = registration.getTeam();
            List<RaceResult> resultados = resultRepository.resultadosDeEquipo(team.getId());

            team.setVictories(contarVictorias(resultados));
            team.setDefeats(contarDerrotas(resultados));
            teamRepository.save(team);
        }
    }

    /** Ganadas: termino y salio primero. */
    private int contarVictorias(List<RaceResult> resultados) {
        return (int) resultados.stream().filter(RaceResult::esVictoria).count();
    }

    /**
     * Perdidas: compitio y no gano.
     *
     * Entran los que terminaron sin ganar, los descalificados y los que abandonaron.
     * NO entra DID_NOT_START: no largo, asi que no perdio nada. La explicacion de
     * ese criterio esta en ResultStatus.
     */
    private int contarDerrotas(List<RaceResult> resultados) {
        return (int) resultados.stream()
                .filter(r -> (r.getStatus() == ResultStatus.FINISHED && !r.esVictoria())
                        || r.getStatus().cuentaComoDerrota())
                .count();
    }

    /** Terminadas: las que efectivamente se corrieron de punta a punta. */
    private int contarTerminadas(List<RaceResult> resultados) {
        return (int) resultados.stream()
                .filter(r -> r.getStatus() == ResultStatus.FINISHED)
                .count();
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /** El texto que va a la bitacora como "antes" y como "despues". */
    private String resumen(RaceResult result) {
        return "status=" + result.getStatus()
                + ", posicion=" + result.getFinalPosition()
                + ", tiempo=" + result.getCompletionTimeSeconds()
                + ", penalizacion=" + result.getPenaltyTimeSeconds();
    }

    private String nombreDelParticipante(RaceRegistration registration) {
        if (registration.getCompetitor() != null) return registration.getCompetitor().getNickname();
        if (registration.getTeam() != null) return registration.getTeam().getName();
        return "desconocido";
    }

    private Race findRaceOrThrow(UUID id) {
        return raceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe una carrera con el id " + id));
    }

    private RaceRegistration findRegistrationOrThrow(UUID id) {
        return registrationRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe una inscripcion con el id " + id));
    }

    private RaceResult findResultOrThrow(UUID id) {
        return resultRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe un resultado con el id " + id));
    }
}
