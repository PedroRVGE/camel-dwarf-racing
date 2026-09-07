package com.eia.camelracing.result;

import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.result.dto.ResultResponse;
import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.result.repository.IRaceResultRepository;
import com.eia.camelracing.result.service.ResultService;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import com.eia.camelracing.team.repository.ITeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La carga de resultados: la operacion mas delicada del sistema.
 *
 * Es delicada porque es la unica que escribe en dos lugares a la vez: guarda el
 * resultado Y rehace las estadisticas del participante. Un error aca no se nota al
 * momento, se nota semanas despues cuando la tabla de posiciones no cierra.
 */
@Tag(Capas.UNIT)
@ExtendWith(MockitoExtension.class)
@DisplayName("ResultService (unitario)")
class ResultServiceTest {

    @Mock
    private IRaceResultRepository resultRepository;
    @Mock
    private IRaceRepository raceRepository;
    @Mock
    private IRaceRegistrationRepository registrationRepository;
    @Mock
    private ICompetitorRepository competitorRepository;
    @Mock
    private ITeamRepository teamRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ResultService service;

    /**
     * Enunciado: "Record a valid result."
     *
     * El test no se conforma con el 201: verifica que las estadisticas del
     * competidor hayan quedado recalculadas. Es lo que el enunciado pide cuando dice
     * que actualizar un resultado tiene que actualizar las estadisticas de forma
     * consistente, y es la mitad de la operacion que se olvida.
     */
    @Test
    @DisplayName("carga un resultado valido y recalcula las estadisticas del competidor")
    void cargaUnResultadoValido() {
        Race carrera = Datos.carrera(RaceStatus.IN_PROGRESS);
        Competitor competidor = Datos.competidor("zafira");
        RaceRegistration inscripcion =
                Datos.inscripcion(carrera, competidor, RegistrationStatus.APPROVED);
        UUID carreraId = carrera.getId();

        when(raceRepository.findById(carreraId)).thenReturn(Optional.of(carrera));
        when(registrationRepository.findById(inscripcion.getId()))
                .thenReturn(Optional.of(inscripcion));
        when(resultRepository.existsByRegistrationId(inscripcion.getId())).thenReturn(false);
        when(resultRepository.posicionOcupada(carreraId, 1, null)).thenReturn(false);
        when(resultRepository.findByRaceIdAndStatus(carreraId, ResultStatus.FINISHED))
                .thenReturn(List.of());
        when(currentUser.username()).thenReturn("organizer");
        when(resultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        // Lo que la base devolveria despues de guardar: la victoria recien cargada.
        when(resultRepository.resultadosDeCompetidor(competidor.getId()))
                .thenReturn(List.of(Datos.resultado(inscripcion, 1, 498)));

        ResultResponse resultado = service.record(carreraId,
                Datos.pedidoDeResultado(inscripcion.getId(), 1, 498));

        assertThat(resultado.status()).isEqualTo(ResultStatus.FINISHED);
        assertThat(resultado.finalPosition()).isEqualTo(1);
        assertThat(resultado.points()).isEqualTo(10);
        assertThat(resultado.recordedBy()).isEqualTo("organizer");

        ArgumentCaptor<Competitor> guardado = ArgumentCaptor.forClass(Competitor.class);
        verify(competitorRepository).save(guardado.capture());
        assertThat(guardado.getValue().getVictories()).isEqualTo(1);
        assertThat(guardado.getValue().getCompletedRaces()).isEqualTo(1);
        assertThat(guardado.getValue().getDefeats()).isZero();
    }

    /**
     * Enunciado: "Reject two winners in one race."
     *
     * La regla esta escrita DOS veces a proposito: aca, que es la que da el mensaje
     * entendible, y como constraint unica (race_id, final_position) en la migracion
     * V3, que es la que hace que la regla no se pueda violar ni escribiendo directo
     * en la base. Este test cubre la primera; RaceResultRepositoryIT cubre la
     * segunda.
     */
    @Test
    @DisplayName("no deja cargar un segundo ganador")
    void noPuedeHaberDosGanadores() {
        Race carrera = Datos.carrera(RaceStatus.IN_PROGRESS);
        Competitor competidor = Datos.competidor("ramses");
        RaceRegistration inscripcion =
                Datos.inscripcion(carrera, competidor, RegistrationStatus.APPROVED);
        UUID carreraId = carrera.getId();

        when(raceRepository.findById(carreraId)).thenReturn(Optional.of(carrera));
        when(registrationRepository.findById(inscripcion.getId()))
                .thenReturn(Optional.of(inscripcion));
        when(resultRepository.existsByRegistrationId(inscripcion.getId())).thenReturn(false);
        when(resultRepository.posicionOcupada(carreraId, 1, null)).thenReturn(true);

        assertThatThrownBy(() -> service.record(carreraId,
                Datos.pedidoDeResultado(inscripcion.getId(), 1, 500)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dos ganadores");

        verify(resultRepository, never()).save(any(RaceResult.class));
    }

    /**
     * Solo los participantes aprobados pueden tener resultado.
     *
     * Un pendiente o un rechazado nunca estuvo en la grilla de largada: darle un
     * puesto seria inventar que corrio.
     */
    @Test
    @DisplayName("no deja cargar el resultado de un participante que no fue aprobado")
    void soloLosAprobadosTienenResultado() {
        Race carrera = Datos.carrera(RaceStatus.IN_PROGRESS);
        RaceRegistration pendiente = Datos.inscripcion(carrera, Datos.competidor("yunque"),
                RegistrationStatus.PENDING);

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));
        when(registrationRepository.findById(pendiente.getId()))
                .thenReturn(Optional.of(pendiente));

        assertThatThrownBy(() -> service.record(carrera.getId(),
                Datos.pedidoDeResultado(pendiente.getId(), 1, 500)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDING");

        verify(resultRepository, never()).save(any(RaceResult.class));
    }

    /**
     * El segundo no puede haber sido mas rapido que el primero.
     *
     * Es el absurdo que el enunciado nombra como "impossible values", y el que mas
     * facil se cuela: los dos resultados son validos por separado, y solo comparados
     * entre si se ve que no pueden convivir. Por eso ninguna constraint de base lo
     * puede atajar y tiene que estar en el servicio.
     */
    @Test
    @DisplayName("no deja que un puesto peor tenga mejor tiempo que uno anterior")
    void losTiemposTienenQueRespetarLosPuestos() {
        Race carrera = Datos.carrera(RaceStatus.IN_PROGRESS);
        Competitor ganador = Datos.competidor("zafira");
        Competitor segundo = Datos.competidor("ramses");
        RaceRegistration delGanador =
                Datos.inscripcion(carrera, ganador, RegistrationStatus.APPROVED);
        RaceRegistration delSegundo =
                Datos.inscripcion(carrera, segundo, RegistrationStatus.APPROVED);
        UUID carreraId = carrera.getId();

        when(raceRepository.findById(carreraId)).thenReturn(Optional.of(carrera));
        when(registrationRepository.findById(delSegundo.getId()))
                .thenReturn(Optional.of(delSegundo));
        when(resultRepository.existsByRegistrationId(delSegundo.getId())).thenReturn(false);
        when(resultRepository.posicionOcupada(carreraId, 2, null)).thenReturn(false);
        // El puesto 1 ya esta cargado con 500 segundos.
        when(resultRepository.findByRaceIdAndStatus(carreraId, ResultStatus.FINISHED))
                .thenReturn(List.of(Datos.resultado(delGanador, 1, 500)));

        // Y ahora entra un segundo puesto con 400: mas rapido que el ganador.
        assertThatThrownBy(() -> service.record(carreraId,
                Datos.pedidoDeResultado(delSegundo.getId(), 2, 400)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no es peor");

        verify(resultRepository, never()).save(any(RaceResult.class));
    }

    /**
     * Los resultados solo se cargan con la carrera en curso.
     *
     * Antes de que largue no hay nada que medir; despues de terminada, sumar un
     * participante nuevo cambiaria una clasificacion que ya es oficial.
     */
    @Test
    @DisplayName("no deja cargar resultados de una carrera que todavia no largo")
    void soloSeCarganResultadosDeCarrerasEnCurso() {
        Race abierta = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        when(raceRepository.findById(abierta.getId())).thenReturn(Optional.of(abierta));

        assertThatThrownBy(() -> service.record(abierta.getId(),
                Datos.pedidoDeResultado(UUID.randomUUID(), 1, 500)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("IN_PROGRESS");

        verify(registrationRepository, never()).findById(any());
        verify(resultRepository, never()).save(any(RaceResult.class));
        verify(competitorRepository, never()).save(any(Competitor.class));
    }
}
