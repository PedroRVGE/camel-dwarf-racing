/**
 * Los controles de pagina, alimentados por el PageResponse de la API.
 *
 * El backend devuelve page, size, totalElements, totalPages, first y last. Los
 * dos ultimos existen justamente para esto: saber si hay pagina anterior o
 * siguiente sin tener que hacer la cuenta aca. Calcularla por nuestra cuenta
 * seria repetir una regla que el servidor ya resolvio, y se desincronizaria el
 * dia que la ultima pagina quede incompleta.
 *
 * "page" viaja base 0 hacia la API y se muestra base 1: nadie lee "pagina 0 de 3".
 */
export default function Paginacion({ pagina, alCambiarPagina }) {
    if (!pagina || pagina.totalElements === 0) {
        return null;
    }

    return (
        <nav className="paginacion" aria-label="Paginacion">
            <span className="paginacion-resumen">
                {pagina.totalElements} {pagina.totalElements === 1 ? 'resultado' : 'resultados'}
                {pagina.totalPages > 1 && ` · pagina ${pagina.page + 1} de ${pagina.totalPages}`}
            </span>

            {pagina.totalPages > 1 && (
                <div className="paginacion-botones">
                    <button
                        type="button"
                        className="boton boton-secundario"
                        onClick={() => alCambiarPagina(pagina.page - 1)}
                        disabled={pagina.first}
                    >
                        Anterior
                    </button>
                    <button
                        type="button"
                        className="boton boton-secundario"
                        onClick={() => alCambiarPagina(pagina.page + 1)}
                        disabled={pagina.last}
                    >
                        Siguiente
                    </button>
                </div>
            )}
        </nav>
    );
}
