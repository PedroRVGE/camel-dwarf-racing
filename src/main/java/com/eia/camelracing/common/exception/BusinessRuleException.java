package com.eia.camelracing.common.exception;

/**
 * Se rompio una regla de negocio, no una validacion de formato.
 *
 * LA DIFERENCIA CON UN 400, QUE ES LA QUE IMPORTA
 * Una validacion de formato mira UN dato aislado y siempre da el mismo veredicto:
 * un peso negativo esta mal hoy, mañana y con cualquier base de datos. Eso lo
 * resuelven las anotaciones del DTO (@NotBlank, @Positive) y devuelven 400.
 *
 * Una regla de negocio depende del ESTADO del sistema en este momento. El mismo
 * request, con los mismos datos, puede ser valido a las 10 e invalido a las 11:
 *
 *   - inscribir un competidor en una carrera abierta -> anda
 *   - el mismo request cinco minutos despues de cerrar la inscripcion -> falla
 *   - el mismo apodo cuando nadie lo uso -> anda; cuando ya existe -> falla
 *
 * Por eso el enunciado pide 409 (Conflict) para estos casos y no 400: el cliente
 * no mando nada mal formado, mando algo que choca con el estado actual. Es la
 * diferencia entre "corregi el formulario" y "el formulario esta bien, pero
 * llegaste tarde".
 *
 * Es RuntimeException y no una excepcion chequeada a proposito. Estas se lanzan
 * desde el fondo de un servicio y las atrapa GlobalExceptionHandler arriba de
 * todo; obligar a declararlas con throws en cada metodo del camino solo agregaria
 * ruido a firmas que no hacen nada con ellas.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
