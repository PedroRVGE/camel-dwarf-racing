package com.eia.camelracing.competitor;

import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.exception.BusinessRuleException;
import com.eia.camelracing.competitor.dto.CompetitorResponse;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.competitor.service.CompetitorService;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.Datos;
import com.eia.camelracing.team.service.TeamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas del alta y la baja de competidores, sin base de datos.
 *
 * QUE PRUEBA ESTA CLASE Y QUE NO
 * Prueba las DECISIONES del servicio: cuando deja pasar, cuando corta, y en que
 * estado deja las cosas. El repositorio esta reemplazado por un mock, asi que un
 * test verde aca NO significa que la consulta funcione contra Postgres: eso lo
 * cubre CompetitorRepositoryIT, en la capa de integracion.
 *
 * La division vale la pena porque estos tests corren en milisegundos y sin Docker,
 * asi que se pueden ejecutar cada vez que se guarda un archivo.
 */
@Tag(Capas.UNIT)
@ExtendWith(MockitoExtension.class)
@DisplayName("CompetitorService (unitario)")
class CompetitorServiceTest {

    @Mock
    private ICompetitorRepository competitorRepository;
    @Mock
    private TeamService teamService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CompetitorService service;

    /**
     * Enunciado: "Create a valid competitor."
     *
     * No alcanza con que no explote: se verifica que el competidor nazca ACTIVE y
     * con las estadisticas en cero. Ese estado inicial es una regla, no un detalle,
     * porque de el depende que se pueda inscribir en carreras desde el primer dia.
     */
    @Test
    @DisplayName("da de alta un competidor valido, ACTIVE y con las estadisticas en cero")
    void creaUnCompetidorValido() {
        when(competitorRepository.existsByNicknameIgnoreCase("martillo")).thenReturn(false);
        when(teamService.prepararIngreso(isNull(), any(Competitor.class))).thenReturn(null);
        when(competitorRepository.save(any(Competitor.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        CompetitorResponse creado = service.createCompetitor(Datos.pedidoDeCompetidor("martillo"));

        assertThat(creado.nickname()).isEqualTo("martillo");
        assertThat(creado.status()).isEqualTo(CompetitorStatus.ACTIVE);
        assertThat(creado.victories()).isZero();
        assertThat(creado.defeats()).isZero();
        assertThat(creado.completedRaces()).isZero();
        assertThat(creado.teamId()).isNull();
    }

    /**
     * Enunciado: "Reject a duplicated nickname."
     *
     * Se verifica ademas que NO se haya llamado a save. Sin eso, el test pasaria
     * igual con un servicio que primero guarda y despues valida, que es exactamente
     * el error que esta regla tiene que impedir.
     */
    @Test
    @DisplayName("rechaza un apodo que ya existe, y no guarda nada")
    void rechazaApodoRepetido() {
        when(competitorRepository.existsByNicknameIgnoreCase("martillo")).thenReturn(true);

        assertThatThrownBy(() -> service.createCompetitor(Datos.pedidoDeCompetidor("martillo")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("martillo");

        verify(competitorRepository, never()).save(any(Competitor.class));
    }

    /**
     * La baja de un competidor es logica, no fisica.
     *
     * Es la regla que el enunciado pide como "soft delete": el competidor pasa a
     * RETIRED y su historial de carreras sigue existiendo. Un DELETE de verdad se
     * llevaria puestos los resultados de las carreras que ya corrio, y la tabla de
     * posiciones de esas carreras dejaria de cerrar.
     */
    @Test
    @DisplayName("dar de baja a un competidor lo deja RETIRED, no lo borra")
    void laBajaEsLogica() {
        Competitor competidor = Datos.competidor("cascoduro");
        UUID id = competidor.getId();
        when(competitorRepository.findById(id)).thenReturn(Optional.of(competidor));

        service.deleteCompetitor(id);

        ArgumentCaptor<Competitor> guardado = ArgumentCaptor.forClass(Competitor.class);
        verify(competitorRepository).save(guardado.capture());
        assertThat(guardado.getValue().getStatus()).isEqualTo(CompetitorStatus.RETIRED);
        verify(competitorRepository, never()).delete(any(Competitor.class));
    }
}
