package com.eia.camelracing.registration;

import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.common.security.CurrentUser;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.repository.IRaceRepository;
import com.eia.camelracing.registration.dto.RegistrationRequest;
import com.eia.camelracing.registration.dto.RegistrationResponse;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.repository.IRaceRegistrationRepository;
import com.eia.camelracing.registration.service.RegistrationService;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import com.eia.camelracing.team.repository.ITeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las cuatro reglas que deciden si alguien puede anotarse en una carrera.
 *
 * Las cuatro estan nombradas en el enunciado, y las cuatro tienen la misma forma:
 * el pedido llega bien escrito, pasa la validacion del DTO, y aun asi hay que
 * rechazarlo porque el ESTADO del sistema no lo permite. Por eso devuelven 409 y
 * no 400: el problema no es lo que se mando, es cuando se mando.
 */
@Tag(Capas.UNIT)
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationService (unitario)")
class RegistrationServiceTest {

    @Mock
    private IRaceRegistrationRepository registrationRepository;
    @Mock
    private IRaceRepository raceRepository;
    @Mock
    private ICompetitorRepository competitorRepository;
    @Mock
    private ITeamRepository teamRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RegistrationService service;

    /**
     * Enunciado: "Register an active competitor successfully."
     *
     * Lo que se verifica ademas del "no explota" es que la inscripcion quede
     * PENDING y SIN carril. Las dos cosas son la regla: anotarse es pedir un lugar,
     * no tenerlo, y el carril se asigna recien al aprobar. Si naciera APPROVED, la
     * pantalla de validacion del organizador no tendria nada que hacer.
     */
    @Test
    @DisplayName("inscribe a un competidor activo y lo deja PENDING, sin carril")
    void inscribeUnCompetidorActivo() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        Competitor competidor = Datos.competidor("martillo");

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));
        when(registrationRepository.countByRaceIdAndStatusIn(eq(carrera.getId()), anyList()))
                .thenReturn(0L);
        when(competitorRepository.findById(competidor.getId())).thenReturn(Optional.of(competidor));
        when(registrationRepository.existsByRaceIdAndCompetitorIdAndStatusIn(
                eq(carrera.getId()), eq(competidor.getId()), anyList())).thenReturn(false);
        when(registrationRepository.competidorYaCorreEnUnEquipo(
                eq(carrera.getId()), eq(competidor.getId()), anyList())).thenReturn(false);
        when(currentUser.username()).thenReturn("organizer");
        when(registrationRepository.save(any(RaceRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RegistrationResponse inscripcion = service.register(carrera.getId(),
                new RegistrationRequest(competidor.getId(), null, null));

        assertThat(inscripcion.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(inscripcion.lane()).isNull();
        assertThat(inscripcion.competitorNickname()).isEqualTo("martillo");
        assertThat(inscripcion.registeredBy()).isEqualTo("organizer");
    }

    /**
     * Enunciado: "Reject a suspended competitor."
     *
     * El mensaje nombra el estado en el que esta, y el test lo comprueba: no es lo
     * mismo estar lesionado que suspendido, y quien recibe el error necesita saber
     * cual de las dos cosas tiene que resolver.
     */
    @Test
    @DisplayName("rechaza a un competidor suspendido")
    void rechazaCompetidorSuspendido() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        Competitor suspendido = Datos.competidor("cascoduro", CompetitorStatus.SUSPENDED);

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));
        when(registrationRepository.countByRaceIdAndStatusIn(eq(carrera.getId()), anyList()))
                .thenReturn(0L);
        when(competitorRepository.findById(suspendido.getId())).thenReturn(Optional.of(suspendido));

        assertThatThrownBy(() -> service.register(carrera.getId(),
                new RegistrationRequest(suspendido.getId(), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SUSPENDED");

        verify(registrationRepository, never()).save(any(RaceRegistration.class));
    }

    /**
     * Enunciado: "Reject a duplicated registration."
     *
     * Sin esta regla, el mismo competidor podria ocupar dos carriles de la misma
     * carrera y aparecer dos veces en la clasificacion.
     */
    @Test
    @DisplayName("rechaza una segunda inscripcion del mismo competidor")
    void rechazaInscripcionRepetida() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        Competitor competidor = Datos.competidor("martillo");

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));
        when(registrationRepository.countByRaceIdAndStatusIn(eq(carrera.getId()), anyList()))
                .thenReturn(0L);
        when(competitorRepository.findById(competidor.getId())).thenReturn(Optional.of(competidor));
        when(registrationRepository.existsByRaceIdAndCompetitorIdAndStatusIn(
                eq(carrera.getId()), eq(competidor.getId()), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.register(carrera.getId(),
                new RegistrationRequest(competidor.getId(), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya esta inscripto");

        verify(registrationRepository, never()).save(any(RaceRegistration.class));
    }

    /**
     * Enunciado: "Reject registration after the deadline."
     *
     * La carrera sigue OPEN_FOR_REGISTRATION: lo que vencio es el plazo. Son dos
     * cosas distintas y el test las separa a proposito, porque el error tipico es
     * confiar solo en el estado y dejar que alguien se anote la noche anterior a una
     * carrera cuyo cierre paso hace una semana pero que nadie se ocupo de cerrar.
     */
    @Test
    @DisplayName("rechaza una inscripcion cuando el plazo ya vencio")
    void rechazaInscripcionFueraDePlazo() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        carrera.setRegistrationDeadline(LocalDateTime.now().minusDays(1));
        Competitor competidor = Datos.competidor("martillo");

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));

        assertThatThrownBy(() -> service.register(carrera.getId(),
                new RegistrationRequest(competidor.getId(), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("plazo");

        verify(registrationRepository, never()).save(any(RaceRegistration.class));
    }

    /**
     * El cupo tambien es una regla, y se comprueba ANTES de mirar al participante.
     *
     * El orden importa para el mensaje: si se mirara primero al competidor, alguien
     * que intenta anotarse en una carrera llena recibiria un error sobre su estado
     * en vez de sobre el cupo.
     */
    @Test
    @DisplayName("rechaza una inscripcion cuando la carrera esta llena")
    void rechazaInscripcionSinCupo() {
        Race carrera = Datos.carrera(RaceStatus.OPEN_FOR_REGISTRATION);
        Competitor competidor = Datos.competidor("martillo");

        when(raceRepository.findById(carrera.getId())).thenReturn(Optional.of(carrera));
        when(registrationRepository.countByRaceIdAndStatusIn(eq(carrera.getId()), anyList()))
                .thenReturn((long) carrera.getMaxParticipants());

        assertThatThrownBy(() -> service.register(carrera.getId(),
                new RegistrationRequest(competidor.getId(), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cupo completo");

        verify(competitorRepository, never()).findById(any());
    }
}
