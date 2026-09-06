package com.eia.camelracing.team.repository;

import com.eia.camelracing.team.dto.TeamSummaryResponse;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.entity.TeamStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ITeamRepository extends JpaRepository<Team, UUID> {

    /**
     * El DETALLE de un equipo, con sus integrantes en la misma consulta.
     *
     * Aca el @EntityGraph sobre la coleccion SI es correcto, porque no hay
     * paginacion: se trae un solo equipo y las filas que multiplica el JOIN son
     * sus propios integrantes. Sin esto, TeamMapper dispararia una consulta aparte
     * para leer la lista.
     */
    @Override
    @EntityGraph(attributePaths = "members")
    Optional<Team> findById(UUID id);

    /**
     * El LISTADO, como proyeccion liviana y sin integrantes.
     *
     * Devuelve TeamSummaryResponse y no Team a proposito. La explicacion larga
     * esta en ese record, pero en corto: traer los integrantes en una consulta
     * paginada rompe la paginacion, porque el JOIN contra la coleccion multiplica
     * las filas y el LIMIT deja de contar equipos.
     *
     * SIZE(t.members) lo traduce Hibernate a una subconsulta con COUNT, asi que se
     * obtiene la cantidad de integrantes sin traer ni una fila de competidores.
     *
     * EL countQuery VA EXPLICITO Y NO ES OPCIONAL
     * Para saber cuantas paginas hay, Spring Data necesita una segunda consulta
     * que cuente el total. Normalmente la deduce sola reescribiendo la de arriba,
     * pero no puede hacerlo cuando el SELECT es un constructor expression: no sabe
     * como convertir "new TeamSummaryResponse(...)" en un COUNT. Sin esta linea la
     * aplicacion falla al arrancar, al validar las consultas.
     *
     * El patron de busqueda llega armado desde el servicio, con los porcentajes
     * puestos y en minuscula. La explicacion de por que no se usa CONCAT dentro de
     * la consulta esta en ICompetitorRepository.buscar(): en resumen, con el
     * parametro en null Postgres resolvia el || como concatenacion de bytea y
     * fallaba con "function lower(bytea) does not exist".
     */
    @Query(value = """
            SELECT new com.eia.camelracing.team.dto.TeamSummaryResponse(
                       t.id, t.name, t.coach, t.status,
                       SIZE(t.members), t.victories, t.defeats)
            FROM Team t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:patron IS NULL
                   OR LOWER(t.name) LIKE :patron
                   OR LOWER(t.coach) LIKE :patron)
            """,
            countQuery = """
            SELECT COUNT(t) FROM Team t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:patron IS NULL
                   OR LOWER(t.name) LIKE :patron
                   OR LOWER(t.coach) LIKE :patron)
            """)
    Page<TeamSummaryResponse> buscar(@Param("status") TeamStatus status,
                                     @Param("patron") String patron,
                                     Pageable pageable);

    /** Si ya hay un equipo con ese nombre. Permite devolver un 409 explicativo. */
    boolean existsByNameIgnoreCase(String name);

    /** Lo mismo al editar, ignorando al propio equipo para que no se encuentre a si mismo. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
