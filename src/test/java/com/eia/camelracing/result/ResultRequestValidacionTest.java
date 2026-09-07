package com.eia.camelracing.result;

import com.eia.camelracing.result.dto.ResultRequest;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.support.Capas;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La coherencia entre el estado de un resultado y sus datos de llegada.
 *
 * LA REGLA, EN UNA LINEA
 * Si termino, hay puesto y tiempo. Si no termino, no hay ninguno de los dos.
 *
 * De ahi sale, sin escribirla aparte, la regla del enunciado "a disqualified
 * participant cannot win": un descalificado no puede tener puesto, asi que tampoco
 * puede tener el puesto 1.
 */
@Tag(Capas.UNIT)
@DisplayName("Coherencia de ResultRequest (unitario)")
class ResultRequestValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirElValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarElValidador() {
        factory.close();
    }

    @Test
    @DisplayName("acepta una llegada con puesto y tiempo")
    void laLlegadaCompletaPasa() {
        ResultRequest pedido = new ResultRequest(
                UUID.randomUUID(), ResultStatus.FINISHED, 498, 0, 1, null);

        assertThat(validator.validate(pedido)).isEmpty();
    }

    @Test
    @DisplayName("acepta un abandono sin puesto ni tiempo")
    void elAbandonoSinDatosPasa() {
        ResultRequest pedido = new ResultRequest(
                UUID.randomUUID(), ResultStatus.DID_NOT_FINISH, null, 0, null,
                "Abandono en el kilometro 800");

        assertThat(validator.validate(pedido)).isEmpty();
    }

    @Test
    @DisplayName("rechaza una llegada sin tiempo ni puesto")
    void rechazaLlegadaIncompleta() {
        ResultRequest pedido = new ResultRequest(
                UUID.randomUUID(), ResultStatus.FINISHED, null, 0, null, null);

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("coherenteConElEstado");
    }

    /**
     * El caso que el enunciado nombra: un descalificado no puede ganar.
     *
     * No hace falta una regla que diga "el puesto 1 no puede ser de un
     * descalificado". Alcanza con que un descalificado no pueda tener NINGUN puesto:
     * la regla mas general cubre la particular y no se puede olvidar de un caso.
     */
    @Test
    @DisplayName("rechaza a un descalificado con puesto: un descalificado no puede ganar")
    void rechazaDescalificadoConPuesto() {
        ResultRequest pedido = new ResultRequest(
                UUID.randomUUID(), ResultStatus.DISQUALIFIED, 480, 0, 1,
                "Invadio el carril vecino");

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("coherenteConElEstado");
    }

    /** Un tiempo de carrera negativo o cero no es un tiempo. */
    @Test
    @DisplayName("rechaza un tiempo de carrera que no es positivo")
    void rechazaTiempoNoPositivo() {
        ResultRequest pedido = new ResultRequest(
                UUID.randomUUID(), ResultStatus.FINISHED, 0, 0, 1, null);

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("completionTimeSeconds");
    }
}
