package com.eia.camelracing.standings.repository;

import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.standings.dto.StandingRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Las dos consultas que arman la tabla de posiciones.
 *
 * POR QUE ES UN Repository Y NO UN JpaRepository
 * Porque las posiciones no son una entidad: no hay tabla standings, no hay nada
 * que guardar. Una tabla de posiciones es siempre una CUENTA sobre los resultados,
 * y guardarla seria tener un dato derivado que hay que acordarse de actualizar cada
 * vez que se corrige un resultado. Extender JpaRepository traeria save(), delete()
 * y findAll() sobre RaceResult, que aca no tienen sentido y que alguien podria
 * usar por error.
 *
 * Repository es la interfaz vacia de Spring Data: no aporta ningun metodo, solo
 * marca la interfaz para que se le generen las consultas declaradas.
 *
 * ESTA ES LA SEGUNDA COPIA DE LA ESCALA DE PUNTOS
 * La primera esta en ResultStatus.puntos(), en Java, y es la que usan las
 * respuestas de resultados. Esta esta en SQL porque la suma la tiene que hacer la
 * base: es lo unico que permite ordenar y paginar la tabla sin traerse todos los
 * resultados del campeonato a memoria. Si cambia el puntaje, HAY QUE TOCAR LOS DOS
 * LUGARES.
 */
public interface IStandingsRepository extends Repository<RaceResult, UUID> {

    /**
     * Los puntos, segun la tabla del enunciado: 10-7-5-3-1 para los cinco primeros.
     *
     * Se apoya en un invariante del modulo: SOLO los resultados FINISHED tienen
     * posicion final. Los otros tres estados la tienen en null, y en SQL una
     * comparacion contra null no es verdadera, asi que caen en el ELSE 0 sin
     * necesidad de preguntar por el estado. Ese invariante lo garantizan el
     * @AssertTrue de ResultRequest y las validaciones de ResultService.
     *
     * El fragmento esta en una constante y no escrito dos veces porque hace falta
     * en el SELECT y otra vez en el ORDER BY: JPQL no deja ordenar por un elemento
     * del constructor expression, asi que la unica forma de ordenar por puntos es
     * repetir la expresion. Concatenar constantes de compilacion adentro de la
     * anotacion evita que las dos copias se desincronicen.
     */
    String PUNTOS = """
            SUM(CASE WHEN r.finalPosition = 1 THEN 10
                     WHEN r.finalPosition = 2 THEN 7
                     WHEN r.finalPosition = 3 THEN 5
                     WHEN r.finalPosition = 4 THEN 3
                     WHEN r.finalPosition = 5 THEN 1
                     ELSE 0 END)""";

    String TERMINADAS = """
            SUM(CASE WHEN r.status = com.eia.camelracing.result.entity.ResultStatus.FINISHED
                     THEN 1 ELSE 0 END)""";

    String VICTORIAS = "SUM(CASE WHEN r.finalPosition = 1 THEN 1 ELSE 0 END)";

    /**
     * Derrotas: compitio y no gano.
     *
     * El unico que no suma es el que no largo (DID_NOT_START): no perdio nada. El
     * ganador tampoco, obviamente. Todo el resto —los que llegaron detras, los
     * descalificados y los que abandonaron— cuenta como derrota. Es el mismo
     * criterio que aplica ResultService al recalcular las estadisticas del
     * competidor, y esta explicado en ResultStatus.
     */
    String DERROTAS = """
            SUM(CASE WHEN r.status = com.eia.camelracing.result.entity.ResultStatus.DID_NOT_START THEN 0
                     WHEN r.finalPosition = 1 THEN 0
                     ELSE 1 END)""";

    /**
     * La tabla de posiciones de los competidores individuales.
     *
     * SOLO APARECE QUIEN TIENE AL MENOS UN RESULTADO. El JOIN con RaceResult es lo
     * que produce ese efecto, y es el correcto: una tabla de posiciones es de los
     * que compitieron. Alguien recien dado de alta no esta ultimo con cero puntos,
     * simplemente todavia no entro al campeonato.
     *
     * El desempate esta pensado: primero los puntos, despues las victorias (entre
     * dos con 10 puntos, va adelante el que gano una carrera antes que el que hizo
     * dos segundos puestos) y al final el apodo, que no es un criterio deportivo
     * pero garantiza que el orden sea siempre el mismo. Sin ese ultimo desempate,
     * dos empatados podrian intercambiarse de lugar entre dos llamadas y aparecer
     * repetidos o faltar al paginar.
     *
     * El countQuery cuenta participantes DISTINTOS, no resultados: la consulta
     * agrupa, asi que cada fila del resultado es un competidor y no una carrera.
     * Sin este countQuery explicito, totalElements diria cuantos resultados hay.
     */
    @Query(value = "SELECT new com.eia.camelracing.standings.dto.StandingRow("
            + "c.id, c.nickname, " + PUNTOS + ", " + TERMINADAS + ", " + VICTORIAS + ", " + DERROTAS + ") "
            + "FROM RaceResult r JOIN r.registration reg JOIN reg.competitor c "
            + "GROUP BY c.id, c.nickname "
            + "ORDER BY " + PUNTOS + " DESC, " + VICTORIAS + " DESC, c.nickname ASC",
            countQuery = "SELECT COUNT(DISTINCT c.id) "
                    + "FROM RaceResult r JOIN r.registration reg JOIN reg.competitor c")
    Page<StandingRow> posicionesDeCompetidores(Pageable pageable);

    /** La tabla de posiciones de los equipos. Mismo criterio que la de competidores. */
    @Query(value = "SELECT new com.eia.camelracing.standings.dto.StandingRow("
            + "t.id, t.name, " + PUNTOS + ", " + TERMINADAS + ", " + VICTORIAS + ", " + DERROTAS + ") "
            + "FROM RaceResult r JOIN r.registration reg JOIN reg.team t "
            + "GROUP BY t.id, t.name "
            + "ORDER BY " + PUNTOS + " DESC, " + VICTORIAS + " DESC, t.name ASC",
            countQuery = "SELECT COUNT(DISTINCT t.id) "
                    + "FROM RaceResult r JOIN r.registration reg JOIN reg.team t")
    Page<StandingRow> posicionesDeEquipos(Pageable pageable);
}
