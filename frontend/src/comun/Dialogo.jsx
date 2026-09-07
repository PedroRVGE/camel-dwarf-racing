import { useEffect, useRef } from 'react';

/**
 * El dialogo de confirmacion de las acciones destructivas.
 *
 * El enunciado lo exige: "a confirmation dialog is required for destructive
 * actions". Retirar un competidor, cancelar una carrera o rechazar una
 * inscripcion no se disparan de un solo clic.
 *
 * Tres detalles que lo hacen usable con el teclado y que se suelen olvidar:
 *
 *   - Escape cierra. Es lo que cualquiera intenta primero para salir sin hacer
 *     nada, y si no funciona el dialogo se siente trabado.
 *   - El foco entra al dialogo al abrirse, sobre el boton de CANCELAR y no sobre
 *     el de confirmar. Si el foco cayera en confirmar, un Enter de mas
 *     ejecutaria justamente la accion que se estaba tratando de proteger.
 *   - aria-modal y el titulo enlazado hacen que un lector de pantalla anuncie de
 *     que se trata en vez de leer la pagina de atras.
 */
export default function Dialogo({
    titulo,
    mensaje,
    textoDeConfirmacion = 'Confirmar',
    peligroso = false,
    trabajando = false,
    // Algunas confirmaciones necesitan un dato ademas del si o el no: rechazar
    // una inscripcion exige un motivo, y el backend lo pide obligatorio. Ese
    // campo entra por aca, sin que el dialogo tenga que saber de que se trata.
    children,
    confirmacionHabilitada = true,
    alConfirmar,
    alCancelar
}) {
    const botonDeCancelar = useRef(null);

    useEffect(() => {
        botonDeCancelar.current?.focus();

        const alPresionar = (evento) => {
            if (evento.key === 'Escape') {
                alCancelar();
            }
        };
        window.addEventListener('keydown', alPresionar);
        return () => window.removeEventListener('keydown', alPresionar);
    }, [alCancelar]);

    return (
        <div className="cortina" onClick={alCancelar}>
            {/* El clic en la cortina cierra; el clic adentro no tiene que
                propagarse hasta ella o cerraria el dialogo al tocar el texto. */}
            <div
                className="dialogo"
                role="dialog"
                aria-modal="true"
                aria-labelledby="dialogo-titulo"
                onClick={(evento) => evento.stopPropagation()}
            >
                <h2 id="dialogo-titulo">{titulo}</h2>
                <p>{mensaje}</p>

                {children}

                <div className="dialogo-botones">
                    <button
                        type="button"
                        className="boton boton-secundario"
                        ref={botonDeCancelar}
                        onClick={alCancelar}
                        disabled={trabajando}
                    >
                        Cancelar
                    </button>
                    <button
                        type="button"
                        className={`boton ${peligroso ? 'boton-peligro' : 'boton-primario'}`}
                        onClick={alConfirmar}
                        disabled={trabajando || !confirmacionHabilitada}
                    >
                        {trabajando ? 'Procesando...' : textoDeConfirmacion}
                    </button>
                </div>
            </div>
        </div>
    );
}
