package com.eia.camelracing.result.repository;

import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.result.entity.ResultStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IRaceResultRepository extends JpaRepository<RaceResult, UUID> {

    /**
     * Un resultado con todo lo que hace falta para mostrarlo.
     *
     * El @EntityGraph baja hasta el segundo nivel: la inscripcion, y adentro de
     * ella el competidor y el equipo. Sin eso, mapear un solo resultado dispara
     * cuatro consultas extra, porque todas esas relaciones son LAZY y el mapper
     * necesita el nombre del participante.
     */
    @Override
    @EntityGraph(attributePaths = {"race", "registration",
            "registration.competitor", "registration.team"})
    Optional<RaceResult> findById(UUID id);

    /**
     * La clasificacion de una carrera.
     *
     * El @EntityGraph es seguro con paginacion porque todas las relaciones que trae
     * son @ManyToOne: cada resultado tiene UNA carrera y UNA inscripcion, asi que el
     * JOIN no multiplica filas y el LIMIT sigue contando resultados.
     *
     * El orden lo pone el Pageable, con final_position ascendente por defecto.
     * Postgres manda los NULL al final en un ORDER BY ascendente, que es justo lo
     * que se quiere: primero los que terminaron, en orden de llegada, y despues los
     * que no.
     */
    @EntityGraph(attributePaths = {"race", "registration",
            "registration.competitor", "registration.team"})
    Page<RaceResult> findByRaceId(UUID raceId, Pageable pageable);

    /** Si esta inscripcion ya tiene resultado cargado. Un participante, un resultado. */
    boolean existsByRegistrationId(UUID registrationId);

    /**
     * Los resultados de una carrera en un estado dado, sin paginar.
     *
     * Lo usa la validacion de coherencia entre puestos y tiempos, que necesita ver
     * TODOS los que llegaron para poder compararlos entre si. No se pagina a
     * proposito: una carrera tiene como mucho cincuenta participantes (lo limita
     * maxParticipants), asi que la lista completa es chica y traerla es mas simple y
     * mas barato que ir de a pedazos.
     */
    List<RaceResult> findByRaceIdAndStatus(UUID raceId, ResultStatus status);

    /**
     * Si el puesto ya esta tomado en esta carrera por OTRO resultado.
     *
     * El parametro excluirId es lo que permite usar la misma consulta al cargar y al
     * corregir. Al cargar viene en null y se controlan todos los resultados; al
     * corregir viene el id del que se esta editando, para que no se choque consigo
     * mismo: sin eso, guardar un primer puesto sin cambiarle la posicion daria
     * "el puesto 1 ya esta ocupado", y lo estaria ocupando el mismo.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM RaceResult r
            WHERE r.race.id = :raceId
              AND r.finalPosition = :posicion
              AND (:excluirId IS NULL OR r.id <> :excluirId)
            """)
    boolean posicionOcupada(@Param("raceId") UUID raceId,
                            @Param("posicion") Integer posicion,
                            @Param("excluirId") UUID excluirId);

    /**
     * Cuantos participantes aprobados de la carrera todavia no tienen resultado.
     *
     * ES LA CONSULTA QUE SOSTIENE "A RACE CANNOT BE COMPLETED WITHOUT OFFICIAL
     * RESULTS". Se podria haber interpretado esa regla como "que haya al menos un
     * resultado", pero eso dejaria dar por terminada una carrera de ocho con un solo
     * tiempo cargado, y la clasificacion quedaria falsa: los otros siete no
     * figurarian ni como abandonos.
     *
     * El NOT EXISTS con subconsulta correlacionada es la forma directa de preguntar
     * "aprobados a los que les falta resultado". La alternativa, traer las dos
     * listas y restarlas en Java, hace el mismo trabajo en la aplicacion y ademas
     * mueve todas las filas por la red.
     */
    @Query("""
            SELECT COUNT(reg) FROM RaceRegistration reg
            WHERE reg.race.id = :raceId
              AND reg.status = com.eia.camelracing.registration.entity.RegistrationStatus.APPROVED
              AND NOT EXISTS (SELECT 1 FROM RaceResult r WHERE r.registration = reg)
            """)
    long aprobadasSinResultado(@Param("raceId") UUID raceId);

    /**
     * Todos los resultados de un competidor, en sus inscripciones individuales.
     *
     * Lo usa el recalculo de estadisticas. Devuelve las entidades y no un conteo
     * porque hay que sacar tres numeros distintos de la misma lista (victorias,
     * derrotas y carreras terminadas), y hacerlo con tres COUNT serian tres viajes a
     * la base para leer las mismas filas.
     *
     * POR QUE NO INCLUYE LAS CARRERAS QUE CORRIO CON SU EQUIPO
     * Es la decision de fondo del modulo, y esta explicada en ResultService: un
     * resultado pertenece a quien figura en la inscripcion. Si un enano corre con
     * "The Five Exceptions" y el equipo gana, la victoria es del equipo.
     */
    @Query("""
            SELECT r FROM RaceResult r
            JOIN r.registration reg
            WHERE reg.competitor.id = :competitorId
            """)
    List<RaceResult> resultadosDeCompetidor(@Param("competitorId") UUID competitorId);

    /** Todos los resultados de un equipo. Mismo uso que el anterior. */
    @Query("""
            SELECT r FROM RaceResult r
            JOIN r.registration reg
            WHERE reg.team.id = :teamId
            """)
    List<RaceResult> resultadosDeEquipo(@Param("teamId") UUID teamId);
}
