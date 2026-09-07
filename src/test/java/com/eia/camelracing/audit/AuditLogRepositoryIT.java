package com.eia.camelracing.audit;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.entity.AuditLog;
import com.eia.camelracing.audit.repository.IAuditLogRepository;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La consulta de la bitacora, con sus seis filtros combinables.
 *
 * ESTE ES EL TEST QUE NACIO DE UN ERROR REAL
 * La primera version de la consulta usaba el patron ":parametro IS NULL OR ..."
 * para las dos fechas, igual que para el resto de los filtros. Contra Postgres eso
 * falla con "could not determine data type of parameter $9" apenas se pide la
 * bitacora sin filtrar por fecha, que es el caso normal.
 *
 * El motivo es que Hibernate emite cada aparicion de un parametro como un
 * placeholder distinto, asi que el de la condicion "IS NULL" queda solo, sin nada
 * de donde deducir su tipo. Con un String o un UUID el driver igual manda el tipo
 * y Postgres se arregla; con un LocalDateTime en null manda "sin especificar" y el
 * motor se planta. La solucion fue COALESCE, y esta explicada en el repositorio.
 *
 * Nada de eso lo puede detectar un mock ni el compilador: es un desacuerdo entre
 * JPQL y el motor, y solo aparece ejecutando la consulta contra Postgres. Por eso
 * el primer test de esta clase es justamente el caso sin filtros.
 */
@Tag(Capas.INTEGRACION)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("IAuditLogRepository contra Postgres (integracion)")
class AuditLogRepositoryIT extends PostgresTestBase {

    @Autowired
    private IAuditLogRepository repository;

    @Autowired
    private TestEntityManager em;

    private UUID idDeLaCarrera;

    @BeforeEach
    void cargarBitacora() {
        idDeLaCarrera = UUID.randomUUID();

        em.persistAndFlush(linea("admin", AuditAction.LOGIN, "User", null,
                LocalDateTime.now().minusDays(10)));
        em.persistAndFlush(linea("organizer", AuditAction.RACE_CREATED, "Race", idDeLaCarrera,
                LocalDateTime.now().minusDays(5)));
        em.persistAndFlush(linea("organizer", AuditAction.RACE_CANCELLED, "Race", idDeLaCarrera,
                LocalDateTime.now().minusDays(1)));
    }

    /**
     * El caso que rompia: los seis filtros en null.
     *
     * Es ademas el que mas se usa, porque es lo que carga la pantalla de auditoria
     * al abrirse.
     */
    @Test
    @DisplayName("devuelve todo cuando no se filtra por nada")
    void sinFiltrosDevuelveTodo() {
        assertThat(repository.buscar(null, null, null, null, null, null, PageRequest.of(0, 10)))
                .hasSize(3);
    }

    /**
     * El rango de fechas, que es la mitad interesante del COALESCE.
     *
     * Se prueban las tres formas de usarlo: solo desde, solo hasta, y las dos. La
     * del medio es la importante, porque deja UN parametro en null y el otro con
     * valor, que es exactamente la combinacion que hacia fallar a la version
     * anterior de la consulta.
     */
    @Test
    @DisplayName("filtra por rango de fechas, con uno solo de los dos extremos o con los dos")
    void filtraPorRangoDeFechas() {
        LocalDateTime haceTresDias = LocalDateTime.now().minusDays(3);
        LocalDateTime haceSieteDias = LocalDateTime.now().minusDays(7);

        // Solo "desde": queda la cancelacion de hace un dia.
        assertThat(repository.buscar(null, null, null, null, haceTresDias, null,
                PageRequest.of(0, 10)))
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.RACE_CANCELLED);

        // Solo "hasta": queda el login de hace diez dias.
        assertThat(repository.buscar(null, null, null, null, null, haceSieteDias,
                PageRequest.of(0, 10)))
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.LOGIN);

        // Las dos puntas: queda la creacion de la carrera, de hace cinco dias.
        assertThat(repository.buscar(null, null, null, null, haceSieteDias, haceTresDias,
                PageRequest.of(0, 10)))
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.RACE_CREATED);
    }

    /**
     * El usuario se busca sin distinguir mayusculas.
     *
     * El parametro llega ya en minusculas desde el servicio y la consulta baja la
     * columna, asi que buscar "Admin" tiene que encontrar lo que hizo "admin". Si no
     * fuera asi, quien investiga un caso tendria que adivinar como escribio su
     * nombre el usuario al registrarse en Keycloak.
     */
    @Test
    @DisplayName("busca por usuario sin distinguir mayusculas")
    void filtraPorUsuario() {
        assertThat(repository.buscar("organizer", null, null, null, null, null,
                PageRequest.of(0, 10)))
                .hasSize(2);
    }

    /**
     * Todo lo que le paso a una entidad concreta.
     *
     * Es la consulta con la que se investiga un caso puntual: "que le paso a esta
     * carrera". Y es la que justifica el indice compuesto (entity_type, entity_id)
     * de la migracion V3.
     */
    @Test
    @DisplayName("trae la historia completa de una entidad")
    void filtraPorEntidad() {
        assertThat(repository.buscar(null, null, "race", idDeLaCarrera, null, null,
                PageRequest.of(0, 10)))
                .extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.RACE_CREATED, AuditAction.RACE_CANCELLED);
    }

    private AuditLog linea(String usuario, AuditAction accion, String tipo, UUID entidad,
                           LocalDateTime cuando) {
        return AuditLog.builder()
                .username(usuario)
                .action(accion)
                .entityType(tipo)
                .entityId(entidad)
                .occurredAt(cuando)
                .description("Linea de prueba")
                .build();
    }
}
