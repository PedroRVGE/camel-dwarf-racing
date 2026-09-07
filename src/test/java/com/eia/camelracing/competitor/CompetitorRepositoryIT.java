package com.eia.camelracing.competitor;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import com.eia.camelracing.competitor.repository.ICompetitorRepository;
import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static com.eia.camelracing.support.Datos.competidor;
import static com.eia.camelracing.support.Datos.sinId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que solo se puede probar con una base de datos de verdad.
 *
 * QUE HACE ACA QUE NO PODRIA HACER UN TEST UNITARIO
 * Un mock del repositorio devuelve lo que se le diga: nunca falla porque una
 * consulta este mal escrita ni porque falte una constraint. Estas tres pruebas
 * corren contra el esquema real, creado por las mismas migraciones de Flyway que
 * corren en produccion, sobre el mismo Postgres 15 de compose.yml.
 *
 * @DataJpaTest levanta solo la capa de persistencia (nada de controladores ni de
 * seguridad) y envuelve cada test en una transaccion que se deshace al terminar,
 * asi que los tests no se ensucian entre si aunque compartan el contenedor.
 *
 * replace = NONE es obligatorio: sin eso, @DataJpaTest intentaria cambiar la base
 * por una en memoria, que es exactamente lo que este proyecto no quiere.
 *
 * OJO CON LOS IMPORTS EN SPRING BOOT 4
 * Las anotaciones de test cambiaron de paquete al partirse spring-boot-autoconfigure
 * en un modulo por tecnologia. Cualquier ejemplo de Boot 3 que se copie de internet
 * no compila:
 *
 *   Boot 3  org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
 *   Boot 4  org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
 *
 *   Boot 3  org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
 *   Boot 4  org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
 *
 *   Boot 3  org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
 *   Boot 4  org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
 *
 * Es el mismo reacomodamiento que obligo a agregar spring-boot-flyway a mano en
 * build.gradle.
 */
@Tag(Capas.INTEGRACION)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ICompetitorRepository contra Postgres (integracion)")
class CompetitorRepositoryIT extends PostgresTestBase {

    @Autowired
    private ICompetitorRepository repository;

    @Autowired
    private TestEntityManager em;

    /**
     * La constraint unica del apodo existe de verdad en la tabla.
     *
     * CompetitorServiceTest ya prueba que el servicio rechaza un apodo repetido, y
     * eso NO alcanza: el chequeo del servicio consulta y despues inserta, y entre
     * esas dos operaciones puede colarse otro request. Los dos consultan, los dos
     * ven el apodo libre, los dos insertan. La base es el unico lugar donde "unico"
     * significa unico pase lo que pase, y este test es el que comprueba que la
     * migracion V1 realmente la creo.
     */
    @Test
    @DisplayName("la base rechaza dos competidores con el mismo apodo")
    void elApodoEsUnicoEnLaBase() {
        em.persistAndFlush(sinId(competidor("martillo")));

        Competitor repetido = sinId(competidor("martillo"));

        // Se comprueba el NOMBRE de la constraint, no solo que haya fallado. Asi el
        // test sigue valiendo si manana aparece otra restriccion sobre la tabla: lo
        // que se afirma es que fue esta y no otra la que corto.
        //
        // La excepcion es la de Hibernate y no la DataIntegrityViolationException de
        // Spring porque TestEntityManager trabaja contra el EntityManager pelado, sin
        // pasar por la traduccion de excepciones que hacen los repositorios. Por la
        // API, un apodo repetido si llega como 409: eso lo cubre el handler de
        // DataIntegrityViolationException en GlobalExceptionHandler.
        assertThatThrownBy(() -> em.persistAndFlush(repetido))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_competitors_nickname");
    }

    /**
     * La consulta de busqueda con TODOS los filtros en null.
     *
     * Es el caso que mas parece trivial y mas problemas dio: el patron
     * ":parametro IS NULL OR condicion" obliga a Postgres a deducir el tipo de un
     * parametro que aparece solo, sin nada al lado de donde sacarlo. Cuando no
     * puede, la consulta falla en tiempo de ejecucion con "could not determine data
     * type of parameter", y eso no lo ve ni el compilador ni un mock: solo aparece
     * pegandole a la base.
     *
     * Este es el listado sin filtros, que es lo primero que carga la pantalla de
     * competidores.
     */
    @Test
    @DisplayName("el listado sin ningun filtro devuelve todo")
    void buscarSinFiltrosDevuelveTodo() {
        em.persistAndFlush(sinId(competidor("martillo")));
        em.persistAndFlush(sinId(competidor("yunque")));
        em.persistAndFlush(sinId(competidor("zafira")));

        Page<Competitor> pagina = repository.buscar(null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    /**
     * Los filtros combinados, que es como los usa la pantalla de verdad.
     *
     * El patron del texto llega en minusculas y con los porcentajes ya puestos,
     * porque asi lo arma el servicio; la consulta solo baja a minusculas la columna.
     */
    @Test
    @DisplayName("filtra por tipo, por estado y por texto a la vez")
    void buscarCombinaLosFiltros() {
        Competitor camelloActivo = sinId(competidor("zafira"));
        camelloActivo.setType(CompetitorType.CAMEL);
        em.persistAndFlush(camelloActivo);

        Competitor camelloLesionado = sinId(competidor("ramses"));
        camelloLesionado.setType(CompetitorType.CAMEL);
        camelloLesionado.setStatus(CompetitorStatus.INJURED);
        em.persistAndFlush(camelloLesionado);

        em.persistAndFlush(sinId(competidor("martillo"))); // enano, ACTIVE

        assertThat(repository.buscar(CompetitorType.CAMEL, null, null, null, PageRequest.of(0, 10)))
                .hasSize(2);

        assertThat(repository.buscar(CompetitorType.CAMEL, CompetitorStatus.ACTIVE, null, null,
                PageRequest.of(0, 10)))
                .extracting(Competitor::getNickname)
                .containsExactly("zafira");

        // Por texto: busca en el nombre y en el apodo, sin distinguir mayusculas.
        assertThat(repository.buscar(null, null, null, "%ram%", PageRequest.of(0, 10)))
                .extracting(Competitor::getNickname)
                .containsExactly("ramses");
    }
}
