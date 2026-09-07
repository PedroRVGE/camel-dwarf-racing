package com.eia.camelracing.standings.dto;

/**
 * Una fila de la tabla de posiciones tal como la devuelve la base, ANTES de
 * saber en que puesto quedo.
 *
 * POR QUE EXISTE ESTE RECORD ADEMAS DE StandingResponse
 * Porque el puesto no se puede calcular en la consulta. La base sabe sumar los
 * puntos de cada participante y ordenarlos, pero el numero de puesto depende de
 * cuantas filas quedaron ANTES en el orden global, y eso incluye las de las
 * paginas anteriores. La consulta paginada, por definicion, solo ve su pagina.
 *
 * (SQL moderno tiene funciones de ventana como RANK() que resuelven exactamente
 * esto, pero JPQL no las expone, y bajar a SQL nativo por un numero que se calcula
 * con una suma es cambiar portabilidad por nada.)
 *
 * Asi que la base devuelve estas filas ya ordenadas y StandingsService les pone el
 * puesto contando desde el offset de la pagina. La pagina 2 con 20 por pagina
 * empieza en el puesto 21.
 *
 * Los numeros son Long y no long porque SUM() en JPQL devuelve Long: son los tipos
 * que Hibernate le va a pasar al constructor.
 */
public record StandingRow(
        java.util.UUID participantId,
        String participantName,
        Long points,
        Long racesFinished,
        Long victories,
        Long defeats
) {
}
