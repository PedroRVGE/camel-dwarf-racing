package com.eia.camelracing.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * El unico lugar donde se arman las respuestas de error de la API.
 *
 * Sin esto, cada excepcion que escapa de un controlador termina en la pagina de
 * error por defecto de Spring, que devuelve un JSON distinto y, segun como este
 * configurado, con la traza de la excepcion adentro. Devolver una traza es una de
 * las condiciones que reprueban el trabajo, y ademas le regala a cualquiera el
 * mapa interno de la aplicacion: nombres de clases, de tablas y de librerias.
 *
 * ORDEN DE PRECEDENCIA
 * Spring elige el handler cuyo tipo de excepcion sea el MAS ESPECIFICO de los que
 * coincidan, no el que este declarado primero. Por eso el handler de Exception
 * que esta al final no se come a los demas: solo actua cuando ninguno de los de
 * arriba aplica.
 *
 * LO QUE ESTA CLASE NO ATRAPA
 * Los 401 y los 403. Esos no son excepciones de un controlador: los produce la
 * cadena de filtros de Spring Security, que corre ANTES de que el request llegue
 * al DispatcherServlet. Un @RestControllerAdvice no puede verlos, y ese es el
 * motivo por el que muchos proyectos terminan con un 403 de cuerpo vacio al lado
 * de errores bien formateados. Se resuelven en SecurityErrorResponder, que se
 * enchufa en la cadena de filtros y produce el mismo ErrorResponse.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // 400 - lo que mando el cliente esta mal formado
    // ------------------------------------------------------------------

    /**
     * Fallaron las validaciones del @RequestBody: un @NotBlank vacio, un
     * @Positive negativo, un @Future en el pasado.
     *
     * Es el unico caso donde la respuesta lleva validationErrors, porque es el
     * unico donde se sabe QUE campo fallo. Sin ese mapa, un formulario de diez
     * campos recibe "datos invalidos" y el usuario tiene que adivinar cual.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errores = new LinkedHashMap<>();
        // putIfAbsent y no put: si un campo tiene dos anotaciones que fallan
        // (@NotBlank y @Size, por ejemplo), se queda con el primer mensaje en vez
        // de pisarlo. Mostrarle los dos al usuario no ayuda: arregla uno y
        // aparece el otro.
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        // Errores que no pertenecen a un campo puntual, sino a la combinacion de
        // varios: "la fecha de cierre tiene que ser anterior a la de largada".
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errores.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return construir(HttpStatus.BAD_REQUEST,
                "Error de validacion en los datos enviados", request, errores);
    }

    /**
     * Fallaron las validaciones de los parametros sueltos del metodo, no del
     * body: un ?size=0 con @Min(1), por ejemplo.
     *
     * Es una excepcion distinta de MethodArgumentNotValidException, que es solo
     * para el @RequestBody. Desde Spring 6.1 el framework valida por su cuenta los
     * @RequestParam y @PathVariable anotados y lanza esta. Sin este handler igual
     * saldria un 400, pero con un cuerpo distinto al del resto de la API.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        Map<String, String> errores = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        errores.putIfAbsent(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())));

        return construir(HttpStatus.BAD_REQUEST,
                "Error de validacion en los parametros de la consulta", request, errores);
    }

    /**
     * El tipo del parametro no coincide: alguien pidio /api/competitors/hola
     * cuando la ruta espera un UUID, o mando ?status=CORRIENDO cuando el enum solo
     * conoce ACTIVE, INJURED, SUSPENDED y RETIRED.
     *
     * Sin este handler esto sale como 500, que es directamente falso: el servidor
     * no fallo, el pedido estaba mal. Y un 500 hace que el frontend muestre "error
     * del servidor" cuando deberia decirle al usuario que corrija el dato.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String esperado = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "otro tipo";
        String mensaje = "El parametro '" + ex.getName() + "' recibio el valor '"
                + ex.getValue() + "', que no es un " + esperado + " valido";

        return construir(HttpStatus.BAD_REQUEST, mensaje, request, null);
    }

    /**
     * El cuerpo del request no se pudo leer: JSON con una coma de mas, un campo de
     * texto donde se esperaba un numero, un valor que no corresponde a ningun
     * valor del enum.
     *
     * Se devuelve un mensaje propio y NO ex.getMessage(): el mensaje de Jackson
     * viene con el nombre completo de la clase Java y a veces con un fragmento del
     * cuerpo enviado. Eso no le sirve a nadie del otro lado y filtra estructura
     * interna.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Cuerpo de request ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST,
                "El cuerpo del request no es un JSON valido o tiene un valor de tipo incorrecto",
                request, null);
    }

    // ------------------------------------------------------------------
    // 404 - no existe
    // ------------------------------------------------------------------

    /**
     * Se pidio algo que no esta en la base.
     *
     * Se usa ex.getMessage() y no un texto fijo porque los servicios lanzan esto
     * con mensajes tipo "Competitor with id 45 was not found", que dicen
     * exactamente que falto. Con un texto generico esa informacion se pierde y el
     * frontend no puede mostrar nada util.
     *
     * El status del cuerpo va con el mismo codigo que el de la respuesta HTTP. Es
     * facil que se separen si se copian handlers entre proyectos, y entonces el
     * cliente ve un 404 en la cabecera y un 400 adentro del JSON.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElementException(
            NoSuchElementException ex, HttpServletRequest request) {

        String mensaje = ex.getMessage() != null ? ex.getMessage() : "No se encontro el recurso solicitado";
        return construir(HttpStatus.NOT_FOUND, mensaje, request, null);
    }

    /**
     * La URL no corresponde a ningun endpoint ni a ningun archivo estatico.
     *
     * Sin esto, pedir /api/inventado devuelve la pagina de error por defecto de
     * Spring en vez del formato de esta API, y el frontend no puede distinguir un
     * 404 de "no existe ese competidor" de un 404 de "esa ruta no existe".
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        return construir(HttpStatus.NOT_FOUND,
                "No existe el recurso " + request.getRequestURI(), request, null);
    }

    // ------------------------------------------------------------------
    // 405 - el recurso existe, pero no con ese verbo
    // ------------------------------------------------------------------

    /**
     * Se uso un verbo HTTP que ese endpoint no soporta.
     *
     * Sin este manejador, un POST a /api/audit —que es de solo lectura— caia en el
     * catch-all y devolvia 500, o sea "el servidor se rompio". Y no se rompio nada:
     * la ruta existe y esta funcionando perfectamente, lo que no existe es esa
     * combinacion de verbo y ruta. Un 500 ahi manda a buscar un error que no esta.
     *
     * La cabecera Allow es parte del protocolo: le dice al cliente que verbos si
     * puede usar contra esa ruta, en vez de dejarlo probando de a uno.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String permitidos = ex.getSupportedHttpMethods() == null
                ? ""
                : ex.getSupportedHttpMethods().toString();

        ResponseEntity<ErrorResponse> respuesta = construir(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + ex.getMethod() + " no esta permitido en " + request.getRequestURI()
                        + (permitidos.isEmpty() ? "" : ". Metodos validos: " + permitidos),
                request, null);

        if (ex.getSupportedHttpMethods() == null || ex.getSupportedHttpMethods().isEmpty()) {
            return respuesta;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]))
                .body(respuesta.getBody());
    }

    // ------------------------------------------------------------------
    // 409 - choca con el estado actual del sistema
    // ------------------------------------------------------------------

    /**
     * Se rompio una regla de negocio. La explicacion de por que esto es 409 y no
     * 400 esta en BusinessRuleException.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {

        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /**
     * La base rechazo la escritura: una unique constraint repetida, una foreign
     * key que apunta a algo que ya no esta.
     *
     * ESTO ES UNA RED DE SEGURIDAD, NO EL CONTROL PRINCIPAL.
     * Los servicios verifican las reglas antes de escribir y lanzan
     * BusinessRuleException con un mensaje que explica el problema. Este handler
     * cubre lo que se escapa por una carrera entre dos requests simultaneos: los
     * dos consultan "existe este apodo?", los dos ven que no, y el segundo choca
     * contra la constraint al guardar.
     *
     * El mensaje que se devuelve es generico A PROPOSITO. ex.getMessage() de
     * Hibernate trae el SQL completo, el nombre de la tabla, el de la constraint y
     * a veces los valores: es basicamente un volcado del esquema. Lo real se
     * escribe en el log del servidor, donde solo lo ve quien administra.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Violacion de integridad en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT,
                "La operacion choca con un dato que ya existe o con una referencia que no existe",
                request, null);
    }

    // ------------------------------------------------------------------
    // 500 - se rompio algo nuestro
    // ------------------------------------------------------------------

    /**
     * La red de contencion final: cualquier excepcion que no atrapo nadie mas.
     *
     * Las dos mitades de este metodo son igual de importantes:
     *
     *   - log.error CON la excepcion entera, incluida la traza. Es la unica forma
     *     de que alguien pueda diagnosticar el problema despues.
     *   - la respuesta va SIN nada de eso. Un mensaje fijo, sin clases, sin
     *     tablas, sin traza.
     *
     * Devolver la traza al cliente es una de las condiciones que reprueban el
     * trabajo, y no es un capricho academico: una traza dice que framework y que
     * version corre el servidor, que es el primer dato que busca cualquiera que
     * quiera atacarlo.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error inesperado en el servidor", request, null);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Arma la respuesta. Existe para que el status HTTP y el campo "status" del
     * cuerpo salgan SIEMPRE del mismo HttpStatus y no se puedan contradecir, que
     * es el error clasico de escribir cada handler a mano.
     *
     * El campo "error" del enunciado ("Not Found", "Conflict") sale de
     * getReasonPhrase(), asi que tampoco hay que escribirlo a mano en ningun lado.
     */
    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String mensaje,
                                                    HttpServletRequest request,
                                                    Map<String, String> validationErrors) {
        ErrorResponse cuerpo = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.status(status).body(cuerpo);
    }
}
