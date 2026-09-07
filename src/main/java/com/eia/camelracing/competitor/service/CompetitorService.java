package com.eia.camelracing.competitor.service;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.competitor.dto.CompetitorRequest;
import com.eia.camelracing.competitor.dto.CompetitorResponse;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import com.eia.camelracing.competitor.mapper.CompetitorMapper;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Competidores: enanos, camellos y todo lo que corra.
 *
 * Depende de TeamService y no del repositorio de equipos directamente. La razon
 * es que asignarle un equipo a un competidor tiene reglas —que el equipo exista,
 * que no este dado de baja, que no supere el maximo de integrantes— y esas reglas
 * ya viven ahi. Si este servicio resolviera el equipo por su cuenta, mandar un
 * teamId al crear un competidor seria una puerta trasera para meter un sexto
 * integrante en un equipo de cinco.
 */
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final ICompetitorRepository competitorRepository;
    private final TeamService teamService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * El listado, con los tres filtros, paginacion y ordenamiento.
     *
     * readOnly = true porque el mapper lee el equipo de cada competidor, que es
     * una relacion LAZY, y con open-in-view apagado la sesion se cierra al salir
     * de este metodo.
     *
     * @param type    filtra por categoria, o null para todas
     * @param status  filtra por estado, o null para todos
     * @param teamId  filtra por equipo, o null para todos
     * @param texto   busca en nombre y apodo, o null para no buscar
     */
    @Transactional(readOnly = true)
    public PageResponse<CompetitorResponse> getCompetitors(CompetitorType type,
                                                           CompetitorStatus status,
                                                           UUID teamId,
                                                           String texto,
                                                           Pageable pageable) {
        return PageResponse.of(
                competitorRepository.buscar(type, status, teamId, patronDeBusqueda(texto), pageable),
                CompetitorMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompetitorResponse getCompetitorById(UUID id) {
        return CompetitorMapper.toResponse(findCompetitorOrThrow(id));
    }

    // ------------------------------------------------------------------
    // CREAR
    // ------------------------------------------------------------------

    /**
     * Da de alta un competidor.
     *
     * El apodo se verifica antes de guardar para poder devolver un 409 con un
     * mensaje que diga cual es el apodo repetido. Sin este chequeo el alta
     * fallaria igual, porque la tabla tiene una unique constraint, pero el error
     * saldria de Postgres y habria que traducirlo desde un mensaje que trae el SQL
     * y el nombre de la constraint adentro.
     *
     * Los dos controles conviven a proposito: este da el buen mensaje, y la
     * constraint cubre el caso de dos requests simultaneos que consultan a la vez,
     * los dos ven el apodo libre y los dos insertan.
     */
    @Transactional
    public CompetitorResponse createCompetitor(CompetitorRequest request) {
        validarApodoLibre(request.nickname(), null);

        Competitor competitor = CompetitorMapper.toEntity(request, null);
        // El equipo se resuelve con las reglas de TeamService. Se hace despues de
        // construir la entidad porque prepararIngreso necesita saber en que equipo
        // esta hoy el competidor, y uno nuevo no esta en ninguno.
        Team team = teamService.prepararIngreso(request.teamId(), competitor);
        competitor.setTeam(team);

        competitor = competitorRepository.save(competitor);

        auditService.registrar(AuditAction.COMPETITOR_CREATED, "Competitor", competitor.getId(),
                "Se dio de alta al competidor '" + competitor.getNickname() + "'");

        return CompetitorMapper.toResponse(competitor);
    }

    // ------------------------------------------------------------------
    // EDITAR
    // ------------------------------------------------------------------

    /**
     * Edita los datos de un competidor.
     *
     * Mandar teamId = null lo saca del equipo y lo deja como individual, que es lo
     * que corresponde: en un PUT, un campo ausente significa "no tiene valor", no
     * "dejalo como estaba".
     *
     * El estado no se toca aca: para eso esta changeStatus.
     */
    @Transactional
    public CompetitorResponse updateCompetitor(UUID id, CompetitorRequest request) {
        Competitor competitor = findCompetitorOrThrow(id);
        validarApodoLibre(request.nickname(), id);

        String antes = "apodo=" + competitor.getNickname()
                + ", equipo=" + (competitor.getTeam() == null ? null : competitor.getTeam().getName());

        Team team = teamService.prepararIngreso(request.teamId(), competitor);
        CompetitorMapper.updateEntity(competitor, request, team);
        competitor = competitorRepository.save(competitor);

        auditService.registrar(AuditAction.COMPETITOR_UPDATED, "Competitor", competitor.getId(),
                "Se editaron los datos del competidor '" + competitor.getNickname() + "'",
                antes,
                "apodo=" + competitor.getNickname()
                        + ", equipo=" + (competitor.getTeam() == null ? null : competitor.getTeam().getName()));

        return CompetitorMapper.toResponse(competitor);
    }

    /**
     * Cambia el estado: lesionarlo, suspenderlo, reactivarlo o retirarlo.
     * PATCH /api/competitors/{id}/status
     *
     * Es un endpoint aparte del PUT porque cambiar de estado no es corregir un
     * dato: es una decision de la organizacion. Separarlo permite pedirle otro
     * permiso, dejarlo asentado en la auditoria como lo que es y, sobre todo, que
     * no se cuele sin querer adentro de una edicion de datos personales.
     *
     * No se restringen las transiciones. Un competidor RETIRED puede volver a
     * ACTIVE, y esta bien que se pueda: alguien retirado por error tiene que poder
     * volver, y en esta liga los regresos son parte del folclore. La unica
     * transicion que el enunciado prohibe explicitamente es la de las carreras
     * (una carrera terminada no vuelve a DRAFT), y esa si esta bloqueada en su
     * modulo.
     */
    @Transactional
    public CompetitorResponse changeStatus(UUID id, CompetitorStatus nuevoEstado) {
        Competitor competitor = findCompetitorOrThrow(id);
        CompetitorStatus anterior = competitor.getStatus();

        competitor.setStatus(nuevoEstado);
        competitor = competitorRepository.save(competitor);

        auditService.registrar(AuditAction.COMPETITOR_STATUS_CHANGED, "Competitor", competitor.getId(),
                "Se cambio el estado del competidor '" + competitor.getNickname() + "'",
                "status=" + anterior, "status=" + nuevoEstado);

        return CompetitorMapper.toResponse(competitor);
    }

    // ------------------------------------------------------------------
    // DAR DE BAJA
    // ------------------------------------------------------------------

    /**
     * "Eliminar" un competidor es retirarlo, no borrarlo de la base.
     *
     * El enunciado lo pide: "un competidor con resultados oficiales no puede
     * eliminarse fisicamente; debe retirarse o desactivarse". Aca se va mas lejos
     * y NUNCA se borra fisicamente, tenga resultados o no.
     *
     * La version condicional —borrar si no corrio nunca, retirar si corrio— parece
     * mas fina y es peligrosa: el dia que alguien agregue una tabla nueva que
     * apunte a competidores y se olvide de sumarla al chequeo, un borrado va a
     * dejar filas huerfanas o a reventar contra una foreign key. Con una sola
     * regla sin excepciones, ese error no puede ocurrir.
     *
     * Que quede como RETIRED tiene ademas el efecto correcto: sigue apareciendo en
     * los resultados de las carreras que ya corrio, pero CompetitorStatus.RETIRED
     * no puede competir, asi que no se lo puede inscribir en ninguna nueva.
     *
     * Es idempotente: retirar a alguien ya retirado no falla. Un DELETE repetido
     * no deberia romper, y el estado final es el mismo.
     */
    @Transactional
    public void deleteCompetitor(UUID id) {
        Competitor competitor = findCompetitorOrThrow(id);
        CompetitorStatus anterior = competitor.getStatus();

        competitor.setStatus(CompetitorStatus.RETIRED);
        competitorRepository.save(competitor);

        auditService.registrar(AuditAction.COMPETITOR_RETIRED, "Competitor", competitor.getId(),
                "Se retiro al competidor '" + competitor.getNickname() + "'",
                "status=" + anterior, "status=" + CompetitorStatus.RETIRED);
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /**
     * @param idPropio null al crear; al editar, el id del competidor que se esta
     *                 editando, para que no se detecte a si mismo como repetido.
     */
    private void validarApodoLibre(String nickname, UUID idPropio) {
        boolean repetido = (idPropio == null)
                ? competitorRepository.existsByNicknameIgnoreCase(nickname)
                : competitorRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, idPropio);

        if (repetido) {
            throw new BusinessRuleException(
                    "Ya hay un competidor con el apodo '" + nickname + "'");
        }
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
