package com.eia.camelracing.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Los 401 y los 403, con el mismo formato de error que el resto de la API.
 *
 * POR QUE ESTO NO ES UN @ExceptionHandler MAS
 * Un @RestControllerAdvice atrapa lo que sale de un controlador. Los errores de
 * autenticacion y autorizacion pasan antes: los produce la cadena de filtros de
 * Spring Security, que corre delante del DispatcherServlet. Cuando falta el token
 * o el rol no alcanza, el request NUNCA llega a un controlador, asi que
 * GlobalExceptionHandler no se entera.
 *
 * El resultado por defecto es un 401 y un 403 con el cuerpo VACIO. La API queda
 * con dos formatos de error: uno lindo para todo lo que pasa el filtro y nada
 * para lo que no. Y justo esos dos son los que el frontend mas necesita
 * distinguir, porque uno manda a la pantalla de login y el otro a la de acceso
 * denegado.
 *
 * Esta clase se enchufa en los dos puntos donde Spring Security permite meter
 * mano, y devuelve el mismo ErrorResponse que todo lo demas.
 *
 * LA DIFERENCIA ENTRE LOS DOS CODIGOS
 *   401 Unauthorized -> "no se quien sos": no vino token, vencio, o la firma no
 *                       valida. La respuesta es volver a loguearse.
 *   403 Forbidden    -> "se quien sos, pero no te alcanza": el token es
 *                       perfecto, el rol no da. Volver a loguearse no cambia nada.
 *
 * Confundirlos hace que el frontend mande al usuario a loguearse de nuevo una y
 * otra vez ante un problema de permisos, en un bucle del que no puede salir.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * El ObjectMapper que ya configuro Spring Boot, no uno nuevo.
     *
     * Importa: el de Boot viene con el modulo de java.time registrado y con las
     * fechas como texto ISO. Uno construido a mano con new ObjectMapper() no sabe
     * serializar LocalDateTime y falla, o lo escribe como un array de numeros. En
     * cualquiera de los dos casos, el campo timestamp de estos errores saldria
     * distinto al de todos los demas.
     *
     * OJO CON EL IMPORT: es tools.jackson.databind.ObjectMapper, NO
     * com.fasterxml.jackson.databind.ObjectMapper.
     *
     * Spring Boot 4 migro a Jackson 3, que cambio de paquete raiz: databind pasó
     * de com.fasterxml.jackson a tools.jackson. El Jackson 2 sigue apareciendo en
     * el classpath como dependencia transitiva de otras librerias, asi que el
     * import viejo COMPILA sin una sola advertencia; lo que no existe es el bean.
     * La aplicacion arranca, llega hasta el final del contexto y recien ahi falla
     * con "required a bean of type ObjectMapper that could not be found", que no
     * dice ni una palabra de que el problema es la version.
     *
     * Las anotaciones (@JsonInclude, @JsonProperty) SI siguen en
     * com.fasterxml.jackson.annotation, tambien en Jackson 3. O sea que en el
     * mismo proyecto conviven los dos prefijos y cada uno es correcto en su lugar.
     */
    private final ObjectMapper objectMapper;

    /** 401: no hay autenticacion valida. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escribir(request, response, HttpStatus.UNAUTHORIZED,
                "Hace falta un token valido para acceder a este recurso");
    }

    /** 403: hay autenticacion, pero el rol no alcanza. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN,
                "Tu rol no tiene permiso para esta operacion");
    }

    /**
     * El mensaje que se devuelve es deliberadamente vago sobre el motivo.
     *
     * Decir "el token vencio hace 3 minutos" o "te falta el rol organizer" seria
     * comodo para depurar y es informacion que no hay por que darle a alguien que
     * todavia no probo quien es. El detalle real de por que se rechazo un token
     * esta en los logs del servidor, y se puede subir el nivel con
     * logging.level.org.springframework.security: debug (ver application-docker.yml).
     */
    private void escribir(HttpServletRequest request, HttpServletResponse response,
                          HttpStatus status, String mensaje) throws IOException {

        ErrorResponse cuerpo = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                request.getRequestURI()
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Sin esto, un mensaje con acentos sale con caracteres rotos en el
        // navegador: el charset por defecto de la respuesta no es UTF-8.
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
