package com.eia.camelracing.race;

import com.eia.camelracing.race.dto.RaceRequest;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos reglas de fecha de una carrera.
 *
 * Las dos viven en el DTO y no en el servicio, y no es lo mismo: una regla
 * declarada en el DTO devuelve 400 con el nombre del campo, que es lo que un
 * formulario necesita para marcar en rojo el casillero equivocado. La misma regla
 * escrita en el servicio devolveria 409 con un texto suelto.
 */
@Tag(Capas.UNIT)
@DisplayName("Validaciones de RaceRequest (unitario)")
class RaceRequestValidacionTest {

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
    @DisplayName("una carrera bien programada no tiene ni una violacion")
    void laCarreraValidaPasa() {
        assertThat(validator.validate(Datos.pedidoDeCarrera("Gran Premio EIA"))).isEmpty();
    }

    /**
     * Enunciado: "Reject a race scheduled in the past."
     *
     * Esta regla NO esta en la base de datos, y es a proposito: un CHECK tiene que
     * ser inmutable, y "la fecha tiene que estar en el futuro" deja de cumplirse
     * sola con el paso del tiempo. Toda carrera ya corrida volveria invalida la
     * tabla entera. Por eso vive en el @Future del DTO, que se evalua al escribir y
     * no para siempre. La explicacion larga esta en la migracion V2.
     */
    @Test
    @DisplayName("rechaza una carrera programada en el pasado")
    void rechazaCarreraEnElPasado() {
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        RaceRequest pedido = Datos.pedidoDeCarrera("Carrera de ayer", ayer, ayer.minusDays(1));

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("scheduledAt");
    }

    /**
     * El cierre de inscripciones tiene que ser anterior a la largada.
     *
     * Es una regla que compara DOS campos entre si, asi que ninguna anotacion suelta
     * la puede expresar: se resuelve con un @AssertTrue sobre un metodo del propio
     * record. El nombre del metodo (isCierreAntesDeLaLargada) es lo que aparece como
     * campo en la respuesta de error, por eso conviene que se lea como la regla.
     */
    @Test
    @DisplayName("rechaza un cierre de inscripciones posterior a la largada")
    void rechazaCierreDespuesDeLaLargada() {
        LocalDateTime largada = LocalDateTime.now().plusDays(10);
        RaceRequest pedido = Datos.pedidoDeCarrera("Carrera al reves", largada, largada.plusDays(1));

        assertThat(validator.validate(pedido))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("cierreAntesDeLaLargada");
    }
}
