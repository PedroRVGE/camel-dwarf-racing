package com.eia.camelracing.common;

import com.eia.camelracing.support.Capas;
import com.eia.camelracing.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los cuatro casos de seguridad que pide el enunciado, sobre la API de verdad.
 *
 * QUE SE PRUEBA ACA Y QUE NO
 * Se prueba que las REGLAS de SecurityConfig sean las que se creen: que un viewer
 * no pueda crear carreras, que un admin si, que sin token no se pase, y que un id
 * inexistente devuelva 404 y no 500.
 *
 * NO se prueba la validacion del token en si (firma, emisor, expiracion): eso lo
 * hace Spring Security, y probarlo de verdad necesita un Keycloak emitiendo tokens
 * firmados. Esa parte esta en la capa e2e.
 *
 * POR QUE HAY UN JwtDecoder DE MENTIRA
 * La aplicacion es un resource server: al arrancar necesita un JwtDecoder, y el que
 * arma Spring Boot solo sale de la configuracion de Keycloak (issuer-uri o
 * jwk-set-uri), que en el perfil de test no existe. Sin este bean, el contexto ni
 * siquiera levanta.
 *
 * El decodificador falso rechaza cualquier token que le llegue, y esta bien que asi
 * sea: los tests de abajo no mandan tokens de verdad, usan el post-procesador jwt()
 * de spring-security-test, que inyecta la autenticacion ya armada y no pasa por el
 * decodificador. El unico que si lo toca es el test del token invalido, que espera
 * exactamente el 401 que este bean produce.
 */
@Tag(Capas.INTEGRACION)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SeguridadIT.SinKeycloak.class)
@DisplayName("Las reglas de acceso de la API (integracion)")
class SeguridadIT extends PostgresTestBase {

    @TestConfiguration
    static class SinKeycloak {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("En los tests no hay Keycloak que valide tokens");
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    /** El cuerpo de una carrera valida, para no repetirlo en cada test. */
    private String carreraValida() {
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        String largada = LocalDateTime.now().plusDays(30).withNano(0).format(iso);
        String cierre = LocalDateTime.now().plusDays(25).withNano(0).format(iso);
        return """
                {
                  "name": "Gran Premio EIA",
                  "description": "Una carrera para probar la seguridad",
                  "scheduledAt": "%s",
                  "startLocation": "Portico de la EIA",
                  "finishLocation": "Alto de Las Palmas",
                  "distanceMeters": 1000,
                  "maxParticipants": 8,
                  "type": "MIXED",
                  "registrationDeadline": "%s"
                }
                """.formatted(largada, cierre);
    }

    /**
     * Enunciado: "Return 401 without a valid token."
     *
     * 401 y no 403: son dos respuestas distintas y la diferencia importa. 401 es "no
     * se quien sos"; 403 es "se quien sos y no te alcanza". Un frontend que las
     * confunde manda al usuario a loguearse de nuevo cuando en realidad ya esta
     * logueado y lo que le falta es un rol.
     */
    @Test
    @DisplayName("sin token devuelve 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/races")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carreraValida()))
                .andExpect(status().isUnauthorized());
    }

    /** Un token que no se puede decodificar tampoco pasa, y da 401 igual que la ausencia. */
    @Test
    @DisplayName("con un token que no se puede validar devuelve 401")
    void conTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/competitors")
                        .header("Authorization", "Bearer esto-no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Enunciado: "Prevent a viewer from creating a race."
     *
     * El viewer esta autenticado y aun asi no puede: por eso 403 y no 401.
     */
    @Test
    @DisplayName("un viewer no puede crear carreras: 403")
    void elViewerNoPuedeCrearCarreras() throws Exception {
        mockMvc.perform(post("/api/races")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carreraValida()))
                .andExpect(status().isForbidden());
    }

    /**
     * Enunciado: "Allow an administrator to create a race."
     *
     * Es el contraejemplo del anterior, y hace falta: sin el, una regla que
     * prohibiera crear carreras a TODO el mundo tambien pasaria el test del viewer.
     */
    @Test
    @DisplayName("un administrador si puede crear carreras: 201")
    void elAdminSiPuedeCrearCarreras() throws Exception {
        mockMvc.perform(post("/api/races")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carreraValida()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    /** El organizador tambien: crear carreras es su trabajo. */
    @Test
    @DisplayName("un organizador puede crear carreras: 201")
    void elOrganizadorPuedeCrearCarreras() throws Exception {
        mockMvc.perform(post("/api/races")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carreraValida()))
                .andExpect(status().isCreated());
    }

    /**
     * Enunciado: "Return 404 for a missing resource."
     *
     * El detalle importante es que sea 404 y no 500. Un id que no existe es una
     * situacion normal (un enlace viejo, un dato borrado), no un error del servidor,
     * y el que lo recibe tiene que poder distinguir "no esta" de "se rompio algo".
     */
    @Test
    @DisplayName("un id que no existe devuelve 404")
    void unIdInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/competitors/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * La validacion del DTO, vista desde afuera.
     *
     * Los tests unitarios ya comprueban que el validador marque el campo. Este
     * comprueba la otra mitad: que esa marca se convierta en un 400 con el nombre
     * del campo adentro, que es lo que necesita un formulario para senalar el
     * casillero equivocado.
     */
    @Test
    @DisplayName("un cuerpo invalido devuelve 400 diciendo que campo esta mal")
    void unCuerpoInvalidoDevuelve400() throws Exception {
        String carreraEnElPasado = carreraValida()
                .replace("\"distanceMeters\": 1000", "\"distanceMeters\": -5");

        mockMvc.perform(post("/api/races")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carreraEnElPasado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.distanceMeters").exists());
    }

    /**
     * La bitacora es solo del administrador.
     *
     * Es la unica seccion con esa restriccion, y tiene sentido: la auditoria dice
     * quien hizo cada cosa, y eso no es informacion operativa sino de control. Un
     * organizador no tiene por que poder revisar lo que hicieron los demas.
     */
    @Test
    @DisplayName("la bitacora no la ve ni el organizador: 403")
    void laBitacoraEsSoloDelAdmin() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    /** El healthcheck es publico: lo consulta Docker, que no tiene token. */
    @Test
    @DisplayName("el healthcheck responde sin token")
    void elHealthcheckEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
