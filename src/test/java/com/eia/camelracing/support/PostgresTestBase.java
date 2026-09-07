package com.eia.camelracing.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El Postgres de verdad contra el que corren las pruebas de integracion.
 *
 * POR QUE POSTGRES Y NO UNA BASE EN MEMORIA
 * El enunciado permite H2 para pruebas, y aca no se usa. Las consultas de este
 * proyecto no son "select * from tabla": la de la tabla de posiciones tiene
 * GROUP BY con SUM y CASE, la de la bitacora usa COALESCE para esquivar un
 * problema de tipos que es especifico de Postgres, y las migraciones de Flyway
 * usan sintaxis que H2 no entiende.
 *
 * Con H2 pasarian dos cosas, las dos malas: consultas que H2 acepta y Postgres
 * rechaza pasarian los tests y fallarian en produccion, y las migraciones ni
 * siquiera se podrian aplicar, con lo cual habria que mantener un segundo esquema
 * solo para los tests. Un segundo esquema es un esquema que se desactualiza.
 *
 * La version es la misma que la de compose.yml (postgres:15-alpine) a proposito:
 * probar contra una version distinta de la que corre en serio es probar otra cosa.
 *
 * EL CONTENEDOR SE LEVANTA UNA SOLA VEZ PARA TODAS LAS CLASES
 * Es lo que se conoce como "singleton container", y por eso no se usa la anotacion
 * @Testcontainers: esa extension apaga los contenedores estaticos al terminar cada
 * clase de test, con lo cual una suite de seis clases levantaria y tiraria Postgres
 * seis veces. Arrancandolo en un bloque static, arranca la primera vez que alguna
 * clase de prueba carga esta, y queda vivo para las demas.
 *
 * Nadie lo apaga a mano, y no hace falta: Testcontainers deja corriendo un
 * contenedor auxiliar (Ryuk) que borra todo lo que se haya creado cuando la JVM de
 * los tests termina, aunque termine mal.
 *
 * Las clases que heredan de aca no comparten datos entre si: cada test de
 * integracion corre dentro de una transaccion que se deshace al terminar. Lo que
 * se comparte es el contenedor, no el contenido.
 */
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("camelracing_test")
                    .withUsername("camelracing")
                    .withPassword("camelracing");

    static {
        POSTGRES.start();
    }

    /**
     * Le pasa a Spring la direccion del contenedor.
     *
     * Tiene que ser dinamico: Testcontainers publica el puerto de Postgres en un
     * puerto libre al azar de la maquina, justamente para que dos suites que corran
     * a la vez no se pisen. Ese numero no se conoce hasta que el contenedor arranco,
     * asi que no puede estar escrito en un application.yml.
     */
    @DynamicPropertySource
    static void configurarLaBase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
