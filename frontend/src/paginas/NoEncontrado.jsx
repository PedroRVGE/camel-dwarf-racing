import { Link, useLocation } from 'react-router-dom';

/**
 * La pantalla de no encontrado: el 404 de la interfaz.
 *
 * Aparece cuando la direccion no corresponde a ninguna pantalla. Muestra la
 * direccion que se intento abrir porque casi siempre el problema esta ahi: un
 * enlace viejo, una direccion copiada a medias, una letra de mas.
 *
 * Ojo con la diferencia: esta pantalla es para una RUTA que no existe. Cuando lo
 * que no existe es el DATO (una carrera borrada, un identificador inventado), la
 * API contesta 404 y la pantalla correspondiente muestra su propio mensaje sin
 * salir de su seccion, para no perder la navegacion del lugar donde estabas.
 */
export default function NoEncontrado() {
    const ubicacion = useLocation();

    return (
        <div className="pagina-mensaje">
            <span className="pagina-mensaje-codigo">404</span>
            <h1>Esta pagina no existe</h1>
            <p>
                No hay nada en <code>{ubicacion.pathname}</code>.
            </p>
            <p className="pagina-mensaje-detalle">
                Puede que el enlace sea viejo o que la direccion tenga un error de tipeo.
            </p>
            <Link to="/" className="boton boton-primario">Volver al panel</Link>
        </div>
    );
}
