package com.eia.camelracing.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Portada de la documentacion de Swagger y cableado del boton "Authorize".
 *
 * Springdoc arma la documentacion solo, leyendo los @RestController y los DTOs:
 * esta clase NO es obligatoria para que Swagger funcione. Lo que agrega son dos
 * cosas:
 *
 *   1. El encabezado (titulo, version, descripcion). Sin ella la pantalla dice
 *      "OpenAPI definition 1.0", que no le explica nada a quien abre la pagina.
 *   2. El login. Sin el esquema de seguridad de mas abajo, Swagger manda todos
 *      los requests sin token y absolutamente todo responde 401, que es la
 *      primera confusion de cualquiera que abre la pagina por primera vez.
 *
 * Donde queda todo una vez levantada la aplicacion:
 *   - Interfaz web:  http://localhost:8080/swagger-ui.html
 *   - JSON crudo:    http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    /** Nombre interno del esquema de seguridad; se referencia dos veces mas abajo. */
    private static final String OAUTH_SCHEME = "keycloak";

    /**
     * Direccion de Keycloak vista DESDE EL NAVEGADOR.
     *
     * Va como property y no escrita a mano porque el navegador y el contenedor de
     * la app ven a Keycloak en direcciones distintas, y esta es la del navegador:
     * Swagger corre en tu maquina, asi que para el Keycloak es localhost, no
     * "keycloak".
     */
    @Value("${keycloak.public-url}")
    private String keycloakPublicUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Bean
    public OpenAPI camelRacingOpenAPI() {
        return new OpenAPI()
                // Conecta el boton "Authorize" de Swagger con Keycloak.
                .components(new Components().addSecuritySchemes(OAUTH_SCHEME, keycloakScheme()))
                // Y esto aplica el candado a TODOS los endpoints de la
                // documentacion. Sin esta linea el esquema queda definido pero sin
                // usar, y Swagger manda los requests sin el token.
                .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME))
                .info(new Info()
                        .title("Camel vs Dwarf Racing API")
                        .version("1.0")
                        .description("""
                                API de la liga de carreras de camellos contra enanos de la
                                Universidad EIA.

                                ---

                                ## Todos los endpoints piden login

                                Apreta **Authorize** (arriba a la derecha) y entra con uno de estos
                                usuarios de prueba:

                                | Usuario | Contrasena | Rol | Que puede hacer |
                                |---|---|---|---|
                                | `admin` | `admin123` | Administrador | todo, incluida la auditoria |
                                | `organizer` | `organizer123` | Organizador | carreras, inscripciones y resultados; consulta competidores y equipos |
                                | `viewer` | `viewer123` | Espectador | solo lectura |

                                Sin loguearte, cualquier llamada devuelve **401**. Logueado como
                                `viewer`, los GET andan pero un POST devuelve **403**: la diferencia
                                es que 401 es "no se quien sos" y 403 es "se quien sos, pero no te
                                alcanza".

                                ## Sobre el login

                                Esta API no guarda usuarios ni contrasenas y no emite tokens: los
                                emite Keycloak y aca solo se verifica la firma. Por eso no vas a
                                encontrar `/api/auth/login` ni `/api/auth/register`; lo unico que
                                hay es `/api/auth/profile`, que dice quien sos segun el token que
                                trajiste.

                                ## Errores

                                Todos los errores tienen la misma forma, con `timestamp`, `status`,
                                `error`, `message` y `path`. Los de validacion agregan un
                                `validationErrors` con el detalle campo por campo.

                                | Codigo | Cuando |
                                |---|---|
                                | 400 | El pedido esta mal formado o no pasa las validaciones |
                                | 401 | Falta el token o no es valido |
                                | 403 | El token es valido pero el rol no alcanza |
                                | 404 | No existe el recurso |
                                | 409 | El pedido choca con una regla de negocio o con un dato existente |
                                """)
                        .contact(new Contact()
                                .name("Universidad EIA - Implementacion de Software"))
                        .license(new License().name("Uso academico")));
    }

    /**
     * Describe como se hace el login, para que Swagger pueda hacerlo solo.
     *
     * El flujo elegido es "authorization code": Swagger te manda a la pantalla de
     * Keycloak, ahi pones usuario y contrasena, y Keycloak te devuelve a Swagger
     * con un codigo que se canjea por el token. La contrasena nunca pasa por
     * Swagger ni por esta aplicacion, que es todo el punto de usar OAuth2.
     *
     * Las dos URLs salen del realm de Keycloak y son las mismas que usaria
     * cualquier otro cliente, incluido el frontend de React.
     */
    private SecurityScheme keycloakScheme() {
        String base = keycloakPublicUrl + "/realms/" + realm + "/protocol/openid-connect";

        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(base + "/auth")
                .tokenUrl(base + "/token")
                // Los scopes son estandar de OpenID Connect. Los roles no van aca:
                // Keycloak los mete solo dentro del token, en realm_access.roles.
                .scopes(new Scopes()
                        .addString("openid", "Identificar al usuario")
                        .addString("profile", "Nombre y datos basicos")
                        .addString("email", "Correo del usuario"));

        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Login con Keycloak. Usuarios de prueba: admin/admin123, "
                        + "organizer/organizer123 y viewer/viewer123")
                .flows(new OAuthFlows().authorizationCode(authorizationCode));
    }
}
