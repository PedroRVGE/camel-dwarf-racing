package com.eia.camelracing.support;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * El sistema completo arriba: la aplicacion, Postgres y un Keycloak de verdad.
 *
 * QUE APORTA ESTA CAPA QUE NO APORTAN LAS OTRAS DOS
 * SeguridadIT ya prueba las reglas de acceso, pero lo hace inyectando la
 * autenticacion ya armada con el post-procesador jwt() de spring-security-test: ahi
 * nadie firma ni verifica nada. Toda la cadena que va desde "el usuario escribio su
 * contrasena" hasta "la aplicacion sabe que tiene el rol organizer" queda sin
 * probar, y es justamente la que mas partes tiene:
 *
 *   1. Keycloak valida la contrasena y emite un token FIRMADO.
 *   2. La aplicacion baja la clave publica del emisor y verifica la firma.
 *   3. Compara el campo "iss" del token contra el emisor que tiene configurado.
 *   4. KeycloakRolesConverter saca los roles de realm_access.roles y les pone el
 *      prefijo ROLE_ que Spring Security espera.
 *
 * Cualquiera de esos cuatro pasos puede estar mal y la aplicacion igual arranca: el
 * sintoma aparece recien cuando todo devuelve 401 o 403 sin explicacion. Estos
 * tests son los que lo detectan antes de la demostracion.
 *
 * EL REALM ES EL MISMO ARCHIVO QUE USA DOCKER COMPOSE
 * No hay una copia para tests. build.gradle suma la carpeta keycloak/ al classpath
 * de las pruebas justamente para eso: si el realm cambia (un cliente nuevo, un rol
 * distinto), estos tests corren contra el cambio. Con una copia aparte, terminarian
 * validando una configuracion que ya no es la que se usa en serio.
 *
 * SON LOS TESTS MAS LENTOS DEL PROYECTO
 * Levantar Keycloak son varias decenas de segundos. Por eso hay pocos y cubren
 * recorridos completos en vez de casos sueltos: lo que se prueba aca es que las
 * piezas encajen, no cada regla por separado.
 *
 * TIENEN SU PROPIA BASE, SEPARADA DE LA DE INTEGRACION
 * Esta clase NO hereda de PostgresTestBase, y no es un descuido. Los tests de
 * integracion corren dentro de una transaccion que se deshace al terminar, asi que
 * no dejan rastro. Estos no pueden: una llamada HTTP de verdad no se puede
 * deshacer, y lo que crean queda en la base.
 *
 * Compartiendo contenedor, los competidores que este test da de alta aparecian en
 * la tabla de posiciones que cuenta StandingsRepositoryIT, y ese test fallaba: no
 * por su culpa ni por la de este, sino por la mezcla. Y fallaba SOLO al correr la
 * suite completa, que es la peor forma de fallar, porque cada clase por separado
 * pasaba y el error parecia intermitente.
 *
 * Dos contenedores cuestan unos segundos mas de arranque y eliminan la clase entera
 * de problema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class E2ETestBase {

    protected static final String REALM = "camelracing";

    /** La base de datos de esta capa, distinta de la de las pruebas de integracion. */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("camelracing_e2e")
                    .withUsername("camelracing")
                    .withPassword("camelracing");

    /**
     * EL PLAZO DE ARRANQUE NO ES UN NUMERO AL AZAR
     * Testcontainers espera 60 segundos por defecto a que un contenedor este listo, y
     * Keycloak 26 en modo desarrollo tarda mas que eso: solo la preparacion de
     * Quarkus se lleva unos 40 segundos, y despues todavia falta inicializar la base
     * interna y el realm. En esta maquina el arranque completo dio 66 segundos.
     *
     * Con el plazo por defecto el test falla con un mensaje que apunta al lugar
     * equivocado ("Timed out waiting for URL to be accessible .../health/ready"), que
     * parece un problema del endpoint de salud cuando en realidad Keycloak todavia
     * estaba levantando. Cinco minutos es holgado a proposito: una maquina mas lenta
     * o una imagen recien bajada tardan mas.
     */
    protected static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
                    .withRealmImportFile("realm-camelracing.json")
                    .withStartupTimeout(Duration.ofMinutes(5));

    static {
        POSTGRES.start();
        KEYCLOAK.start();
    }

    /**
     * Le dice a la aplicacion contra que emisor validar los tokens.
     *
     * Con issuer-uri alcanza: de ahi la aplicacion descubre sola donde estan las
     * claves publicas, leyendo el documento de configuracion que todo servidor
     * OpenID Connect publica. En compose.yml hacen falta las dos direcciones por
     * separado porque adentro de la red de Docker el navegador y la aplicacion ven a
     * Keycloak en direcciones distintas; aca las dos son la misma.
     */
    @DynamicPropertySource
    static void configurarElStack(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getIssuerUrl(REALM).toString());
    }

    @LocalServerPort
    protected int puerto;

    /**
     * El cliente HTTP con el que estos tests le pegan a la aplicacion.
     *
     * ESTA ARMADO A MANO, Y HAY DOS RAZONES
     *
     * 1) LA FABRICA DE PEDIDOS TIENE QUE SOPORTAR PATCH. La fabrica que RestTemplate
     *    elige por defecto en algunos entornos se apoya en HttpURLConnection, que NO
     *    conoce el verbo PATCH y corta con "Invalid HTTP method: PATCH". Este proyecto
     *    usa PATCH para todos los cambios de estado (abrir la inscripcion, largar,
     *    aprobar), asi que sin esto la mitad del recorrido no se puede probar.
     *    JdkClientHttpRequestFactory usa el cliente HTTP del JDK, que si lo soporta.
     *
     * 2) LOS ERRORES SON EL OBJETO DE LA PRUEBA, NO UN ACCIDENTE. Por defecto
     *    RestTemplate lanza una excepcion ante cualquier 4xx o 5xx, y aca justamente
     *    se quiere COMPROBAR que llega un 401, un 403 o un 409. Con el manejador
     *    puesto en "esto nunca es un error", la respuesta vuelve entera y el test
     *    puede mirarle el codigo y el cuerpo.
     */
    protected final RestTemplate http = clienteQueNoSeQueja();

    private static RestTemplate clienteQueNoSeQueja() {
        RestTemplate cliente = new RestTemplate(new JdkClientHttpRequestFactory());
        cliente.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse respuesta) {
                return false;
            }
        });
        return cliente;
    }

    protected String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    /**
     * Pide un token a Keycloak como lo haria la pantalla de login.
     *
     * Usa el flujo "password", que le pasa usuario y contrasena directamente al
     * servidor. En una aplicacion de verdad no se usa (el navegador va a la pantalla
     * de Keycloak y vuelve con un codigo), pero para un test es lo unico practico:
     * lo otro requeriria manejar un navegador.
     *
     * El cliente es camelracing-swagger, el mismo con el que se prueba la API a
     * mano desde la interfaz de Swagger.
     */
    protected String tokenDe(String usuario, String clave) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", "camelracing-swagger");
        formulario.add("username", usuario);
        formulario.add("password", clave);
        formulario.add("grant_type", "password");

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respuesta = http.postForEntity(
                KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token",
                new HttpEntity<>(formulario, cabeceras),
                Map.class);

        Object token = respuesta.getBody() == null ? null : respuesta.getBody().get("access_token");
        if (token == null) {
            throw new IllegalStateException(
                    "Keycloak no dio un token para '" + usuario + "': " + respuesta);
        }
        return token.toString();
    }

    /** Las cabeceras de un pedido autenticado con este token. */
    protected HttpHeaders autenticado(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    /** Un cuerpo JSON con su token, listo para mandar. */
    protected HttpEntity<String> pedido(String token, String cuerpoJson) {
        return new HttpEntity<>(cuerpoJson, autenticado(token));
    }

    /** Un pedido sin cuerpo (GET, PATCH sin datos, DELETE) con su token. */
    protected HttpEntity<Void> pedido(String token) {
        return new HttpEntity<>(autenticado(token));
    }
}
