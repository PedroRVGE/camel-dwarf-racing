package com.eia.camelracing.common.config;

import com.eia.camelracing.common.exception.SecurityErrorResponder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Quien puede entrar a que.
 *
 * COMO FUNCIONA EL LOGIN, EN CORTO
 * Esta aplicacion NO tiene usuarios ni contrasenas propias. De eso se encarga
 * Keycloak, que es un servidor de identidad aparte. El circuito es:
 *
 *   1. El usuario se loguea EN KEYCLOAK (desde el frontend de React o desde
 *      Swagger). La contrasena nunca pasa por esta aplicacion.
 *   2. Keycloak le devuelve un token JWT firmado, que dice quien es y que roles
 *      tiene.
 *   3. El navegador manda ese token en cada request, en la cabecera
 *      Authorization: Bearer eyJhbGciOi...
 *   4. Esta app verifica la firma del token contra la clave publica de Keycloak
 *      y, si esta bien, deja pasar segun el rol.
 *
 * A ese papel (el que guarda los datos y exige token, pero no emite tokens) se le
 * dice "resource server", y es lo que configura esta clase.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Produce los 401 y 403 con el mismo formato de error que el resto de la API. */
    private final SecurityErrorResponder securityErrorResponder;

    /**
     * Desde que direcciones acepta llamadas del navegador.
     *
     * Llega como variable de entorno desde compose.yml. Los valores por defecto
     * cubren los dos modos de trabajo del frontend: 3000 es el contenedor de
     * nginx y 5173 el servidor de desarrollo de Vite.
     */
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Las reglas de acceso, en orden. La PRIMERA que coincide gana, asi que van de
     * lo mas especifico a lo mas general: si anyRequest() estuviera arriba, se
     * comeria todo lo de abajo.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // --------------------------------------------------------------
                // CORS
                // --------------------------------------------------------------
                // El frontend corre en otro puerto que la API, y para el navegador
                // eso es otro ORIGEN distinto. Sin esta configuracion, el navegador
                // bloquea cada llamada antes de que salga y en la consola aparece
                // un error de CORS que no dice nada del backend, porque el backend
                // ni se entero.
                //
                // Ojo con el orden: tiene que estar antes de authorizeHttpRequests
                // para que el filtro de CORS quede delante del de autorizacion. Si
                // no, la peticion preflight (el OPTIONS que el navegador manda
                // solo, sin cabecera Authorization) recibe un 401 y la llamada real
                // nunca se hace.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // --------------------------------------------------------------
                // CSRF apagado
                // --------------------------------------------------------------
                // CSRF protege contra que otra pagina use la sesion del usuario sin
                // que se de cuenta. Ese ataque se apoya en que el navegador manda la
                // cookie de sesion sola. Aca no hay cookie de sesion: la
                // autorizacion va en una cabecera que hay que poner a mano, y otra
                // pagina no puede hacerlo. Sin cookie no hay CSRF que prevenir, y
                // dejarlo prendido rompe todos los POST de la API.
                .csrf(csrf -> csrf.disable())

                // --------------------------------------------------------------
                // Sin sesiones
                // --------------------------------------------------------------
                // STATELESS: el servidor no guarda nada entre requests. Cada llamada
                // trae su token y se valida sola. Es lo que corresponde a una API y
                // permite levantar varias copias de la app sin compartir sesiones.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ---- ABIERTO A CUALQUIERA ----

                        // El preflight de CORS. El navegador lo manda SIN el token,
                        // asi que si pidiera autenticacion nunca pasaria y ninguna
                        // llamada del frontend funcionaria.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // El healthcheck del contenedor. Docker lo consulta sin
                        // ningun token, y no puede tenerlo: no hay usuario detras.
                        // Solo expone {"status":"UP"}, sin detalles (ver
                        // application.yml, show-details: never).
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Swagger: es la puerta de entrada para probar la API a mano,
                        // y ahi adentro esta el boton "Authorize" que hace el login.
                        // Se expone la DEFINICION de la API, no los datos: para
                        // llamar a cualquier endpoint sigue haciendo falta el token.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()

                        // Archivos estaticos.
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/css/**", "/js/**", "/img/**").permitAll()

                        // Sin esto, un error dentro de un request ya autenticado se
                        // transforma en un 403 confuso al reenviarse a /error.
                        .requestMatchers("/error").permitAll()

                        // ---- AUDITORIA: SOLO ADMINISTRADOR ----
                        // Va PRIMERO que las reglas de /api/** de abajo, porque si
                        // no, la regla de "cualquier GET lo ve cualquier rol" se lo
                        // comeria y un espectador podria leer el registro completo de
                        // quien hizo que. El enunciado es explicito: "Only
                        // administrators may view the complete audit log".
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

                        // ---- QUIEN SOY ----
                        // Cualquiera que este logueado puede preguntar por su propio
                        // perfil, sin importar el rol.
                        .requestMatchers("/api/auth/**").authenticated()

                        // ---- COMPETIDORES Y EQUIPOS: ESCRIBE SOLO EL ADMIN ----
                        // Segun la tabla del enunciado, el organizador de carreras
                        // solo CONSULTA competidores y equipos ("view competitors and
                        // teams"); darlos de alta o editarlos es del administrador.
                        .requestMatchers(HttpMethod.POST, "/api/competitors/**", "/api/teams/**")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/competitors/**", "/api/teams/**")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/competitors/**", "/api/teams/**")
                            .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/competitors/**", "/api/teams/**")
                            .hasRole("ADMIN")

                        // ---- CARRERAS, INSCRIPCIONES Y RESULTADOS ----
                        // Es el trabajo del organizador, y el administrador puede
                        // todo. Las rutas anidadas
                        // (/api/races/{id}/registrations, /api/races/{id}/results)
                        // caen adentro de /api/races/** y quedan cubiertas.
                        .requestMatchers(HttpMethod.POST, "/api/races/**",
                                "/api/registrations/**", "/api/results/**")
                            .hasAnyRole("ADMIN", "ORGANIZER")
                        .requestMatchers(HttpMethod.PUT, "/api/races/**",
                                "/api/registrations/**", "/api/results/**")
                            .hasAnyRole("ADMIN", "ORGANIZER")
                        .requestMatchers(HttpMethod.PATCH, "/api/races/**",
                                "/api/registrations/**", "/api/results/**")
                            .hasAnyRole("ADMIN", "ORGANIZER")
                        .requestMatchers(HttpMethod.DELETE, "/api/races/**",
                                "/api/registrations/**", "/api/results/**")
                            .hasAnyRole("ADMIN", "ORGANIZER")

                        // ---- LECTURA: LOS TRES ROLES ----
                        // El espectador lee el calendario, los competidores, los
                        // resultados y la clasificacion. La auditoria quedo afuera
                        // porque su regla esta declarada mas arriba.
                        .requestMatchers(HttpMethod.GET, "/api/**")
                            .hasAnyRole("ADMIN", "ORGANIZER", "VIEWER")

                        // ---- TODO LO DEMAS ----
                        // Por defecto se cierra, no se abre: si manana alguien agrega
                        // un endpoint y se olvida de darle una regla, queda protegido
                        // en vez de quedar expuesto.
                        .anyRequest().authenticated()
                )

                // --------------------------------------------------------------
                // Que devolver cuando no se pasa
                // --------------------------------------------------------------
                // Sin estas dos lineas, los 401 y 403 salen con el cuerpo VACIO,
                // porque los produce la cadena de filtros y no llegan nunca al
                // @RestControllerAdvice. Ver SecurityErrorResponder.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder)
                )

                // --------------------------------------------------------------
                // Validacion del token
                // --------------------------------------------------------------
                // Aca se enchufa el traductor de roles de abajo. Spring se encarga
                // solo de bajar la clave publica de Keycloak y verificar la firma,
                // la expiracion y el emisor; eso se configura por properties
                // (jwk-set-uri e issuer-uri en application-docker.yml).
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * Que origenes del navegador pueden llamar a esta API.
     *
     * Se listan explicitamente y no se usa "*" a proposito. Con el comodin
     * cualquier pagina de internet podria hacerle requests a esta API desde el
     * navegador de un usuario logueado; ademas, el estandar prohibe combinar "*"
     * con credenciales, asi que ni siquiera funcionaria.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // El frontend manda Authorization y Content-Type. Se listan en vez de usar
        // "*" por el mismo motivo que los origenes.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Cuanto puede cachear el navegador el resultado del preflight, en
        // segundos. Sin esto, cada llamada de la aplicacion son DOS viajes al
        // servidor: el OPTIONS y despues el real.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Traduce los roles de Keycloak al formato que entiende Spring Security.
     *
     * POR QUE HACE FALTA
     * Los dos hablan de "roles" pero los guardan distinto:
     *
     *   - Keycloak los mete anidados dentro del token:
     *       { "realm_access": { "roles": ["admin", "organizer", "viewer"] } }
     *   - Spring Security los busca planos y con prefijo ROLE_, porque
     *     hasRole("ADMIN") internamente compara contra "ROLE_ADMIN".
     *
     * Sin este traductor, Spring no encuentra ningun rol donde busca, TODAS las
     * reglas hasRole(...) fallan y todo devuelve 403 aunque el token este
     * perfecto. Es el error mas tipico al conectar Keycloak con Spring, y no deja
     * ninguna pista en los logs: el token es valido, la firma esta bien, el
     * usuario existe, y aun asi no puede hacer nada.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        return converter;
    }

    /**
     * El traductor propiamente dicho: saca realm_access.roles del token y devuelve
     * una lista de ROLE_LOQUESEA.
     *
     * Es estatica y sin estado, asi que se puede instanciar en un test unitario
     * sin levantar Spring. Eso importa: es la pieza cuyo error mas caro cuesta
     * encontrar, y conviene tenerla cubierta por un test que corre en
     * milisegundos.
     */
    public static class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            // Un token sin realm_access es valido: significa un usuario sin ningun
            // rol asignado. Se devuelve lista vacia y las reglas de acceso lo
            // rechazan solas, en vez de reventar con NullPointerException.
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .map(Object::toString)
                    // Keycloak los manda en minuscula ("admin"), Spring los espera
                    // en mayuscula y con prefijo ("ROLE_ADMIN").
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();
        }
    }
}
