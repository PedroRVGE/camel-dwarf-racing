package com.eia.camelracing.race.repository;

import com.eia.camelracing.race.dto.RaceSummaryResponse;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.entity.RaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IRaceRepository extends JpaRepository<Race, UUID> {

    /**
     * El listado, con filtros, paginacion y ordenamiento.
     *
     * LA SUBCONSULTA DEL COUNT ES LO INTERESANTE DE ESTA CONSULTA
     * Cada fila del listado necesita saber cuantos participantes confirmados tiene
     * la carrera. Race no mapea una coleccion de inscripciones a proposito (ver el
     * comentario largo en esa entidad), asi que no se puede usar SIZE(). En su
     * lugar va una subconsulta correlacionada con COUNT dentro del propio SELECT.
     *
     * Lo que evita es un N+1 disfrazado: la alternativa seria traer las veinte
     * carreras y despues preguntar, una por una, cuantas inscripciones tiene cada
     * una. Veintiun viajes a la base para dibujar una tabla.
     *
     * El estado APROBADA va con el nombre completo del enum
     * (com.eia...RegistrationStatus.APPROVED). Es verboso, pero es la forma que
     * Hibernate resuelve sin ambiguedad; el nombre corto depende de que no haya
     * otro enum con un valor igual en el contexto.
     *
     * El patron del LIKE llega armado desde el servicio, con los porcentajes
     * puestos y en minuscula. La explicacion de por que no se usa CONCAT dentro de
     * la consulta esta en ICompetitorRepository.buscar(): con el parametro en null,
     * Postgres resolvia el || como concatenacion de bytea y fallaba.
     *
     * El countQuery va explicito porque Spring Data no sabe reescribir un SELECT
     * con constructor expression a un COUNT. Sin el, la aplicacion no arranca.
     */
    @Query(value = """
            SELECT new com.eia.camelracing.race.dto.RaceSummaryResponse(
                       r.id, r.name, r.scheduledAt, r.type, r.status,
                       r.distanceMeters, r.maxParticipants,
                       (SELECT COUNT(reg) FROM RaceRegistration reg
                         WHERE reg.race = r
                           AND reg.status = com.eia.camelracing.registration.entity.RegistrationStatus.APPROVED))
            FROM Race r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:type IS NULL OR r.type = :type)
              AND (:patron IS NULL
                   OR LOWER(r.name) LIKE :patron
                   OR LOWER(r.startLocation) LIKE :patron
                   OR LOWER(r.finishLocation) LIKE :patron)
            """,
            countQuery = """
            SELECT COUNT(r) FROM Race r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:type IS NULL OR r.type = :type)
              AND (:patron IS NULL
                   OR LOWER(r.name) LIKE :patron
                   OR LOWER(r.startLocation) LIKE :patron
                   OR LOWER(r.finishLocation) LIKE :patron)
            """)
    Page<RaceSummaryResponse> buscar(@Param("status") RaceStatus status,
                                     @Param("type") RaceType type,
                                     @Param("patron") String patron,
                                     Pageable pageable);
}
