import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { carreras } from '../../api/carreras.js';
import { inscripciones } from '../../api/inscripciones.js';
import { resultados } from '../../api/resultados.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import Dialogo from '../../comun/Dialogo.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { useRecurso } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_CARRERA,
    ESTADO_DE_INSCRIPCION,
    ESTADO_DE_RESULTADO,
    TIPO_DE_CARRERA,
    distancia,
    fechaYHora,
    numero,
    tiempo
} from '../../comun/formato.js';
import { ADVERTENCIA_DEL_PASO, NOMBRE_DEL_PASO, TRANSICIONES } from './transiciones.js';

/**
 * El detalle de una carrera: el centro de operaciones de todo el sistema.
 *
 * Desde aca se ve en que punto esta la carrera, quienes se anotaron y como
 * termino, y se dispara el paso al estado siguiente. Las dos pantallas de trabajo
 * pesado (gestionar inscripciones y cargar resultados) son rutas aparte, no
 * pestanas de esta: son tareas largas, con su propio formulario, y merecen su
 * propia direccion para poder volver a ellas o compartirlas.
 *
 * CADA PASO DE ESTADO PIDE CONFIRMACION
 * No porque sean destructivos todos, sino porque casi ninguno se puede deshacer:
 * una carrera que largo no vuelve a inscripciones, y una terminada no vuelve
 * nunca. El dialogo dice exactamente que se pierde en cada caso.
 */
export default function DetalleDeCarrera() {
    const { id } = useParams();
    const navegar = useNavigate();
    const { puede } = useAuth();
    const avisos = useNotificaciones();

    const [pasoPendiente, setPasoPendiente] = useState(null);
    const [trabajando, setTrabajando] = useState(false);

    const cargar = useCallback(async () => {
        const carrera = await carreras.buscarPorId(id);

        // Los resultados se piden solo si la carrera llego a un punto donde
        // pueden existir. Pedirlos siempre seria un viaje al servidor que casi
        // siempre vuelve vacio.
        const hayResultados = carrera.status === 'IN_PROGRESS' || carrera.status === 'COMPLETED';

        const [anotados, marcas] = await Promise.all([
            inscripciones.listarDeCarrera(id, { size: 50, sort: 'lane,asc' }),
            hayResultados
                ? resultados.listarDeCarrera(id, { size: 50, sort: 'finalPosition,asc' })
                : Promise.resolve(null)
        ]);

        return { carrera, anotados, marcas };
    }, [id]);

    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    const darElPaso = async () => {
        setTrabajando(true);
        try {
            if (pasoPendiente === 'CANCELLED') {
                // Cancelar tiene su propio endpoint (DELETE), que ademas deja
                // asentado en la bitacora que fue una cancelacion y no un cambio
                // de estado cualquiera.
                await carreras.cancelar(id);
                avisos.exito('La carrera quedo cancelada');
                navegar('/carreras');
                return;
            }

            await carreras.cambiarEstado(id, pasoPendiente);
            avisos.exito('La carrera avanzo de estado');
            setPasoPendiente(null);
            recargar();
        } catch (fallo) {
            // Aca cae el caso mas interesante del sistema: intentar terminar una
            // carrera a la que le faltan resultados. El backend contesta con el
            // detalle ("faltan cargar N"), y por eso se muestra su mensaje tal
            // cual en vez de uno generico.
            avisos.error(fallo.message);
            setPasoPendiente(null);
        } finally {
            setTrabajando(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Buscando la carrera" />;
    }

    if (error) {
        return (
            <div className="pagina">
                <ErrorDeCarga
                    mensaje={error.status === 404 ? 'Esta carrera no existe.' : error.message}
                    alReintentar={error.status === 404 ? undefined : recargar}
                />
                <Link to="/carreras" className="boton boton-secundario">Volver al listado</Link>
            </div>
        );
    }

    const carrera = datos.carrera;
    const pasos = TRANSICIONES[carrera.status] ?? [];
    const puedeOperar = puede('gestionarCarreras');
    const editable = carrera.status === 'DRAFT' || carrera.status === 'OPEN_FOR_REGISTRATION';

    return (
        <div className="pagina">
            <nav className="miga">
                <Link to="/carreras">Carreras</Link> / {carrera.name}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{carrera.name}</h1>
                    <p className="pagina-bajada">{carrera.description}</p>
                    <div className="fila-de-insignias">
                        <Insignia tabla={ESTADO_DE_CARRERA} valor={carrera.status} />
                        <Insignia tabla={TIPO_DE_CARRERA} valor={carrera.type} />
                        <span className="tenue">Organiza: {carrera.organizer}</span>
                    </div>
                </div>

                {puedeOperar && editable && (
                    <div className="acciones">
                        <Link to={`/carreras/${id}/editar`} className="boton boton-secundario">
                            Editar
                        </Link>
                    </div>
                )}
            </header>

            {puedeOperar && pasos.length > 0 && (
                <section className="tarjeta tarjeta-acciones">
                    <div>
                        <h2>Siguiente paso</h2>
                        <p className="nota">
                            La carrera avanza de a un estado. Cada uno habilita cosas distintas.
                        </p>
                    </div>
                    <div className="acciones">
                        {pasos.map((paso) => (
                            <button
                                key={paso}
                                type="button"
                                className={`boton ${paso === 'CANCELLED' ? 'boton-peligro' : 'boton-primario'}`}
                                onClick={() => setPasoPendiente(paso)}
                            >
                                {NOMBRE_DEL_PASO[paso]}
                            </button>
                        ))}
                    </div>
                </section>
            )}

            <div className="rejilla-dos">
                <section className="tarjeta">
                    <h2>Datos de la carrera</h2>
                    <dl className="ficha">
                        <div>
                            <dt>Largada</dt>
                            <dd>{fechaYHora(carrera.scheduledAt)}</dd>
                        </div>
                        <div>
                            <dt>Cierre de inscripciones</dt>
                            <dd>{fechaYHora(carrera.registrationDeadline)}</dd>
                        </div>
                        <div>
                            <dt>Recorrido</dt>
                            <dd>{carrera.startLocation} → {carrera.finishLocation}</dd>
                        </div>
                        <div>
                            <dt>Distancia</dt>
                            <dd>{distancia(carrera.distanceMeters)}</dd>
                        </div>
                        <div>
                            <dt>Cupo</dt>
                            <dd>{carrera.approvedCount} de {carrera.maxParticipants} lugares ocupados</dd>
                        </div>
                        <div>
                            <dt>Pendientes de aprobar</dt>
                            <dd>{carrera.pendingCount}</dd>
                        </div>
                    </dl>
                </section>

                <section className="tarjeta">
                    <div className="tarjeta-encabezado">
                        <h2>Inscriptos ({datos.anotados.totalElements})</h2>
                        {puede('gestionarInscripciones') && (
                            <Link to={`/carreras/${id}/inscripciones`} className="boton boton-secundario">
                                Gestionar
                            </Link>
                        )}
                    </div>

                    {datos.anotados.content.length === 0 ? (
                        <SinDatos
                            titulo="Nadie se anoto todavia"
                            detalle={
                                carrera.status === 'OPEN_FOR_REGISTRATION'
                                    ? 'Las inscripciones estan abiertas.'
                                    : 'Las inscripciones se habilitan cuando la carrera pasa a "inscripciones abiertas".'
                            }
                        />
                    ) : (
                        <div className="tabla-envoltura">
                            <table className="tabla">
                                <thead>
                                    <tr>
                                        <th scope="col" className="numerica">Carril</th>
                                        <th scope="col">Participante</th>
                                        <th scope="col">Estado</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {datos.anotados.content.map((inscripcion) => (
                                        <tr key={inscripcion.id}>
                                            <td className="numerica monoespaciado">
                                                {numero(inscripcion.lane)}
                                            </td>
                                            <td>{inscripcion.participantName}</td>
                                            <td>
                                                <Insignia
                                                    tabla={ESTADO_DE_INSCRIPCION}
                                                    valor={inscripcion.status}
                                                />
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            </div>

            {datos.marcas && (
                <section className="tarjeta">
                    <div className="tarjeta-encabezado">
                        <h2>Resultados ({datos.marcas.totalElements})</h2>
                        {puede('cargarResultados') && carrera.status === 'IN_PROGRESS' && (
                            <Link to={`/carreras/${id}/resultados`} className="boton boton-primario">
                                Cargar resultados
                            </Link>
                        )}
                    </div>

                    {datos.marcas.content.length === 0 ? (
                        <SinDatos
                            titulo="Todavia no hay resultados"
                            detalle="La carrera no se puede dar por terminada hasta que todos los aprobados tengan el suyo."
                        />
                    ) : (
                        <div className="tabla-envoltura">
                            <table className="tabla">
                                <thead>
                                    <tr>
                                        <th scope="col" className="numerica">Puesto</th>
                                        <th scope="col">Participante</th>
                                        <th scope="col">Estado</th>
                                        <th scope="col" className="numerica">Tiempo</th>
                                        <th scope="col" className="numerica">Penalizacion</th>
                                        <th scope="col" className="numerica">Total</th>
                                        <th scope="col" className="numerica">Puntos</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {datos.marcas.content.map((resultado) => (
                                        <tr key={resultado.id}>
                                            <td className="numerica monoespaciado">
                                                {numero(resultado.finalPosition)}
                                            </td>
                                            <td>{resultado.participantName}</td>
                                            <td>
                                                <Insignia
                                                    tabla={ESTADO_DE_RESULTADO}
                                                    valor={resultado.status}
                                                />
                                            </td>
                                            <td className="numerica monoespaciado">
                                                {tiempo(resultado.completionTimeSeconds)}
                                            </td>
                                            <td className="numerica monoespaciado">
                                                {resultado.penaltyTimeSeconds > 0
                                                    ? `+${resultado.penaltyTimeSeconds}s`
                                                    : '-'}
                                            </td>
                                            <td className="numerica monoespaciado">
                                                {tiempo(resultado.totalTimeSeconds)}
                                            </td>
                                            <td className="numerica monoespaciado">{resultado.points}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            )}

            {pasoPendiente && (
                <Dialogo
                    titulo={NOMBRE_DEL_PASO[pasoPendiente]}
                    mensaje={ADVERTENCIA_DEL_PASO[pasoPendiente] ?? 'Confirmas el cambio de estado?'}
                    textoDeConfirmacion={NOMBRE_DEL_PASO[pasoPendiente]}
                    peligroso={pasoPendiente === 'CANCELLED'}
                    trabajando={trabajando}
                    alConfirmar={darElPaso}
                    alCancelar={() => setPasoPendiente(null)}
                />
            )}
        </div>
    );
}
