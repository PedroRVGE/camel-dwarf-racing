package com.eia.camelracing.registration.service;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.registration.dto.RegistrationRequest;
import com.eia.camelracing.registration.dto.RegistrationResponse;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.mapper.RegistrationMapper;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.repository.ITeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Inscripciones: quien puede anotarse, cuando, y como se aprueba o se rechaza.
 *
 * ES EL SERVICIO CON MAS REGLAS DEL PROYECTO, Y NO ES CASUALIDAD
 * La inscripcion es el punto donde se cruzan las tres entidades del sistema, y
 * por eso concentra las condiciones: la carrera tiene que estar abierta, el
 * competidor tiene que estar activo, el equipo tiene que tener gente, el tipo
 * tiene que coincidir, nadie puede anotarse dos veces ni correr contra si mismo.
 *
 * Todas esas verificaciones devuelven 409 y no 400, y la distincion importa: no
 * son datos mal formados, son pedidos correctos que chocan con el estado actual
 * del sistema. El mismo request que hoy falla porque las inscripciones estan
 * cerradas habria funcionado ayer.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final IRaceRegistrationRepository registrationRepository;
    private final IRaceRepository raceRepository;
    private final ICompetitorRepository competitorRepository;
    private final ITeamRepository teamRepository;
    private final CurrentUser currentUser;

    /**
     * Los estados que "cuentan": ocupan cupo y bloquean una segunda inscripcion.
     *
     * Las rechazadas y las canceladas quedan afuera a proposito. Un equipo al que
     * le rechazaron la inscripcion tiene que poder volver a anotarse una vez
     * corregido el problema; si el rechazo siguiera contando, el rechazo seria
     * definitivo y el motivo escrito no serviria de nada.
     */
    private static final List<RegistrationStatus> CUENTAN =
            List.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED);

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * Las inscripciones de una carrera.
     *
     * readOnly porque el mapper lee la carrera y el participante de cada
     * inscripcion, que son relaciones LAZY.
     */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> getByRace(UUID raceId, RegistrationStatus status,
                                                        Pageable pageable) {
        // Se verifica que la carrera exista para devolver 404 en vez de una lista
        // vacia. Una lista vacia haria parecer que la carrera existe y no tiene a
        // nadie anotado, cuando en realidad el id esta mal.
        if (!raceRepository.existsById(raceId)) {
            throw new NoSuchElementException("No existe una carrera con el id " + raceId);
        }
        return PageResponse.of(
                registrationRepository.buscarPorCarrera(raceId, status, pageable),
                RegistrationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrationResponse getById(UUID id) {
        return RegistrationMapper.toResponse(findRegistrationOrThrow(id));
    }

    // ------------------------------------------------------------------
    // INSCRIBIR
    // ------------------------------------------------------------------

    /**
     * Inscribe a un competidor o a un equipo en una carrera.
     *
     * El orden de las validaciones no es arbitrario: primero lo que depende solo de
     * la carrera (esta abierta, no vencio el plazo), despues lo del participante, y
     * al final el cupo. Asi el mensaje de error apunta a la causa mas general
     * primero: si las inscripciones estan cerradas, no tiene sentido informar
     * ademas que el equipo esta suspendido.
     *
     * Nace PENDING. Inscribirse es pedir un lugar, no obtenerlo.
     */
    @Transactional
    public RegistrationResponse register(UUID raceId, RegistrationRequest request) {
        Race race = findRaceOrThrow(raceId);

        validarQueLaCarreraAdmiteInscripciones(race);
        validarCupo(race);
        validarCarrilLibre(raceId, request.lane());

        Competitor competitor = null;
        Team team = null;

        if (request.competitorId() != null) {
            competitor = validarInscripcionIndividual(race, request.competitorId());
        } else {
            team = validarInscripcionDeEquipo(race, request.teamId());
        }

        RaceRegistration registration = RegistrationMapper.toEntity(
                request, race, competitor, team, currentUser.username());

        return RegistrationMapper.toResponse(registrationRepository.save(registration));
    }

    // ------------------------------------------------------------------
    // APROBAR Y RECHAZAR
    // ------------------------------------------------------------------

    /**
     * Aprueba una inscripcion: el participante queda confirmado.
     * PATCH /api/registrations/{id}/approve
     *
     * Si no traia carril, se le asigna el siguiente libre. Se hace al aprobar y no
     * al inscribirse porque hasta ese momento no se sabe si el participante va a
     * correr: reservarle un carril a alguien que despues se rechaza dejaria huecos
     * en la grilla de largada.
     */
    @Transactional
    public RegistrationResponse approve(UUID id) {
        RaceRegistration registration = findRegistrationOrThrow(id);

        validarQueEstaPendiente(registration, "aprobar");
        validarQueLaCarreraSigueDecidiendo(registration.getRace(), "aprobar");

        if (registration.getLane() == null) {
            registration.setLane(siguienteCarrilLibre(registration.getRace().getId()));
        }
        registration.setStatus(RegistrationStatus.APPROVED);

        return RegistrationMapper.toResponse(registrationRepository.save(registration));
    }

    /**
     * Rechaza una inscripcion, con motivo obligatorio.
     * PATCH /api/registrations/{id}/reject
     *
     * El motivo se guarda en validationNotes y se le devuelve al participante. El
     * enunciado lo exige ("rejected registrations must include a clear reason"), y
     * es lo que permite que un equipo sepa que corregir para volver a anotarse.
     *
     * Se libera el carril si lo tenia reservado: la inscripcion rechazada no va a
     * usarlo, y dejarlo ocupado bloquearia ese numero para el resto.
     */
    @Transactional
    public RegistrationResponse reject(UUID id, String reason) {
        RaceRegistration registration = findRegistrationOrThrow(id);

        validarQueEstaPendiente(registration, "rechazar");
        validarQueLaCarreraSigueDecidiendo(registration.getRace(), "rechazar");

        registration.setStatus(RegistrationStatus.REJECTED);
        registration.setValidationNotes(reason);
        registration.setLane(null);

        return RegistrationMapper.toResponse(registrationRepository.save(registration));
    }

    // ------------------------------------------------------------------
    // DAR DE BAJA
    // ------------------------------------------------------------------

    /**
     * Da de baja una inscripcion.
     * DELETE /api/registrations/{id}
     *
     * Como todo lo demas en este proyecto, no borra: pasa la inscripcion a
     * CANCELLED. Asi queda registro de que alguien se anoto y despues se bajo, que
     * es informacion util para la organizacion.
     *
     * Una vez que la carrera largo ya no se puede: la lista de participantes de una
     * carrera en curso o terminada es un hecho historico.
     */
    @Transactional
    public void cancel(UUID id) {
        RaceRegistration registration = findRegistrationOrThrow(id);

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            return; // Idempotente: dar de baja algo ya dado de baja no es un error.
        }

        Race race = registration.getRace();
        if (!race.getStatus().admiteInscripciones()
                && race.getStatus() != com.eia.camelracing.race.entity.RaceStatus.CLOSED_FOR_REGISTRATION
                && race.getStatus() != com.eia.camelracing.race.entity.RaceStatus.DRAFT) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus()
                            + ": la lista de participantes ya no se puede modificar");
        }

        registration.setStatus(RegistrationStatus.CANCELLED);
        // Se libera el carril para que lo pueda tomar otro participante.
        registration.setLane(null);
        registrationRepository.save(registration);
    }

    // ==================================================================
    // Las reglas, una por metodo
    // ==================================================================

    /**
     * La carrera tiene que estar abierta y el plazo no puede haber vencido.
     *
     * Son dos condiciones distintas y las dos hacen falta. El estado lo controla la
     * organizacion a mano; el plazo vence solo con el reloj. Una carrera puede
     * seguir en OPEN_FOR_REGISTRATION porque nadie la cerro todavia y estar igual
     * fuera de plazo.
     */
    private void validarQueLaCarreraAdmiteInscripciones(Race race) {
        if (!race.getStatus().admiteInscripciones()) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus()
                            + " y no admite inscripciones. Solo se puede inscribir cuando esta OPEN_FOR_REGISTRATION");
        }
        if (LocalDateTime.now().isAfter(race.getRegistrationDeadline())) {
            throw new BusinessRuleException(
                    "El plazo de inscripcion vencio el " + race.getRegistrationDeadline());
        }
    }

    /** El cupo maximo de la carrera no se puede superar. */
    private void validarCupo(Race race) {
        long ocupados = registrationRepository.countByRaceIdAndStatusIn(race.getId(), CUENTAN);
        if (ocupados >= race.getMaxParticipants()) {
            throw new BusinessRuleException(
                    "La carrera ya tiene el cupo completo (" + race.getMaxParticipants() + " participantes)");
        }
    }

    /** Dos participantes no pueden largar desde el mismo carril. */
    private void validarCarrilLibre(UUID raceId, Integer lane) {
        if (lane != null && registrationRepository.existsByRaceIdAndLane(raceId, lane)) {
            throw new BusinessRuleException(
                    "El carril " + lane + " ya esta ocupado en esta carrera");
        }
    }

    /**
     * Todo lo que hay que verificar para inscribir a un competidor por su cuenta.
     *
     * @return el competidor ya cargado, listo para asociar a la inscripcion
     */
    private Competitor validarInscripcionIndividual(Race race, UUID competitorId) {
        if (!race.getType().admiteIndividuales()) {
            throw new BusinessRuleException(
                    "Es una carrera de tipo " + race.getType()
                            + ": no admite competidores individuales, solo equipos");
        }

        Competitor competitor = competitorRepository.findById(competitorId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe un competidor con el id " + competitorId));

        // "Only ACTIVE competitors may be registered in new races". El mensaje dice
        // en que estado esta, porque no es lo mismo estar lesionado que suspendido.
        if (!competitor.getStatus().puedeCompetir()) {
            throw new BusinessRuleException(
                    "El competidor '" + competitor.getNickname() + "' esta "
                            + competitor.getStatus() + " y no puede inscribirse en carreras");
        }

        if (registrationRepository.existsByRaceIdAndCompetitorIdAndStatusIn(
                race.getId(), competitorId, CUENTAN)) {
            throw new BusinessRuleException(
                    "El competidor '" + competitor.getNickname() + "' ya esta inscripto en esta carrera");
        }

        // "A participant cannot compete simultaneously as an individual and as a
        // team member in the same race". Sin esto, un enano podria correr contra su
        // propio equipo.
        if (registrationRepository.competidorYaCorreEnUnEquipo(race.getId(), competitorId, CUENTAN)) {
            throw new BusinessRuleException(
                    "El competidor '" + competitor.getNickname()
                            + "' ya esta inscripto en esta carrera como integrante de un equipo");
        }

        return competitor;
    }

    /**
     * Todo lo que hay que verificar para inscribir a un equipo.
     *
     * @return el equipo ya cargado
     */
    private Team validarInscripcionDeEquipo(Race race, UUID teamId) {
        if (!race.getType().admiteEquipos()) {
            throw new BusinessRuleException(
                    "Es una carrera de tipo " + race.getType()
                            + ": no admite equipos, solo competidores individuales");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe un equipo con el id " + teamId));

        // "A suspended team cannot enter a race", y tampoco uno dado de baja.
        if (!team.getStatus().puedeCompetir()) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' esta " + team.getStatus()
                            + " y no puede inscribirse en carreras");
        }

        // "A team must contain at least one competitor before entering a race".
        List<Competitor> integrantes = competitorRepository.findByTeamId(teamId);
        if (integrantes.isEmpty()) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' no tiene integrantes y no puede inscribirse");
        }

        // "All individual competitors and team members must be eligible". Se listan
        // TODOS los que no pueden en vez de cortar en el primero: si el equipo tiene
        // tres lesionados, la organizacion quiere enterarse de los tres de una vez y
        // no descubrirlos de a uno a fuerza de reintentos.
        String noHabilitados = integrantes.stream()
                .filter(c -> !c.getStatus().puedeCompetir())
                .map(c -> c.getNickname() + " (" + c.getStatus() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        if (noHabilitados != null) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' tiene integrantes que no pueden competir: "
                            + noHabilitados);
        }

        if (registrationRepository.existsByRaceIdAndTeamIdAndStatusIn(race.getId(), teamId, CUENTAN)) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' ya esta inscripto en esta carrera");
        }

        // La otra mitad de la regla de la doble participacion, mirada desde el
        // equipo: ninguno de sus integrantes puede estar ya anotado por su cuenta.
        if (registrationRepository.algunIntegranteYaCorreSolo(race.getId(), teamId, CUENTAN)) {
            throw new BusinessRuleException(
                    "Algun integrante del equipo '" + team.getName()
                            + "' ya esta inscripto individualmente en esta carrera");
        }

        return team;
    }

    /** Solo se puede aprobar o rechazar lo que esta pendiente. */
    private void validarQueEstaPendiente(RaceRegistration registration, String accion) {
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException(
                    "La inscripcion esta " + registration.getStatus()
                            + " y solo se puede " + accion + " una inscripcion PENDING");
        }
    }

    /**
     * Aprobar y rechazar tienen sentido mientras la carrera todavia esta decidiendo
     * su plantel, o sea con inscripciones abiertas o cerradas. Una vez que largo, la
     * lista quedo firme.
     */
    private void validarQueLaCarreraSigueDecidiendo(Race race, String accion) {
        var estado = race.getStatus();
        boolean sigueDecidiendo =
                estado == com.eia.camelracing.race.entity.RaceStatus.OPEN_FOR_REGISTRATION
                        || estado == com.eia.camelracing.race.entity.RaceStatus.CLOSED_FOR_REGISTRATION;
        if (!sigueDecidiendo) {
            throw new BusinessRuleException(
                    "La carrera esta " + estado + ": ya no se pueden " + accion + " inscripciones");
        }
    }

    /**
     * El siguiente numero de carril libre.
     *
     * Toma el maximo usado y le suma uno, empezando por el 1. No reutiliza los
     * huecos que dejan las bajas: si largan por los carriles 1, 2 y 4 porque el 3 se
     * dio de baja, el siguiente es el 5 y no el 3. Reutilizar numeros haria que dos
     * participantes distintos de la misma carrera figuren con el mismo carril en
     * momentos distintos, y eso confunde cualquier planilla de largada.
     */
    private int siguienteCarrilLibre(UUID raceId) {
        Integer maximo = registrationRepository.maxLaneDeLaCarrera(raceId);
        return maximo == null ? 1 : maximo + 1;
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    private RaceRegistration findRegistrationOrThrow(UUID id) {
        return registrationRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe una inscripcion con el id " + id));
    }

    private Race findRaceOrThrow(UUID id) {
        return raceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe una carrera con el id " + id));
    }
}
