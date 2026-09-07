package com.eia.camelracing.standings;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.standings.dto.StandingRow;
import com.eia.camelracing.standings.repository.IStandingsRepository;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.PostgresTestBase;
import com.eia.camelracing.team.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static com.eia.camelracing.support.Datos.carrera;
import static com.eia.camelracing.support.Datos.competidor;
import static com.eia.camelracing.support.Datos.equipo;
import static com.eia.camelracing.support.Datos.inscripcion;
import static com.eia.camelracing.support.Datos.inscripcionDeEquipo;
import static com.eia.camelracing.support.Datos.resultado;
import static com.eia.camelracing.support.Datos.resultadoSinLlegar;
import static com.eia.camelracing.support.Datos.sinId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * La tabla de posiciones: la consulta mas complicada del proyecto.
 *
 * POR QUE ESTA CONSULTA NO SE PUEDE PROBAR DE OTRA MANERA
 * Suma puntos con un CASE de cinco ramas, agrupa por participante, ordena por tres
 * criterios y ademas se pagina. Es exactamente el tipo de consulta con la que H2 y
 * Postgres NO se comportan igual: H2 es mucho mas permisivo con lo que se puede
 * poner en un GROUP BY, asi que un test verde en H2 no probaria nada sobre lo que
 * pasa en produccion.
 *
 * ADEMAS, ESTE ES EL TEST QUE SINCRONIZA LA REGLA DUPLICADA
 * La escala de puntos (10-7-5-3-1) esta escrita dos veces: en Java, dentro de
 * ResultStatus, y en SQL, adentro de esta consulta. La duplicacion es deliberada,
 * porque la suma la tiene que hacer la base para poder ordenar y paginar sin
 * traerse todos los resultados a memoria.
 *
 * ResultStatusTest fija la version de Java contra los mismos numeros que fija esta
 * clase para la de SQL. Si alguien cambia el puntaje en un solo lado, uno de los
 * dos se pone rojo, que es justo lo que se necesita de una regla duplicada.
 */
@Tag(Capas.INTEGRACION)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("La tabla de posiciones contra Postgres (integracion)")
class StandingsRepositoryIT extends PostgresTestBase {

    @Autowired
    private IStandingsRepository repository;

    @Autowired
    private TestEntityManager em;

    /**
     * Una carrera completa: cinco que puntuan y tres que no.
     *
     * Se cargan los cinco puestos que dan puntos porque, con menos, un error en una
     * sola rama del CASE pasaria desapercibido.
     */
    @BeforeEach
    void correrUnaCarrera() {
        Race laCarrera = em.persistAndFlush(sinId(carrera(RaceStatus.COMPLETED)));

        anotarYCorrer(laCarrera, "zafira", 1, 498);
        anotarYCorrer(laCarrera, "ramses", 2, 521);
        anotarYCorrer(laCarrera, "martillo", 3, 705);
        anotarYCorrer(laCarrera, "yunque", 4, 742);
        anotarYCorrer(laCarrera, "trotalomas", 5, 806);

        anotarSinLlegar(laCarrera, "barbanegra", ResultStatus.DID_NOT_FINISH);
        anotarSinLlegar(laCarrera, "cascoduro", ResultStatus.DISQUALIFIED);
        anotarSinLlegar(laCarrera, "cascabel", ResultStatus.DID_NOT_START);
    }

    /**
     * La escala del enunciado, calculada por Postgres.
     *
     * Los puntos se comprueban uno por uno y en orden, porque el orden TAMBIEN es
     * parte de la consulta: ordena por puntos, despues por victorias y despues por
     * nombre.
     */
    @Test
    @DisplayName("suma 10-7-5-3-1 y ordena de mayor a menor")
    void laEscalaDePuntosEsLaDelEnunciado() {
        Page<StandingRow> tabla = repository.posicionesDeCompetidores(PageRequest.of(0, 20));

        assertThat(tabla.getContent())
                .extracting(StandingRow::participantName, StandingRow::points)
                .containsExactly(
                        tuple("zafira", 10L),
                        tuple("ramses", 7L),
                        tuple("martillo", 5L),
                        tuple("yunque", 3L),
                        tuple("trotalomas", 1L),
                        // Los tres que no llegaron entran igual, con cero puntos, y
                        // desempatan por nombre. Aparecer en la tabla con cero no es lo
                        // mismo que no aparecer: significa que compitieron.
                        tuple("barbanegra", 0L),
                        tuple("cascabel", 0L),
                        tuple("cascoduro", 0L));
    }

    /**
     * Las tres columnas que acompanan a los puntos.
     *
     * La distincion importante esta en el ultimo: el que NO LARGO no suma derrota.
     * No compitio, asi que no perdio. El descalificado y el que abandono si.
     */
    @Test
    @DisplayName("cuenta terminadas, victorias y derrotas por separado")
    void cuentaBienLasEstadisticas() {
        Page<StandingRow> tabla = repository.posicionesDeCompetidores(PageRequest.of(0, 20));

        StandingRow ganador = buscar(tabla, "zafira");
        assertThat(ganador.victories()).isEqualTo(1);
        assertThat(ganador.racesFinished()).isEqualTo(1);
        assertThat(ganador.defeats()).isZero();

        StandingRow descalificado = buscar(tabla, "cascoduro");
        assertThat(descalificado.victories()).isZero();
        assertThat(descalificado.racesFinished()).isZero();
        assertThat(descalificado.defeats()).isEqualTo(1);

        // El que no se presento a la largada: ni victoria ni derrota.
        StandingRow ausente = buscar(tabla, "cascabel");
        assertThat(ausente.racesFinished()).isZero();
        assertThat(ausente.defeats()).isZero();
    }

    /**
     * La paginacion sobre una consulta agrupada.
     *
     * Es el punto donde una consulta agrupada se rompe con mas facilidad: el COUNT
     * que Spring Data deriva solo para saber cuantas paginas hay contaria FILAS DE
     * RESULTADO en vez de PARTICIPANTES, y la tabla diria que hay ocho paginas de
     * una. Por eso la consulta declara su propio countQuery con COUNT(DISTINCT), y
     * esto es lo que lo comprueba.
     */
    @Test
    @DisplayName("pagina sin equivocarse en el total de participantes")
    void paginaBien() {
        Page<StandingRow> primera = repository.posicionesDeCompetidores(PageRequest.of(0, 3));

        assertThat(primera.getContent()).hasSize(3);
        assertThat(primera.getTotalElements()).isEqualTo(8);
        assertThat(primera.getTotalPages()).isEqualTo(3);

        Page<StandingRow> segunda = repository.posicionesDeCompetidores(PageRequest.of(1, 3));
        assertThat(segunda.getContent())
                .extracting(StandingRow::participantName)
                .containsExactly("yunque", "trotalomas", "barbanegra");
    }

    /**
     * La tabla de equipos es otra consulta, sobre las mismas filas.
     *
     * Un resultado pertenece a QUIEN FIGURA EN LA INSCRIPCION: si corrio un equipo,
     * la victoria es del equipo y no de sus integrantes. Por eso el equipo de este
     * test aparece en la tabla de equipos y no en la de competidores, aunque tenga
     * integrantes que si corrieron por su cuenta en la misma base.
     */
    @Test
    @DisplayName("arma la tabla de equipos con los resultados de las inscripciones de equipo")
    void laTablaDeEquiposEsIndependiente() {
        Race otraCarrera = em.persistAndFlush(sinId(carrera(RaceStatus.COMPLETED)));
        Team barbas = em.persistAndFlush(sinId(equipo("Barbas de Hierro")));
        Team medianos = em.persistAndFlush(sinId(equipo("Los Medianos de la Loma")));

        RaceRegistration deBarbas = em.persistAndFlush(
                sinId(inscripcionDeEquipo(otraCarrera, barbas, RegistrationStatus.APPROVED)));
        RaceRegistration deMedianos = em.persistAndFlush(
                sinId(inscripcionDeEquipo(otraCarrera, medianos, RegistrationStatus.APPROVED)));

        em.persistAndFlush(sinId(resultado(deBarbas, 1, 1240)));
        em.persistAndFlush(sinId(resultado(deMedianos, 2, 1395)));

        assertThat(repository.posicionesDeEquipos(PageRequest.of(0, 10)).getContent())
                .extracting(StandingRow::participantName, StandingRow::points)
                .containsExactly(
                        tuple("Barbas de Hierro", 10L),
                        tuple("Los Medianos de la Loma", 7L));

        // Y los ocho competidores individuales siguen siendo ocho: el equipo no se
        // colo en la tabla individual.
        assertThat(repository.posicionesDeCompetidores(PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(8);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void anotarYCorrer(Race carrera, String apodo, int puesto, int segundos) {
        Competitor competidor = em.persistAndFlush(sinId(competidor(apodo)));
        RaceRegistration anotado = em.persistAndFlush(
                sinId(inscripcion(carrera, competidor, RegistrationStatus.APPROVED)));
        em.persistAndFlush(sinId(resultado(anotado, puesto, segundos)));
    }

    private void anotarSinLlegar(Race carrera, String apodo, ResultStatus estado) {
        Competitor competidor = em.persistAndFlush(sinId(competidor(apodo)));
        RaceRegistration anotado = em.persistAndFlush(
                sinId(inscripcion(carrera, competidor, RegistrationStatus.APPROVED)));
        em.persistAndFlush(sinId(resultadoSinLlegar(anotado, estado)));
    }

    private StandingRow buscar(Page<StandingRow> tabla, String nombre) {
        return tabla.getContent().stream()
                .filter(fila -> fila.participantName().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No aparece '" + nombre + "' en la tabla"));
    }
}
