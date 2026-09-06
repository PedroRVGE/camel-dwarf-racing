package com.eia.camelracing.registration.repository;

import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface IRaceRegistrationRepository extends JpaRepository<RaceRegistration, UUID> {

    /**
     * Una inscripcion, con la carrera y el participante en la MISMA consulta.
     *
     * RegistrationMapper necesita los tres para armar la respuesta, y los tres son
     * LAZY. Sin este @EntityGraph, cada inscripcion que se mapea dispara hasta tres
     * consultas extra.
     */
    @Override
    @EntityGraph(attributePaths = {"race", "competitor", "team"})
    Optional<RaceRegistration> findById(UUID id);

    /**
     * Las inscripciones de una carrera, opcionalmente filtradas por estado.
     *
     * El @EntityGraph es seguro con paginacion porque las tres relaciones son
     * @ManyToOne: cada inscripcion tiene UNA carrera, UN competidor y UN equipo, asi
     * que el JOIN no multiplica filas y el LIMIT sigue contando inscripciones. Con
     * una coleccion habria que evitarlo.
     */
    @EntityGraph(attributePaths = {"race", "competitor", "team"})
    @Query("""
            SELECT r FROM RaceRegistration r
            WHERE r.race.id = :raceId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<RaceRegistration> buscarPorCarrera(@Param("raceId") UUID raceId,
                                            @Param("status") RegistrationStatus status,
                                            Pageable pageable);

    /**
     * Cuantas inscripciones de una carrera estan en alguno de estos estados.
     *
     * Se usa para dos cosas distintas: el cupo (cuantas ocupan lugar) y el minimo
     * de dos participantes para poder largar (cuantas estan aprobadas).
     */
    long countByRaceIdAndStatusIn(UUID raceId, Collection<RegistrationStatus> statuses);

    /** Si este competidor ya esta inscripto en esta carrera, en alguno de estos estados. */
    boolean existsByRaceIdAndCompetitorIdAndStatusIn(
            UUID raceId, UUID competitorId, Collection<RegistrationStatus> statuses);

    /** Si este equipo ya esta inscripto en esta carrera. */
    boolean existsByRaceIdAndTeamIdAndStatusIn(
            UUID raceId, UUID teamId, Collection<RegistrationStatus> statuses);

    /** Si el carril ya esta ocupado en esta carrera. Cubre "starting positions cannot be duplicated". */
    boolean existsByRaceIdAndLane(UUID raceId, Integer lane);

    /**
     * El carril mas alto usado en la carrera, o null si todavia no hay ninguno.
     *
     * Sirve para asignar el siguiente al aprobar una inscripcion que vino sin
     * carril. Devuelve Integer y no int justamente para poder distinguir "todavia
     * no hay carriles" de "el ultimo es el cero".
     */
    @Query("SELECT MAX(r.lane) FROM RaceRegistration r WHERE r.race.id = :raceId")
    Integer maxLaneDeLaCarrera(@Param("raceId") UUID raceId);

    /**
     * Si este competidor ya esta anotado en la carrera COMO PARTE DE UN EQUIPO.
     *
     * Cubre media de la regla del enunciado "a participant cannot compete
     * simultaneously as an individual and as a team member in the same race". Esta
     * mitad se consulta cuando alguien intenta inscribir al competidor por su
     * cuenta: hay que revisar si algun equipo ya inscripto lo tiene entre sus
     * integrantes.
     *
     * El doble JOIN es lo que hace el trabajo: de la inscripcion al equipo, y del
     * equipo a sus integrantes. Sin esto habria que traer todas las inscripciones
     * de equipo de la carrera y revisar sus planteles en Java.
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM RaceRegistration r
            JOIN r.team t
            JOIN t.members m
            WHERE r.race.id = :raceId
              AND m.id = :competitorId
              AND r.status IN :statuses
            """)
    boolean competidorYaCorreEnUnEquipo(@Param("raceId") UUID raceId,
                                        @Param("competitorId") UUID competitorId,
                                        @Param("statuses") Collection<RegistrationStatus> statuses);

    /**
     * Si algun integrante de este equipo ya esta anotado INDIVIDUALMENTE en la
     * carrera.
     *
     * Es la otra mitad de la misma regla, para el caso inverso: se quiere inscribir
     * al equipo y hay que revisar que ninguno de sus cinco enanos se haya anotado
     * antes por su cuenta.
     *
     * La subconsulta saca los ids de los integrantes del equipo y el IN los busca
     * entre las inscripciones individuales de esa carrera.
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM RaceRegistration r
            WHERE r.race.id = :raceId
              AND r.status IN :statuses
              AND r.competitor.id IN (
                    SELECT m.id FROM Team t JOIN t.members m WHERE t.id = :teamId)
            """)
    boolean algunIntegranteYaCorreSolo(@Param("raceId") UUID raceId,
                                       @Param("teamId") UUID teamId,
                                       @Param("statuses") Collection<RegistrationStatus> statuses);
}
