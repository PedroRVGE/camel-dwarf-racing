package com.eia.camelracing.competitor.repository;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICompetitorRepository extends JpaRepository<Competitor, UUID> {

    /**
     * Buscar uno, trayendo su equipo en la MISMA consulta.
     *
     * CompetitorMapper necesita el nombre del equipo y la relacion es LAZY. Sin
     * este @EntityGraph, cada competidor que se mapea dispara una consulta extra:
     * es el problema N+1.
     */
    @Override
    @EntityGraph(attributePaths = "team")
    Optional<Competitor> findById(UUID id);

    /**
     * El listado, con filtros, paginacion y ordenamiento. El enunciado los exige
     * los tres.
     *
     * COMO FUNCIONAN LOS FILTROS OPCIONALES
     * El patron (:param IS NULL OR campo = :param) es lo que permite que un mismo
     * metodo sirva para todas las combinaciones: si el parametro no vino, esa
     * condicion es verdadera para todas las filas y el filtro desaparece.
     *
     * La alternativa habitual son las Specifications de Spring Data, que arman la
     * consulta con objetos. Para cuatro filtros fijos como estos son mucho mas
     * codigo y bastante menos legible: aca la consulta se lee como el SQL que va a
     * terminar siendo.
     *
     * OJO CON EL BUSCADOR DE TEXTO: EL PATRON SE ARMA EN JAVA, NO ACA
     * El parametro llega ya con los porcentajes puestos y en minuscula
     * ("%docker%"), en vez de escribir CONCAT('%', :texto, '%') dentro de la
     * consulta. No es una cuestion de gusto: la version con CONCAT reventaba.
     *
     * Cuando el filtro no se usa, el parametro va en null, y ahi Postgres tiene
     * que deducir de que tipo es un NULL sin tipo declarado. Metido adentro de una
     * concatenacion, el operador || esta sobrecargado —sirve para texto y para
     * bytea— y Postgres elegia bytea. El resultado era un 500 con
     * "function lower(bytea) does not exist" en TODO listado que no filtrara por
     * texto, o sea el caso mas comun de todos.
     *
     * Con el patron armado afuera, el parametro aparece una sola vez y en una
     * posicion donde su tipo es evidente (LOWER(columna) LIKE :patron), asi que no
     * hay nada que deducir.
     *
     * LOWER() de los dos lados hace la busqueda insensible a mayusculas, asi que
     * "byte" encuentra a "Byte". Tiene un costo: al aplicar una funcion sobre la
     * columna, la base no puede usar el indice de nickname y termina recorriendo
     * la tabla. Para el tamano de esta liga no importa; si creciera, la solucion
     * es un indice funcional sobre LOWER(nickname).
     *
     * El @EntityGraph aca es seguro porque team es un @ManyToOne: cada competidor
     * tiene UN equipo, asi que el JOIN no multiplica filas y la paginacion sigue
     * funcionando. Con una coleccion habria que evitarlo (ver TeamSummaryResponse).
     */
    @EntityGraph(attributePaths = "team")
    @Query("""
            SELECT c FROM Competitor c
            WHERE (:type IS NULL OR c.type = :type)
              AND (:status IS NULL OR c.status = :status)
              AND (:teamId IS NULL OR c.team.id = :teamId)
              AND (:patron IS NULL
                   OR LOWER(c.name) LIKE :patron
                   OR LOWER(c.nickname) LIKE :patron)
            """)
    Page<Competitor> buscar(@Param("type") CompetitorType type,
                            @Param("status") CompetitorStatus status,
                            @Param("teamId") UUID teamId,
                            @Param("patron") String patron,
                            Pageable pageable);

    /**
     * Si ya hay alguien con ese apodo.
     *
     * Es el chequeo previo que permite devolver un 409 con un mensaje que explica
     * cual es el problema, en vez de dejar que reviente la unique constraint y
     * tener que traducir un error de Postgres.
     */
    boolean existsByNicknameIgnoreCase(String nickname);

    /**
     * Lo mismo, pero ignorando al propio competidor.
     *
     * Hace falta al editar: sin el "AndIdNot", guardar un competidor sin cambiarle
     * el apodo daria "ese apodo ya existe", porque se encontraria a si mismo.
     */
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, UUID id);

    /** Los integrantes de un equipo. Lo usa el modulo de equipos. */
    List<Competitor> findByTeamId(UUID teamId);

    /** Cuantos integrantes tiene un equipo, sin traerlos. Se usa para el maximo configurable. */
    long countByTeamId(UUID teamId);
}
