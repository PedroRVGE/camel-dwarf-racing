package com.eia.camelracing.result;

import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.support.Capas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La escala de puntos del enunciado.
 *
 * POR QUE MERECE SUS PROPIOS TESTS
 * Es la unica regla del proyecto que esta escrita DOS veces: en Java, dentro de
 * ResultStatus, y en SQL, dentro de IStandingsRepository. La duplicacion es
 * deliberada (la suma la tiene que hacer la base para poder ordenar y paginar sin
 * traerse todos los resultados a memoria), pero una regla duplicada es una regla
 * que se puede desincronizar.
 *
 * Estos tests fijan la version de Java. La de SQL la fija StandingsRepositoryIT,
 * en la capa de integracion, comparando contra los mismos numeros. Si alguien
 * cambia el puntaje en un solo lado, uno de los dos se pone rojo.
 */
@Tag(Capas.UNIT)
@DisplayName("La escala de puntos (unitario)")
class ResultStatusTest {

    /**
     * La tabla tal cual esta en el enunciado: 10, 7, 5, 3, 1 y nada del sexto en
     * adelante.
     */
    @ParameterizedTest(name = "puesto {0} suma {1} puntos")
    @CsvSource({"1, 10", "2, 7", "3, 5", "4, 3", "5, 1", "6, 0", "7, 0", "20, 0"})
    @DisplayName("le da a cada puesto los puntos del enunciado")
    void laEscalaEsLaDelEnunciado(int puesto, int puntosEsperados) {
        assertThat(ResultStatus.FINISHED.puntos(puesto)).isEqualTo(puntosEsperados);
    }

    /**
     * El que no llego a la meta no suma, tenga el puesto que tenga.
     *
     * El @EnumSource con exclusion es a proposito: si manana se agrega un estado
     * nuevo (un "RETIRED_BY_JUDGE", por ejemplo), este test lo incluye solo y
     * obliga a decidir cuantos puntos suma, en vez de dejarlo pasar en silencio.
     */
    @ParameterizedTest(name = "{0} no suma puntos")
    @EnumSource(value = ResultStatus.class, names = "FINISHED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("no le da puntos a nadie que no haya terminado")
    void losQueNoTerminaronNoSuman(ResultStatus estado) {
        assertThat(estado.puntos(1)).isZero();
        assertThat(estado.puntos(null)).isZero();
        assertThat(estado.admitePosicionYTiempo()).isFalse();
    }

    /**
     * Un FINISHED sin puesto tampoco suma.
     *
     * En teoria no puede existir, porque lo impide la constraint
     * ck_results_coherencia_estado. El metodo lo contempla igual, y esta bien que lo
     * haga: un dato roto tiene que dar cero, no una excepcion de puntero nulo en el
     * medio del calculo de la tabla de posiciones.
     */
    @Test
    @DisplayName("un FINISHED sin puesto suma cero en vez de romper")
    void finishedSinPuestoSumaCero() {
        assertThat(ResultStatus.FINISHED.puntos(null)).isZero();
    }

    /** Solo FINISHED cuenta como llegada, y solo DISQUALIFIED y DID_NOT_FINISH como derrota. */
    @Test
    @DisplayName("distingue llegada, derrota y ausencia")
    void distingueLosTresCasos() {
        assertThat(ResultStatus.FINISHED.llegoALaMeta()).isTrue();
        assertThat(ResultStatus.FINISHED.cuentaComoDerrota()).isFalse();

        assertThat(ResultStatus.DISQUALIFIED.cuentaComoDerrota()).isTrue();
        assertThat(ResultStatus.DID_NOT_FINISH.cuentaComoDerrota()).isTrue();

        // El que no largo no compitio: no es una derrota, es una ausencia.
        assertThat(ResultStatus.DID_NOT_START.cuentaComoDerrota()).isFalse();
    }
}
