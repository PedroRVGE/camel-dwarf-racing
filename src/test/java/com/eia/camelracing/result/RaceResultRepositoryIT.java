package com.eia.camelracing.result;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.result.repository.IRaceResultRepository;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.PostgresTestBase;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static com.eia.camelracing.support.Datos.carrera;
import static com.eia.camelracing.support.Datos.competidor;
import static com.eia.camelracing.support.Datos.inscripcion;
import static com.eia.camelracing.support.Datos.resultado;
import static com.eia.camelracing.support.Datos.resultadoSinLlegar;
import static com.eia.camelracing.support.Datos.sinId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las reglas de los resultados que viven EN LA BASE, no en el codigo.
 *
 * Las tres que se prueban aca estan escritas dos veces a proposito: una vez en
 * ResultService, que es la que da el mensaje 409 entendible, y una vez como
 * constraint en la migracion V3, que es la que hace que la regla no se pueda
 * violar ni escribiendo directo con psql.
 *
 * ResultServiceTest cubre la primera mitad. Esta clase cubre la segunda, que es la
 * que de verdad garantiza el dato: sin ella, un "no puede haber dos ganadores"
 * podria ser una promesa que solo cumple el camino feliz de la API.
 */
@Tag(Capas.INTEGRACION)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Las constraints de race_results contra Postgres (integracion)")
class RaceResultRepositoryIT extends PostgresTestBase {

    @Autowired
    private IRaceResultRepository repository;

    @Autowired
    private TestEntityManager em;

    private Race laCarrera;
    private RaceRegistration delGanador;
    private RaceRegistration delSegundo;

    @BeforeEach
    void armarLaCarrera() {
        laCarrera = em.persistAndFlush(sinId(carrera(RaceStatus.IN_PROGRESS)));

        Competitor ganador = em.persistAndFlush(sinId(competidor("zafira")));
        Competitor segundo = em.persistAndFlush(sinId(competidor("ramses")));

        delGanador = em.persistAndFlush(
                sinId(inscripcion(laCarrera, ganador, RegistrationStatus.APPROVED)));
        delSegundo = em.persistAndFlush(
                sinId(inscripcion(laCarrera, segundo, RegistrationStatus.APPROVED)));
    }

    /**
     * Enunciado: "Only one official winner is allowed."
     *
     * La constraint es UNIQUE (race_id, final_position), o sea que en realidad
     * prohibe repetir CUALQUIER puesto, no solo el primero. La regla general cubre
     * la particular y ademas evita el otro absurdo: dos terceros puestos.
     */
    @Test
    @DisplayName("la base rechaza un segundo ganador en la misma carrera")
    void noPuedeHaberDosGanadores() {
        em.persistAndFlush(sinId(resultado(delGanador, 1, 498)));

        RaceResult otroPrimero = sinId(resultado(delSegundo, 1, 512));

        assertThatThrownBy(() -> em.persistAndFlush(otroPrimero))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_results_race_position");
    }

    /**
     * Los que no llegaron a la meta conviven sin chocar entre si.
     *
     * Es la contracara del test anterior y la razon por la que la constraint puede
     * ser tan simple: Postgres admite varios NULL dentro de una restriccion unica.
     * Todos los abandonos tienen final_position en null y ninguno choca con otro.
     * Sin esa particularidad habria que inventar un puesto ficticio para cada
     * participante que no termino.
     */
    @Test
    @DisplayName("varios participantes sin puesto conviven sin violar la constraint")
    void losQueNoLlegaronNoChocanEntreSi() {
        em.persistAndFlush(sinId(resultadoSinLlegar(delGanador, ResultStatus.DID_NOT_FINISH)));
        em.persistAndFlush(sinId(resultadoSinLlegar(delSegundo, ResultStatus.DISQUALIFIED)));

        assertThat(repository.findByRaceIdAndStatus(laCarrera.getId(), ResultStatus.FINISHED))
                .isEmpty();
    }

    /**
     * La consulta que sostiene la regla "no se puede terminar una carrera a medias".
     *
     * Cuenta los participantes aprobados que todavia no tienen resultado, con una
     * subconsulta NOT EXISTS correlacionada. Es la clase de consulta que un mock
     * jamas podria validar, y de la que depende que una carrera no quede COMPLETED
     * con la clasificacion incompleta para siempre.
     */
    @Test
    @DisplayName("cuenta los aprobados que todavia no tienen resultado")
    void cuentaLosQueFaltanCargar() {
        assertThat(repository.aprobadasSinResultado(laCarrera.getId())).isEqualTo(2);

        em.persistAndFlush(sinId(resultado(delGanador, 1, 498)));
        assertThat(repository.aprobadasSinResultado(laCarrera.getId())).isEqualTo(1);

        em.persistAndFlush(sinId(resultado(delSegundo, 2, 512)));
        assertThat(repository.aprobadasSinResultado(laCarrera.getId())).isZero();
    }

    /**
     * Un participante, un resultado.
     *
     * Dos filas para la misma inscripcion serian dos afirmaciones sobre el mismo
     * hecho, y las estadisticas del competidor lo contarian dos veces.
     */
    @Test
    @DisplayName("la base rechaza dos resultados para la misma inscripcion")
    void unaInscripcionNoPuedeTenerDosResultados() {
        em.persistAndFlush(sinId(resultado(delGanador, 1, 498)));

        RaceResult duplicado = sinId(resultado(delGanador, 2, 600));

        assertThatThrownBy(() -> em.persistAndFlush(duplicado))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_results_registration");
    }
}
