package com.eia.camelracing.race.service;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.race.dto.RaceRequest;
import com.eia.camelracing.race.dto.RaceResponse;
import com.eia.camelracing.race.dto.RaceSummaryResponse;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.mapper.RaceMapper;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.result.repository.IRaceResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Carreras: alta, edicion y, sobre todo, el control de su ciclo de vida.
 *
 * LA PARTE IMPORTANTE DE ESTE SERVICIO ES changeStatus()
 * El resto es un CRUD comun. Lo que hace que una carrera se comporte bien es que
 * no pueda saltar de cualquier estado a cualquier otro: que no se largue sin
 * participantes, que una carrera terminada no vuelva a borrador, que no se
 * reabran inscripciones de algo que ya se corrio.
 *
 * Necesita el repositorio de inscripciones ademas del suyo porque dos de esas
 * reglas dependen de cuanta gente hay anotada, y ese dato no vive en Race.
 */
@Service
@RequiredArgsConstructor
public class RaceService {

    private final IRaceRepository raceRepository;
    private final IRaceRegistrationRepository registrationRepository;
    private final IRaceResultRepository resultRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /**
     * El minimo de participantes para que una carrera pueda largar.
     *
     * El enunciado lo fija: "at least two valid participants are required to
     * start". Una carrera de uno no es una carrera, es alguien trotando.
     */
    private static final int MINIMO_PARA_LARGAR = 2;

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * El listado, con filtros, paginacion y ordenamiento.
     *
     * Sin @Transactional: la consulta devuelve records ya armados desde la base,
     * no entidades con relaciones LAZY.
     */
    public PageResponse<RaceSummaryResponse> getRaces(RaceStatus status, com.eia.camelracing.race.entity.RaceType type,
                                                      String texto, Pageable pageable) {
        return PageResponse.of(raceRepository.buscar(status, type, patronDeBusqueda(texto), pageable));
    }

    /**
     * El detalle, con los dos conteos de inscripciones.
     *
     * Son dos consultas COUNT extra, y son baratas: devuelven un numero. La
     * alternativa —mapear la coleccion de inscripciones en Race— traeria la lista
     * entera para contarla, y ademas rompe la paginacion del listado (ver el
     * comentario largo en la entidad Race).
     */
    @Transactional(readOnly = true)
    public RaceResponse getRaceById(UUID id) {
        Race race = findRaceOrThrow(id);
        return RaceMapper.toResponse(race, aprobadas(id), pendientes(id));
    }

    // ------------------------------------------------------------------
    // CREAR Y EDITAR
    // ------------------------------------------------------------------

    /**
     * Crea una carrera en estado DRAFT.
     *
     * El organizador sale del token: es quien esta creando la carrera. Que no
     * venga en el cuerpo evita que alguien le atribuya la organizacion a otro.
     */
    @Transactional
    public RaceResponse createRace(RaceRequest request) {
        Race race = RaceMapper.toEntity(request, currentUser.username());
        race = raceRepository.save(race);

        auditService.registrar(AuditAction.RACE_CREATED, "Race", race.getId(),
                "Se creo la carrera '" + race.getName() + "'");

        // Recien creada no tiene inscripciones, asi que los conteos son cero y no
        // hace falta ir a preguntarlos a la base.
        return RaceMapper.toResponse(race, 0, 0);
    }

    /**
     * Edita los datos de una carrera.
     *
     * QUE SE PUEDE EDITAR Y CUANDO
     * El enunciado dice "a completed race cannot be edited". Aca se bloquea
     * tambien la carrera CANCELLED y la que ya esta IN_PROGRESS, y las tres por el
     * mismo motivo: son estados en los que cambiar las condiciones falsea la
     * historia. Editarle la distancia a una carrera en curso significaria que los
     * tiempos que se estan midiendo no corresponden al recorrido que figura; en una
     * terminada o cancelada, directamente se reescribe algo que ya paso.
     *
     * Mientras la carrera este en DRAFT, con inscripciones abiertas o cerradas, se
     * puede corregir todo, incluida la fecha.
     */
    @Transactional
    public RaceResponse updateRace(UUID id, RaceRequest request) {
        Race race = findRaceOrThrow(id);

        if (race.getStatus().esFinal()) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus() + " y ya no se puede editar");
        }
        if (race.getStatus() == RaceStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "La carrera ya largo: no se pueden cambiar sus condiciones");
        }

        String antes = "nombre=" + race.getName() + ", fecha=" + race.getScheduledAt()
                + ", distancia=" + race.getDistanceMeters() + ", cupo=" + race.getMaxParticipants();

        RaceMapper.updateEntity(race, request);
        race = raceRepository.save(race);

        auditService.registrar(AuditAction.RACE_UPDATED, "Race", race.getId(),
                "Se editaron los datos de la carrera '" + race.getName() + "'",
                antes,
                "nombre=" + race.getName() + ", fecha=" + race.getScheduledAt()
                        + ", distancia=" + race.getDistanceMeters() + ", cupo=" + race.getMaxParticipants());

        return RaceMapper.toResponse(race, aprobadas(id), pendientes(id));
    }

    // ------------------------------------------------------------------
    // EL CICLO DE VIDA
    // ------------------------------------------------------------------

    /**
     * Mueve la carrera de un estado al siguiente.
     * PATCH /api/races/{id}/status
     *
     * Se controlan dos cosas distintas, y hacen falta las dos:
     *
     *   1. Que la TRANSICION exista. Las combinaciones validas estan declaradas en
     *      RaceStatus, no acá: asi, agregar un estado nuevo obliga a definir sus
     *      transiciones en el mismo lugar donde se lo declara.
     *
     *   2. Que se cumplan las condiciones para ese destino puntual. Que la
     *      transicion DRAFT -> OPEN sea legal no significa que cualquier carrera
     *      pueda largar: para pasar a IN_PROGRESS hacen falta dos participantes
     *      aprobados.
     */
    @Transactional
    public RaceResponse changeStatus(UUID id, RaceStatus nuevoEstado) {
        Race race = findRaceOrThrow(id);
        RaceStatus actual = race.getStatus();

        // Pedir el estado que ya tiene no es un error: es una operacion que no
        // cambia nada. Devolver 409 por esto solo complicaria a un frontend que
        // reintenta.
        if (actual == nuevoEstado) {
            return RaceMapper.toResponse(race, aprobadas(id), pendientes(id));
        }

        if (!actual.puedePasarA(nuevoEstado)) {
            // El mensaje enumera los destinos validos, en vez de decir solo "no se
            // puede". Es la diferencia entre un error que se entiende y uno que
            // obliga a ir a leer el codigo.
            String posibles = actual.transicionesPosibles().isEmpty()
                    ? "ninguno, es un estado final"
                    : actual.transicionesPosibles().toString();
            throw new BusinessRuleException(
                    "No se puede pasar de " + actual + " a " + nuevoEstado
                            + ". Destinos validos desde " + actual + ": " + posibles);
        }

        if (nuevoEstado == RaceStatus.IN_PROGRESS) {
            validarQuePuedeLargar(race);
        }
        if (nuevoEstado == RaceStatus.COMPLETED) {
            validarQuePuedeDarsePorTerminada(race);
        }

        race.setStatus(nuevoEstado);
        race = raceRepository.save(race);

        auditService.registrar(
                nuevoEstado == RaceStatus.CANCELLED ? AuditAction.RACE_CANCELLED : AuditAction.RACE_STATUS_CHANGED,
                "Race", race.getId(),
                "Se cambio el estado de la carrera '" + race.getName() + "'",
                "status=" + actual, "status=" + nuevoEstado);

        return RaceMapper.toResponse(race, aprobadas(id), pendientes(id));
    }

    /**
     * "Eliminar" una carrera es cancelarla.
     *
     * Mismo criterio que con competidores y equipos: nada se borra fisicamente.
     * Una carrera borrada dejaria inscripciones y, mas adelante, resultados
     * apuntando a algo que no existe, y ademas desapareceria del historial de todos
     * los que corrieron en ella.
     *
     * Cancelar respeta la maquina de estados, asi que intentar cancelar una carrera
     * ya terminada da 409 y no un borrado silencioso: una carrera que ya se corrio
     * no se puede "des-correr".
     */
    @Transactional
    public void deleteRace(UUID id) {
        Race race = findRaceOrThrow(id);

        if (race.getStatus() == RaceStatus.CANCELLED) {
            return; // Ya estaba cancelada: el DELETE es idempotente.
        }
        if (!race.getStatus().puedePasarA(RaceStatus.CANCELLED)) {
            throw new BusinessRuleException(
                    "La carrera esta " + race.getStatus() + " y ya no se puede cancelar");
        }

        RaceStatus anterior = race.getStatus();
        race.setStatus(RaceStatus.CANCELLED);
        raceRepository.save(race);

        auditService.registrar(AuditAction.RACE_CANCELLED, "Race", race.getId(),
                "Se cancelo la carrera '" + race.getName() + "'",
                "status=" + anterior, "status=" + RaceStatus.CANCELLED);
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /**
     * Una carrera solo puede largar con al menos dos participantes APROBADOS.
     *
     * Se cuentan las aprobadas y no las que ocupan lugar: una inscripcion pendiente
     * es alguien que pidio correr y que nadie confirmo todavia. Largar con dos
     * pendientes significaria que la carrera arranca sin que la organizacion haya
     * revisado a ninguno de los dos.
     */
    private void validarQuePuedeLargar(Race race) {
        long confirmados = aprobadas(race.getId());
        if (confirmados < MINIMO_PARA_LARGAR) {
            throw new BusinessRuleException(
                    "La carrera necesita al menos " + MINIMO_PARA_LARGAR
                            + " participantes aprobados para largar, y tiene " + confirmados);
        }
    }

    /**
     * "A race cannot be completed without official results."
     *
     * QUE SIGNIFICA "SIN RESULTADOS OFICIALES"
     * La lectura minima seria "que haya al menos un resultado cargado". Se
     * implementa la exigente: TODOS los participantes aprobados tienen que tener
     * resultado. Con la lectura minima, una carrera de ocho se podria dar por
     * terminada con un solo tiempo cargado, y los otros siete no figurarian en la
     * clasificacion ni siquiera como abandonos: quedarian como si nunca hubieran
     * corrido. Justamente el tipo de historia falsa que el resto del modulo se
     * ocupa de impedir.
     *
     * No hace falta pedir aparte que haya al menos un resultado: para llegar a
     * IN_PROGRESS ya hicieron falta dos participantes aprobados, asi que exigir que
     * ninguno quede sin resultado garantiza que haya por lo menos dos.
     *
     * Y si alguien efectivamente no corrio, para eso esta DID_NOT_START: dejarlo
     * asentado es un dato, no un tramite.
     */
    private void validarQuePuedeDarsePorTerminada(Race race) {
        long faltantes = resultRepository.aprobadasSinResultado(race.getId());
        if (faltantes > 0) {
            throw new BusinessRuleException(
                    "La carrera no se puede dar por terminada: faltan cargar " + faltantes
                            + " resultado(s). Los participantes que no corrieron van como DID_NOT_START");
        }
    }

    private long aprobadas(UUID raceId) {
        return registrationRepository.countByRaceIdAndStatusIn(
                raceId, List.of(RegistrationStatus.APPROVED));
    }

    private long pendientes(UUID raceId) {
        return registrationRepository.countByRaceIdAndStatusIn(
                raceId, List.of(RegistrationStatus.PENDING));
    }

    private Race findRaceOrThrow(UUID id) {
        return raceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe una carrera con el id " + id));
    }

    /** Ver la explicacion en CompetitorService: el patron se arma aca, no en el JPQL. */
    private String patronDeBusqueda(String texto) {
        return (texto == null || texto.isBlank())
                ? null
                : "%" + texto.trim().toLowerCase() + "%";
    }
}
