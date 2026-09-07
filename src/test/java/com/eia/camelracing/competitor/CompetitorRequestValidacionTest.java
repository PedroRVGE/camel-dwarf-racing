package com.eia.camelracing.competitor;

import com.eia.camelracing.competitor.dto.CompetitorRequest;
import com.eia.camelracing.competitor.entity.CompetitorType;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las validaciones del alta de competidores, probadas donde viven: en el DTO.
 *
 * POR QUE SE PRUEBAN SIN LEVANTAR SPRING
 * Estas reglas no las escribe el servicio, las declaran las anotaciones del
 * record (@Positive, @Past, @Digits). Quien las hace cumplir es el validador de
 * Jakarta, y ese se puede construir a mano en dos lineas. Levantar el contexto de
 * Spring para probarlas seria pagar varios segundos por algo que no depende de
 * Spring en absoluto.
 *
 * Lo que SI depende de Spring es que el controlador devuelva 400 cuando esto
 * falla, y eso se prueba una sola vez en la capa de integracion, no una vez por
 * campo.
 */
@Tag(Capas.UNIT)
@DisplayName("Validaciones de CompetitorRequest (unitario)")
class CompetitorRequestValidacionTest {

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

    /** El caso de control: si esto fallara, los tests de abajo no probarian nada. */
    @Test
    @DisplayName("un alta correcta no tiene ni una violacion")
    void elAltaValidaPasa() {
        Set<ConstraintViolation<CompetitorRequest>> violaciones =
                validator.validate(Datos.pedidoDeCompetidor("martillo"));

        assertThat(violaciones).isEmpty();
    }

    /**
     * Enunciado: "Reject a competitor with invalid weight."
     *
     * Se prueban los dos numeros que no son un peso: el negativo y el cero. El cero
     * importa tanto como el negativo, porque es el que se cuela cuando el frontend
     * manda un campo vacio y alguien lo convierte a numero sin pensar.
     */
    @ParameterizedTest(name = "peso {0} kg")
    @ValueSource(strings = {"-1.00", "0.00", "-612.50"})
    @DisplayName("rechaza un peso que no es positivo")
    void rechazaPesoInvalido(String peso) {
        Set<ConstraintViolation<CompetitorRequest>> violaciones =
                validator.validate(Datos.pedidoDeCompetidorConPeso("martillo", peso));

        assertThat(violaciones)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("weightKg");
    }

    /**
     * El peso tampoco puede tener cuatro digitos enteros.
     *
     * No es un capricho: la columna es NUMERIC(5,2), o sea que entra hasta 999,99. Sin
     * esta validacion, un peso de 1000 pasaria el @Positive y reventaria despues en
     * la base con un error del driver, que no le sirve a nadie.
     */
    @Test
    @DisplayName("rechaza un peso que no entra en la columna de la base")
    void rechazaPesoDemasiadoGrande() {
        CompetitorRequest pedido = Datos.pedidoDeCompetidorConPeso("martillo", "1000.00");

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("weightKg");
    }

    /**
     * Un competidor que todavia no nacio.
     *
     * Es el mismo tipo de absurdo que el enunciado nombra con "a camel finishing
     * before the race began": datos que la base aceptaria sin chistar y que no
     * pueden existir en el mundo real.
     */
    @Test
    @DisplayName("rechaza una fecha de nacimiento en el futuro")
    void rechazaFechaDeNacimientoFutura() {
        CompetitorRequest pedido = new CompetitorRequest(
                "Competidor del manana", "manana", CompetitorType.CAMEL,
                LocalDate.now().plusDays(1),
                new BigDecimal("600.00"), new BigDecimal("200.00"), "Colombia", null);

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("dateOfBirth");
    }
}
