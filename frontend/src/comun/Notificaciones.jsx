import { createContext, useCallback, useContext, useMemo, useState } from 'react';

/**
 * Los avisos de exito y de error que aparecen en una esquina.
 *
 * El enunciado los pide: "success and failure notifications must be visible".
 * Cubren el hueco que dejan los otros estados: cuando una accion TERMINA bien, la
 * pantalla vuelve a la lista y, sin un aviso, no queda ninguna senal de que algo
 * paso. El usuario se queda con la duda de si guardo o no.
 *
 * Estan en un contexto y no en cada pantalla porque casi siempre se disparan
 * justo antes de navegar a otro lado: si el aviso viviera en la pantalla que
 * guarda, se desmontaria con ella y no llegaria a verse.
 *
 * role="status" (y no "alert") es a proposito para los de exito: hace que un
 * lector de pantalla lo anuncie sin interrumpir lo que estaba leyendo. Los de
 * error si usan "alert", porque ahi si conviene interrumpir.
 */
const ContextoDeNotificaciones = createContext(null);

let siguienteId = 1;

export function ProveedorDeNotificaciones({ children }) {
    const [avisos, setAvisos] = useState([]);

    const quitar = useCallback((id) => {
        setAvisos((actuales) => actuales.filter((aviso) => aviso.id !== id));
    }, []);

    const avisar = useCallback((texto, tipo) => {
        const id = siguienteId++;
        setAvisos((actuales) => [...actuales, { id, texto, tipo }]);
        // Se van solos a los cinco segundos. Un aviso que hay que cerrar a mano
        // termina siendo un estorbo cuando se encadenan varias acciones.
        window.setTimeout(() => quitar(id), 5000);
    }, [quitar]);

    const valor = useMemo(() => ({
        exito: (texto) => avisar(texto, 'exito'),
        error: (texto) => avisar(texto, 'error')
    }), [avisar]);

    return (
        <ContextoDeNotificaciones.Provider value={valor}>
            {children}
            <div className="avisos">
                {avisos.map((aviso) => (
                    <div
                        key={aviso.id}
                        className={`aviso aviso-${aviso.tipo}`}
                        role={aviso.tipo === 'error' ? 'alert' : 'status'}
                    >
                        <span>{aviso.texto}</span>
                        <button
                            type="button"
                            className="aviso-cerrar"
                            onClick={() => quitar(aviso.id)}
                            aria-label="Cerrar el aviso"
                        >
                            ×
                        </button>
                    </div>
                ))}
            </div>
        </ContextoDeNotificaciones.Provider>
    );
}

export function useNotificaciones() {
    const contexto = useContext(ContextoDeNotificaciones);
    if (contexto === null) {
        throw new Error('useNotificaciones se uso fuera de <ProveedorDeNotificaciones>');
    }
    return contexto;
}
