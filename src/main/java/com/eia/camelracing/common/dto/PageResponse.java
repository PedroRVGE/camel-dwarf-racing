package com.eia.camelracing.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Una pagina de resultados, con la forma que devuelve esta API.
 *
 * POR QUE NO SE DEVUELVE EL Page DE SPRING DIRECTAMENTE
 * Se podria escribir ResponseEntity<Page<CompetitorResponse>> y funcionaria, pero
 * el JSON que sale de ahi es la serializacion de PageImpl, una clase interna de
 * Spring Data. Eso trae dos problemas:
 *
 *   1. La propia documentacion de Spring avisa que ese JSON no es un contrato
 *      estable y puede cambiar entre versiones. El frontend quedaria atado a un
 *      detalle de implementacion de una libreria.
 *   2. Trae campos que no le sirven a nadie del otro lado: "pageable", con el
 *      offset y el objeto sort completo adentro, "numberOfElements", "empty".
 *      Son unos veinte campos anidados para decir en que pagina estas.
 *
 * Con este record el contrato lo define el proyecto: siete campos, todos con un
 * significado obvio, documentados en Swagger y que no cambian porque alguien
 * actualice Spring Data.
 *
 * @param content       los elementos de esta pagina
 * @param page          numero de pagina, empezando en 0
 * @param size          cuantos elementos por pagina se pidieron
 * @param totalElements cuantos elementos hay en total, sin paginar
 * @param totalPages    cuantas paginas hay en total
 * @param first         si esta es la primera pagina
 * @param last          si esta es la ultima pagina
 */
@Schema(description = "Una pagina de resultados")
public record PageResponse<T>(

        @Schema(description = "Los elementos de esta pagina")
        List<T> content,

        @Schema(description = "Numero de pagina, empezando en 0", example = "0")
        int page,

        @Schema(description = "Cuantos elementos por pagina", example = "20")
        int size,

        @Schema(description = "Total de elementos sin paginar", example = "137")
        long totalElements,

        @Schema(description = "Total de paginas", example = "7")
        int totalPages,

        @Schema(description = "Si es la primera pagina", example = "true")
        boolean first,

        @Schema(description = "Si es la ultima pagina", example = "false")
        boolean last
) {

    /**
     * Convierte un Page de entidades en un PageResponse de DTOs.
     *
     * El segundo parametro es el mapper que traduce cada elemento, tipicamente una
     * referencia a metodo: PageResponse.of(pagina, CompetitorMapper::toResponse).
     *
     * Existe para que ningun servicio tenga que repetir estas siete lineas. Si se
     * copiara a mano en cada listado, tarde o temprano uno se olvida de un campo o
     * invierte first y last, y la paginacion del frontend se rompe solo en esa
     * pantalla.
     *
     * Se usa page.map(...) de Spring y no un stream sobre el contenido porque
     * Page.map conserva los metadatos —total de elementos, numero de pagina— que
     * un stream perderia.
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.map(mapper).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /**
     * Para las consultas que YA devuelven el DTO armado.
     *
     * Pasa cuando la proyeccion se hace en la base con un constructor expression
     * (SELECT new ...TeamSummaryResponse(...)): ahi no hay entidades que mapear,
     * el repositorio ya devolvio exactamente lo que se va a responder.
     *
     * Sin esta version habria que escribir PageResponse.of(pagina,
     * Function.identity()), que funciona pero deja al lector preguntandose que
     * transformacion se esta aplicando. Ninguna.
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }
}
