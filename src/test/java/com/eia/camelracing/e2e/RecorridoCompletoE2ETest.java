package com.eia.camelracing.e2e;

import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.E2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El recorrido completo de una carrera, por HTTP y con tokens firmados de verdad.
 *
 * POR QUE UN SOLO TEST LARGO Y NO DIEZ CORTOS
 * Porque lo que se prueba aca no es cada regla por separado (de eso se ocupan las
 * otras dos capas) sino que las piezas ENCAJEN: que el id que devuelve el alta de
 * un competidor sirva para inscribirlo, que la inscripcion aprobada sirva para
 * cargarle un resultado, y que ese resultado termine sumando puntos en la tabla de
 * posiciones. Un error de encastre no se ve probando cada paso aislado.
 *
 * Ademas, cada paso usa el ROL que corresponde: el alta de competidores es del
 * administrador, la carrera y los resultados son del organizador, y la tabla de
 * posiciones la lee el viewer. Si la configuracion de roles estuviera mal, el
 * recorrido se corta en el paso equivocado y el mensaje dice cual.
 *
 * NO ES TRANSACCIONAL, Y ES A PROPOSITO
 * Los datos que crea quedan en la base: no hay forma de deshacer una llamada HTTP
 * de verdad. Por eso los apodos y los nombres llevan un sufijo unico, para que dos
 * corridas seguidas no choquen contra las constraints de unicidad.
 */
@Tag(Capas.E2E)
@DisplayName("El recorrido completo de una carrera (e2e)")
class RecorridoCompletoE2ETest extends E2ETestBase {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * De la nada a la tabla de posiciones, en once llamadas.
     *
     * Es el guion de la demostracion del proyecto, escrito como test.
     */
    @Test
    @DisplayName("crea, inscribe, larga, carga resultados y suma puntos")
    void elRecorridoCompleto() {
        String admin = tokenDe("admin", "admin123");
        String organizer = tokenDe("organizer", "organizer123");
        String viewer = tokenDe("viewer", "viewer123");

        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        // 1. El administrador da de alta a los dos competidores.
        String zafira = crearCompetidor(admin, "zafira-" + sufijo);
        String ramses = crearCompetidor(admin, "ramses-" + sufijo);

        // 2. El organizador crea la carrera. Nace en DRAFT: todavia no esta publicada.
        String carrera = crearCarrera(organizer, "Gran Premio E2E " + sufijo);
        assertThat(estadoDeLaCarrera(viewer, carrera)).isEqualTo("DRAFT");

        // 3. La abre a inscripcion.
        cambiarEstado(organizer, carrera, "OPEN_FOR_REGISTRATION");

        // 4. Inscribe a los dos. Quedan PENDING, sin carril.
        String deZafira = inscribir(organizer, carrera, zafira);
        String deRamses = inscribir(organizer, carrera, ramses);

        // 5. Los aprueba. Recien ahi reciben carril.
        Map<String, Object> aprobada = aprobar(organizer, deZafira);
        assertThat(aprobada.get("status")).isEqualTo("APPROVED");
        assertThat(aprobada.get("lane")).isEqualTo(1);
        aprobar(organizer, deRamses);

        // 6. Cierra inscripciones y larga.
        cambiarEstado(organizer, carrera, "CLOSED_FOR_REGISTRATION");
        cambiarEstado(organizer, carrera, "IN_PROGRESS");

        // 7. Carga el primer resultado y comprueba el puntaje del ganador.
        Map<String, Object> ganador = cargarResultado(organizer, carrera, deZafira, 1, 498);
        assertThat(ganador.get("points")).isEqualTo(10);

        // 8. Con un solo resultado, la carrera NO se puede dar por terminada.
        ResponseEntity<Map> aMedias = http.exchange(url("/api/races/" + carrera + "/status"),
                HttpMethod.PATCH, pedido(organizer, "{\"status\":\"COMPLETED\"}"), Map.class);
        assertThat(aMedias.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(aMedias.getBody().get("message").toString()).contains("faltan cargar 1");

        // 9. Con el segundo cargado, si.
        cargarResultado(organizer, carrera, deRamses, 2, 521);
        cambiarEstado(organizer, carrera, "COMPLETED");
        assertThat(estadoDeLaCarrera(viewer, carrera)).isEqualTo("COMPLETED");

        // 10. Y el viewer ve los puntos en la tabla de posiciones.
        ResponseEntity<Map> tabla = http.exchange(
                url("/api/standings/competitors?size=100"), HttpMethod.GET,
                pedido(viewer), Map.class);
        assertThat(tabla.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filas = (List<Map<String, Object>>) tabla.getBody().get("content");

        assertThat(filas)
                .filteredOn(fila -> fila.get("participantName").equals("zafira-" + sufijo))
                .singleElement()
                .satisfies(fila -> {
                    assertThat(fila.get("points")).isEqualTo(10);
                    assertThat(fila.get("victories")).isEqualTo(1);
                });

        assertThat(filas)
                .filteredOn(fila -> fila.get("participantName").equals("ramses-" + sufijo))
                .singleElement()
                .satisfies(fila -> assertThat(fila.get("points")).isEqualTo(7));
    }

    /**
     * Los roles del token de verdad.
     *
     * Es el test que prueba KeycloakRolesConverter de punta a punta: el token del
     * viewer trae sus roles adentro de realm_access.roles, y de ahi tiene que salir
     * el ROLE_VIEWER contra el que compara SecurityConfig. Si el converter estuviera
     * mal, el viewer no podria ni leer, y aca se veria en el primer GET.
     */
    @Test
    @DisplayName("un viewer con token real lee pero no crea")
    void elViewerLeePeroNoCrea() {
        String viewer = tokenDe("viewer", "viewer123");

        ResponseEntity<Map> lectura = http.exchange(url("/api/competitors"), HttpMethod.GET,
                pedido(viewer), Map.class);
        assertThat(lectura.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> escritura = http.exchange(url("/api/races"), HttpMethod.POST,
                pedido(viewer, cuerpoDeCarrera("Carrera prohibida")), Map.class);
        assertThat(escritura.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Sin token no se entra, ni con uno inventado.
     *
     * El segundo caso es el que prueba que la firma se verifica de verdad: el texto
     * "Bearer no-soy-un-token" tiene la forma correcta y aun asi no pasa, porque la
     * aplicacion bajo la clave publica de Keycloak y no le cierra.
     */
    @Test
    @DisplayName("sin token, y con un token falso, devuelve 401")
    void sinTokenNoSePasa() {
        ResponseEntity<Map> sinNada = http.exchange(url("/api/competitors"), HttpMethod.GET,
                null, Map.class);
        assertThat(sinNada.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> inventado = http.exchange(url("/api/competitors"), HttpMethod.GET,
                pedido("no-soy-un-token"), Map.class);
        assertThat(inventado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ==================================================================
    // Los pasos del recorrido, uno por metodo
    // ==================================================================

    private String crearCompetidor(String token, String apodo) {
        String cuerpo = """
                {
                  "name": "Competidor %s",
                  "nickname": "%s",
                  "type": "CAMEL",
                  "dateOfBirth": "2017-05-08",
                  "weightKg": 612.00,
                  "heightCm": 218.50,
                  "countryOfOrigin": "Marruecos"
                }
                """.formatted(apodo, apodo);

        ResponseEntity<Map> respuesta = http.exchange(url("/api/competitors"), HttpMethod.POST,
                pedido(token, cuerpo), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody().get("id").toString();
    }

    private String cuerpoDeCarrera(String nombre) {
        String largada = LocalDateTime.now().plusDays(30).withNano(0).format(ISO);
        String cierre = LocalDateTime.now().plusDays(25).withNano(0).format(ISO);
        return """
                {
                  "name": "%s",
                  "description": "El recorrido completo, de punta a punta",
                  "scheduledAt": "%s",
                  "startLocation": "Portico de la EIA",
                  "finishLocation": "Alto de Las Palmas",
                  "distanceMeters": 1000,
                  "maxParticipants": 8,
                  "type": "MIXED",
                  "registrationDeadline": "%s"
                }
                """.formatted(nombre, largada, cierre);
    }

    private String crearCarrera(String token, String nombre) {
        ResponseEntity<Map> respuesta = http.exchange(url("/api/races"), HttpMethod.POST,
                pedido(token, cuerpoDeCarrera(nombre)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody().get("id").toString();
    }

    private void cambiarEstado(String token, String carrera, String estado) {
        ResponseEntity<Map> respuesta = http.exchange(url("/api/races/" + carrera + "/status"),
                HttpMethod.PATCH, pedido(token, "{\"status\":\"" + estado + "\"}"), Map.class);

        assertThat(respuesta.getStatusCode())
                .describedAs("no se pudo pasar la carrera a " + estado + ": " + respuesta.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    private String estadoDeLaCarrera(String token, String carrera) {
        ResponseEntity<Map> respuesta = http.exchange(url("/api/races/" + carrera), HttpMethod.GET,
                pedido(token), Map.class);
        return respuesta.getBody().get("status").toString();
    }

    private String inscribir(String token, String carrera, String competidor) {
        ResponseEntity<Map> respuesta = http.exchange(
                url("/api/races/" + carrera + "/registrations"), HttpMethod.POST,
                pedido(token, "{\"competitorId\":\"" + competidor + "\"}"), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().get("status")).isEqualTo("PENDING");
        assertThat(respuesta.getBody().get("lane")).isNull();
        return respuesta.getBody().get("id").toString();
    }

    private Map<String, Object> aprobar(String token, String inscripcion) {
        ResponseEntity<Map> respuesta = http.exchange(
                url("/api/registrations/" + inscripcion + "/approve"), HttpMethod.PATCH,
                pedido(token), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> cuerpo = respuesta.getBody();
        return cuerpo;
    }

    private Map<String, Object> cargarResultado(String token, String carrera, String inscripcion,
                                                int puesto, int segundos) {
        String cuerpo = """
                {
                  "registrationId": "%s",
                  "status": "FINISHED",
                  "completionTimeSeconds": %d,
                  "penaltyTimeSeconds": 0,
                  "finalPosition": %d
                }
                """.formatted(inscripcion, segundos, puesto);

        ResponseEntity<Map> respuesta = http.exchange(url("/api/races/" + carrera + "/results"),
                HttpMethod.POST, pedido(token, cuerpo), Map.class);

        assertThat(respuesta.getStatusCode())
                .describedAs("no se pudo cargar el resultado: " + respuesta.getBody())
                .isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = respuesta.getBody();
        return resultado;
    }
}
