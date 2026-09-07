package com.eia.camelracing.race;

import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.race.dto.RaceResponse;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.race.service.RaceService;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.result.repository.IRaceResultRepository;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El ciclo de vida de una carrera: quien la crea, cuando se puede editar y cuando
 * se puede dar por terminada.
 */
@Tag(Capas.UNIT)
@ExtendWith(MockitoExtension.class)
@DisplayName("RaceService (unitario)")
class RaceServiceTest {

    @Mock
    private IRaceRepository raceRepository;
    @Mock
    private IRaceRegistrationRepository registrationRepository;
    @Mock
    private IRaceResultRepository resultRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RaceService service;

    /**
     * Enunciado: "Create a valid race."
     *
     * Las dos cosas que se verifican no vienen del cuerpo del pedido, y por eso
     * importan: la carrera nace en DRAFT (no publicada, nadie se puede anotar
     * todavia) y el organizador sale del TOKEN, no de un campo que el cliente pueda
     * mandar. Si saliera del cuerpo, cualquiera podria crear carreras a nombre de
     * otro.
     */
    @Test
    @DisplayName("crea una carrera valida en DRAFT y con el organizador del token")
    void creaUnaCarreraValida() {
        when(currentUser.username()).thenReturn("organizer");
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        RaceResponse creada = service.createRace(Datos.pedidoDeCarrera("Gran Premio EIA"));

        assertThat(creada.name()).isEqualTo("Gran Premio EIA");
        assertThat(creada.status()).isEqualTo(RaceStatus.DRAFT);
        assertThat(creada.organizer()).isEqualTo("organizer");
        assertThat(creada.approvedCount()).isZero();
    }

    /**
     * "A completed race cannot be edited."
     *
     * Cambiarle la distancia o la fecha a una carrera ya corrida falsea las
     * condiciones en las que se compitio: los tiempos que estan cargados dejarian de
     * corresponder al recorrido que dice la carrera.
     */
    @Test
    @DisplayName("no deja editar una carrera ya terminada")
    void noSePuedeEditarUnaCarreraTerminada() {
        Race terminada = Datos.carrera(RaceStatus.COMPLETED);
        when(raceRepository.findById(terminada.getId())).thenReturn(Optional.of(terminada));

        assertThatThrownBy(() -> service.updateRace(terminada.getId(),
                Datos.pedidoDeCarrera("Otro nombre")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");

        verify(raceRepository, never()).save(any(Race.class));
    }

    /**
     * Una carrera no se puede dar por terminada si falta cargar resultados.
     *
     * Es la regla que cierra el circuito: sin ella, la clasificacion oficial de una
     * carrera COMPLETED podria estar incompleta para siempre, y la tabla de
     * posiciones estaria sumando puntos de una competencia a medio cargar.
     *
     * El mensaje dice CUANTOS faltan, y el test lo comprueba: un "no se puede
     * terminar" a secas obliga a ir a mirar la base para saber que falta.
     */
    @Test
    @DisplayName("no deja terminar una carrera con participantes sin resultado")
    void noSePuedeTerminarSinResultados() {
        Race enCurso = Datos.carrera(RaceStatus.IN_PROGRESS);
        UUID id = enCurso.getId();
        when(raceRepository.findById(id)).thenReturn(Optional.of(enCurso));
        when(resultRepository.aprobadasSinResultado(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.changeStatus(id, RaceStatus.COMPLETED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2");

        assertThat(enCurso.getStatus()).isEqualTo(RaceStatus.IN_PROGRESS);
        verify(raceRepository, never()).save(any(Race.class));
    }

    /**
     * Pedir el estado que la carrera ya tiene no es un error.
     *
     * Parece un detalle y no lo es: un frontend que reintenta un PATCH que ya habia
     * funcionado (porque se corto la conexion, por ejemplo) recibiria un 409 y le
     * mostraria al usuario un error por una operacion que en realidad salio bien.
     */
    @Test
    @DisplayName("pedir el estado que ya tiene no falla ni vuelve a guardar")
    void cambiarAlMismoEstadoEsIdempotente() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        UUID id = carrera.getId();
        when(raceRepository.findById(id)).thenReturn(Optional.of(carrera));

        RaceResponse respuesta = service.changeStatus(id, RaceStatus.OPEN_FOR_REGISTRATION);

        assertThat(respuesta.status()).isEqualTo(RaceStatus.OPEN_FOR_REGISTRATION);
        verify(raceRepository, never()).save(any(Race.class));
    }
}
