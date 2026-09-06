package com.eia.camelracing.team.service;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.team.dto.TeamRequest;
import com.eia.camelracing.team.dto.TeamResponse;
import com.eia.camelracing.team.dto.TeamSummaryResponse;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.entity.TeamStatus;
import com.eia.camelracing.team.mapper.TeamMapper;
import com.eia.camelracing.team.repository.ITeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Equipos, y todo lo que tenga que ver con quien pertenece a que equipo.
 *
 * ESTE SERVICIO ES EL DUENO DE LAS REGLAS DE PERTENENCIA
 * Un competidor puede quedar asignado a un equipo por dos caminos distintos: por
 * el endpoint de integrantes (POST /api/teams/{id}/members/{id}) o mandando un
 * teamId al crear o editar un competidor. Las reglas —el maximo de integrantes,
 * que el equipo exista y admita gente— tienen que valer en los dos, o el segundo
 * camino es una puerta trasera para saltearse el primero.
 *
 * Por eso la validacion vive UNA sola vez, en prepararIngreso(), y
 * CompetitorService llama a este servicio en vez de resolver el equipo por su
 * cuenta. La dependencia va en un solo sentido (CompetitorService -> TeamService)
 * asi que no hay ciclo.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final ITeamRepository teamRepository;
    private final ICompetitorRepository competitorRepository;

    /**
     * El maximo de integrantes por equipo.
     *
     * El enunciado pide explicitamente que sea configurable, y por eso sale de
     * application.yml y no es un numero escrito en el codigo. La carrera original
     * era de cinco enanos contra un camello, pero nada garantiza que siga siendo
     * cinco: cambiarlo tiene que ser editar una linea de configuracion, no
     * recompilar.
     */
    @Value("${camelracing.teams.max-members}")
    private int maxMembers;

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * El listado, con filtros, paginacion y ordenamiento.
     *
     * No lleva @Transactional porque la consulta devuelve records ya armados desde
     * la base, no entidades con relaciones LAZY: no hay nada que se pueda quedar
     * sin cargar.
     */
    public PageResponse<TeamSummaryResponse> getTeams(TeamStatus status, String texto, Pageable pageable) {
        return PageResponse.of(teamRepository.buscar(status, patronDeBusqueda(texto), pageable));
    }

    /**
     * El detalle, con los integrantes.
     *
     * readOnly = true no es decorativo: TeamMapper recorre team.getMembers(), que
     * es LAZY, y application.yml apaga open-in-view, asi que la sesion de
     * Hibernate se cierra al terminar este metodo. Sin la transaccion abierta el
     * mapper revienta con LazyInitializationException. readOnly ademas le avisa a
     * Hibernate que no hace falta revisar cambios al cerrar (dirty checking), que
     * es trabajo de mas en una consulta.
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(UUID id) {
        return TeamMapper.toResponse(findTeamOrThrow(id));
    }

    // ------------------------------------------------------------------
    // CREAR Y EDITAR
    // ------------------------------------------------------------------

    @Transactional
    public TeamResponse createTeam(TeamRequest request) {
        if (teamRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleException(
                    "Ya existe un equipo llamado '" + request.name() + "'");
        }
        Team team = TeamMapper.toEntity(request);
        return TeamMapper.toResponse(teamRepository.save(team));
    }

    /**
     * Edita los datos del equipo: nombre, descripcion y responsable.
     *
     * Ni el estado ni los integrantes se tocan aca. Eso es lo que hace imposible
     * que un PUT para corregir el nombre del entrenador vacie el equipo por
     * accidente, que es el clasico efecto de recibir la lista de integrantes
     * adentro del mismo request.
     */
    @Transactional
    public TeamResponse updateTeam(UUID id, TeamRequest request) {
        Team team = findTeamOrThrow(id);
        // AndIdNot: sin eso, guardar el equipo sin cambiarle el nombre daria "ese
        // nombre ya existe", porque se encontraria a si mismo.
        if (teamRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessRuleException(
                    "Ya existe otro equipo llamado '" + request.name() + "'");
        }
        TeamMapper.updateEntity(team, request);
        return TeamMapper.toResponse(teamRepository.save(team));
    }

    /** Cambia el estado: suspender un equipo, reactivarlo, darlo de baja. */
    @Transactional
    public TeamResponse changeStatus(UUID id, TeamStatus nuevoEstado) {
        Team team = findTeamOrThrow(id);
        team.setStatus(nuevoEstado);
        // Dar de baja por PATCH tiene que liberar a los integrantes igual que
        // hacerlo por DELETE, o quedaria una forma de dejarlos atrapados.
        if (nuevoEstado == TeamStatus.INACTIVE) {
            liberarIntegrantes(team);
        }
        return TeamMapper.toResponse(teamRepository.save(team));
    }

    // ------------------------------------------------------------------
    // DAR DE BAJA
    // ------------------------------------------------------------------

    /**
     * "Eliminar" un equipo es darlo de baja, no borrarlo de la base.
     *
     * El enunciado lo pide asi: "un equipo con historial oficial de carreras no
     * puede eliminarse; debe desactivarse". Y va mas lejos de lo que pide, porque
     * aca NUNCA se borra fisicamente, tenga historial o no.
     *
     * El motivo es que la version condicional —borrar si no tiene historial,
     * desactivar si lo tiene— es una bomba de tiempo: el dia que alguien agregue
     * una tabla nueva que apunte a equipos y se olvide de sumarla al chequeo,
     * borrar un equipo va a dejar filas huerfanas. Con una sola regla, sin
     * excepciones, ese error no puede ocurrir.
     *
     * A los integrantes se los libera. Puede sorprender, pero es lo que mantiene
     * cierta la regla "un competidor no puede pertenecer a mas de un equipo
     * activo": si se quedaran adentro de un equipo dado de baja, no podrian sumarse
     * a ninguno nuevo y estarian atrapados en un equipo que ya no existe. Su
     * historial de carreras no se toca, que es donde vive el pasado del equipo.
     */
    @Transactional
    public void deleteTeam(UUID id) {
        Team team = findTeamOrThrow(id);
        team.setStatus(TeamStatus.INACTIVE);
        liberarIntegrantes(team);
        teamRepository.save(team);
    }

    // ------------------------------------------------------------------
    // INTEGRANTES
    // ------------------------------------------------------------------

    /**
     * Suma un competidor al equipo.
     * POST /api/teams/{teamId}/members/{competitorId}
     */
    @Transactional
    public TeamResponse addMember(UUID teamId, UUID competitorId) {
        Competitor competitor = findCompetitorOrThrow(competitorId);
        Team team = prepararIngreso(teamId, competitor);
        competitor.setTeam(team);
        competitorRepository.save(competitor);
        // Se relee el equipo para que la respuesta traiga la lista de integrantes
        // ya actualizada: el objeto que quedo en memoria todavia tiene la lista
        // como estaba antes, porque el dueno de la relacion es Competitor.
        return TeamMapper.toResponse(findTeamOrThrow(teamId));
    }

    /**
     * Saca un competidor del equipo. Queda como competidor individual, no se borra.
     * DELETE /api/teams/{teamId}/members/{competitorId}
     */
    @Transactional
    public TeamResponse removeMember(UUID teamId, UUID competitorId) {
        Team team = findTeamOrThrow(teamId);
        Competitor competitor = findCompetitorOrThrow(competitorId);

        // Se verifica que efectivamente este en ESTE equipo. Sin este chequeo,
        // pedir sacar a alguien que esta en otro equipo devolveria 204 como si
        // hubiera funcionado, y encima le sacaria el equipo que si tenia.
        if (competitor.getTeam() == null || !competitor.getTeam().getId().equals(team.getId())) {
            throw new BusinessRuleException(
                    "El competidor '" + competitor.getNickname() + "' no pertenece a este equipo");
        }

        competitor.setTeam(null);
        competitorRepository.save(competitor);
        return TeamMapper.toResponse(findTeamOrThrow(teamId));
    }

    /**
     * Valida que un competidor pueda entrar a un equipo y devuelve el equipo.
     *
     * Es el unico lugar donde viven las reglas de ingreso, y lo llaman tanto el
     * endpoint de integrantes como CompetitorService cuando alguien manda un
     * teamId al crear o editar un competidor.
     *
     * @param teamId el equipo al que entra, o null si va a competir solo
     * @return el equipo cargado, o null si teamId venia null
     */
    @Transactional(readOnly = true)
    public Team prepararIngreso(UUID teamId, Competitor competitor) {
        // Sin equipo: es un competidor individual, que el enunciado permite
        // explicitamente ("optional team"). No hay nada que validar.
        if (teamId == null) {
            return null;
        }

        Team team = findTeamOrThrow(teamId);

        // Ya esta en este equipo: no es un ingreso, es un guardado que no cambia
        // nada. Se devuelve el equipo sin contar cupo, porque contarlo aqui haria
        // que editarle el nombre a un competidor del equipo numero 10 fallara con
        // "el equipo esta completo".
        if (competitor.getTeam() != null && competitor.getTeam().getId().equals(teamId)) {
            return team;
        }

        // A un equipo dado de baja no se le suma gente: seria dejarla atrapada,
        // porque dar de baja el equipo es justamente lo que libera a sus
        // integrantes. Un equipo SUSPENDED si admite cambios de plantel: la
        // sancion es temporal y el enunciado solo dice que no puede correr.
        if (team.getStatus() == TeamStatus.INACTIVE) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' esta dado de baja y no admite integrantes");
        }

        // El maximo configurable. Se cuenta en la base con un COUNT en vez de
        // traer la lista y medirla: con el equipo completo, traer diez
        // competidores enteros para descartarlos es trabajo tirado.
        long actuales = competitorRepository.countByTeamId(teamId);
        if (actuales >= maxMembers) {
            throw new BusinessRuleException(
                    "El equipo '" + team.getName() + "' ya tiene el maximo de "
                            + maxMembers + " integrantes");
        }

        return team;
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /**
     * Deja sin equipo a todos los integrantes.
     *
     * Se recorre la lista y se guarda uno por uno en vez de hacer un UPDATE masivo
     * con @Modifying. Son a lo sumo diez filas, y asi Hibernate mantiene
     * coherente lo que tiene en memoria; un UPDATE masivo escribe directo en la
     * base y deja los objetos ya cargados diciendo que siguen en el equipo.
     */
    private void liberarIntegrantes(Team team) {
        List<Competitor> integrantes = competitorRepository.findByTeamId(team.getId());
        integrantes.forEach(c -> c.setTeam(null));
        competitorRepository.saveAll(integrantes);
        team.getMembers().clear();
    }

    /**
     * Busca el equipo o lanza NoSuchElementException, que GlobalExceptionHandler
     * traduce a un 404. Esta aparte para no repetir el mismo orElseThrow en cada
     * metodo, y para que el mensaje sea siempre el mismo.
     */
    private Team findTeamOrThrow(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe un equipo con el id " + id));
    }

    private Competitor findCompetitorOrThrow(UUID id) {
        return competitorRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("No existe un competidor con el id " + id));
    }

    /**
     * Arma el patron del LIKE, o null si no hay que buscar nada.
     *
     * Hace dos cosas, y las dos importan:
     *
     *   1. Convierte el texto vacio en null. El filtro de la consulta se desactiva
     *      cuando el parametro es null, no cuando es "". Sin esto, un ?texto= en
     *      blanco —que es lo que manda un formulario con el buscador vacio— se
     *      tomaria como una busqueda de la cadena vacia.
     *
     *   2. Le pone los porcentajes y lo pasa a minuscula ACA, en Java, en vez de
     *      hacerlo con CONCAT dentro de la consulta. Eso no es cosmetico: con la
     *      concatenacion en SQL y el parametro en null, Postgres resolvia el
     *      operador || como concatenacion de bytea y todo listado sin busqueda
     *      fallaba con "function lower(bytea) does not exist".
     */
    private String patronDeBusqueda(String texto) {
        return (texto == null || texto.isBlank())
                ? null
                : "%" + texto.trim().toLowerCase() + "%";
    }
}
