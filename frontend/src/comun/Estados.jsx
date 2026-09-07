// =============================================================================
//  Los cuatro estados que puede tener una pantalla
// =============================================================================
//  El enunciado los nombra explicitamente: "the interface must clearly show
//  loading, success, empty and error states".
//
//  Son cuatro y no dos, y la diferencia entre los dos ultimos es la que mas se
//  olvida: una lista VACIA y una lista que FALLO se ven igual (no hay filas) y
//  significan cosas opuestas. Si las dos muestran una tabla en blanco, el usuario
//  no tiene forma de saber si no hay competidores o si la API esta caida.
// =============================================================================

/** Mientras se espera. Cualquier operacion que tarde muestra esto. */
export function Cargando({ mensaje = 'Cargando' }) {
    return (
        <div className="estado" role="status" aria-live="polite">
            <span className="girador" aria-hidden="true" />
            <p>{mensaje}...</p>
        </div>
    );
}

/**
 * Cuando no hay nada que mostrar.
 *
 * Recibe una accion opcional porque un vacio casi siempre tiene una salida: si no
 * hay carreras, el organizador quiere crear una. Una pantalla vacia sin salida
 * deja al usuario sin saber que hacer.
 */
export function SinDatos({ titulo = 'No hay nada por aca', detalle, children }) {
    return (
        <div className="estado estado-vacio">
            <p className="estado-titulo">{titulo}</p>
            {detalle && <p className="estado-detalle">{detalle}</p>}
            {children}
        </div>
    );
}

/**
 * Cuando algo fallo.
 *
 * Muestra el mensaje que mando la API, que es el que explica la regla concreta, y
 * ofrece reintentar. Nunca muestra el detalle tecnico: el enunciado prohibe
 * exponer trazas, y ademas al usuario no le sirven.
 */
export function ErrorDeCarga({ mensaje, alReintentar }) {
    return (
        <div className="estado estado-error" role="alert">
            <p className="estado-titulo">No se pudo cargar</p>
            <p className="estado-detalle">{mensaje}</p>
            {alReintentar && (
                <button type="button" className="boton boton-secundario" onClick={alReintentar}>
                    Reintentar
                </button>
            )}
        </div>
    );
}
