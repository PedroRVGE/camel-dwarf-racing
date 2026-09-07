// =============================================================================
//  Las piezas de los formularios
// =============================================================================
//  Todos los formularios de la aplicacion se arman con estas piezas, y eso
//  resuelve de una sola vez tres cosas que el enunciado pide por separado:
//
//    - "Labels for all form fields": la etiqueta no es opcional, es un parametro.
//      No se puede dibujar un campo sin etiqueta ni por descuido.
//
//    - "Forms must show field-level validation messages": el error se muestra
//      PEGADO al campo. Un cartel arriba que diga "hay errores" obliga al usuario
//      a buscar cual es.
//
//    - "Keyboard-accessible forms": el <label> queda unido al control por el id,
//      asi que se puede llegar a el con el tabulador y hacer foco tocando el
//      texto. Ademas aria-invalid y aria-describedby hacen que un lector de
//      pantalla anuncie el error, y no solo lo pinte de rojo.
//
//  LOS ERRORES VIENEN DE DOS LADOS Y SE MUESTRAN IGUAL
//  Algunos los detecta el navegador antes de mandar (un campo obligatorio vacio)
//  y otros los devuelve la API en validationErrors, con el nombre del campo como
//  clave. Los dos terminan en el mismo lugar de la pantalla, porque para el
//  usuario son la misma cosa: ese campo esta mal.
// =============================================================================

function Campo({ etiqueta, nombre, error, ayuda, requerido, children }) {
    const idDeError = `${nombre}-error`;
    const idDeAyuda = `${nombre}-ayuda`;

    return (
        <div className={`campo ${error ? 'campo-con-error' : ''}`}>
            <label htmlFor={nombre}>
                {etiqueta}
                {requerido && <span className="campo-obligatorio" aria-hidden="true"> *</span>}
            </label>
            {children({ idDeError, idDeAyuda, hayError: Boolean(error) })}
            {ayuda && !error && <p className="campo-ayuda" id={idDeAyuda}>{ayuda}</p>}
            {error && <p className="campo-error" id={idDeError} role="alert">{error}</p>}
        </div>
    );
}

/** Un campo de texto, de numero o de fecha: cambia el tipo, no la estructura. */
export function CampoDeTexto({
    etiqueta, nombre, valor, alCambiar, tipo = 'text',
    error, ayuda, requerido, ...resto
}) {
    return (
        <Campo etiqueta={etiqueta} nombre={nombre} error={error} ayuda={ayuda} requerido={requerido}>
            {({ idDeError, idDeAyuda, hayError }) => (
                <input
                    id={nombre}
                    name={nombre}
                    type={tipo}
                    // El valor nunca puede ser null: React trata un control con
                    // valor null como "no controlado" y despues avisa por consola
                    // que cambio de no controlado a controlado. La cadena vacia es
                    // lo que corresponde para "sin dato".
                    value={valor ?? ''}
                    onChange={(evento) => alCambiar(evento.target.value)}
                    aria-invalid={hayError}
                    aria-describedby={hayError ? idDeError : (ayuda ? idDeAyuda : undefined)}
                    {...resto}
                />
            )}
        </Campo>
    );
}

/** Un desplegable. Las opciones son {valor, texto}, como las arma formato.js. */
export function CampoDeSeleccion({
    etiqueta, nombre, valor, alCambiar, opciones,
    vacio = 'Elegi una opcion', error, ayuda, requerido, ...resto
}) {
    return (
        <Campo etiqueta={etiqueta} nombre={nombre} error={error} ayuda={ayuda} requerido={requerido}>
            {({ idDeError, idDeAyuda, hayError }) => (
                <select
                    id={nombre}
                    name={nombre}
                    value={valor ?? ''}
                    onChange={(evento) => alCambiar(evento.target.value)}
                    aria-invalid={hayError}
                    aria-describedby={hayError ? idDeError : (ayuda ? idDeAyuda : undefined)}
                    {...resto}
                >
                    {vacio !== null && <option value="">{vacio}</option>}
                    {opciones.map((opcion) => (
                        <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                    ))}
                </select>
            )}
        </Campo>
    );
}

/** Un texto largo: descripciones, motivos de rechazo, observaciones. */
export function CampoDeArea({
    etiqueta, nombre, valor, alCambiar, error, ayuda, requerido, filas = 3, ...resto
}) {
    return (
        <Campo etiqueta={etiqueta} nombre={nombre} error={error} ayuda={ayuda} requerido={requerido}>
            {({ idDeError, idDeAyuda, hayError }) => (
                <textarea
                    id={nombre}
                    name={nombre}
                    rows={filas}
                    value={valor ?? ''}
                    onChange={(evento) => alCambiar(evento.target.value)}
                    aria-invalid={hayError}
                    aria-describedby={hayError ? idDeError : (ayuda ? idDeAyuda : undefined)}
                    {...resto}
                />
            )}
        </Campo>
    );
}

/**
 * El aviso de error general del formulario.
 *
 * Es para los errores que NO son de un campo: una regla de negocio que se viola
 * al combinar varios ("no se puede terminar una carrera sin resultados"). Esos no
 * tienen donde pegarse, asi que van arriba de los botones, donde el usuario mira
 * despues de que su envio no funciono.
 */
export function ErrorDelFormulario({ mensaje }) {
    if (!mensaje) {
        return null;
    }
    return <p className="aviso aviso-error" role="alert">{mensaje}</p>;
}
