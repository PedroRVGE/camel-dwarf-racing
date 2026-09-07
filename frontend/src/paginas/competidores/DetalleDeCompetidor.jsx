import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { competidores } from '../../api/competidores.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import Dialogo from '../../comun/Dialogo.jsx';
import { Cargando, ErrorDeCarga } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { useRecurso } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_COMPETIDOR,
    TIPO_DE_COMPETIDOR,
    opcionesDe,
    soloFecha,
    textoDe
} from '../../comun/formato.js';

/**
 * La ficha de un competidor, con sus estadisticas y sus acciones.
 *
 * EL CAMBIO DE ESTADO ES UN DESPLEGABLE Y LA BAJA ES UN BOTON APARTE
 * Aunque las dos cosas terminan cambiando el mismo campo, son operaciones
 * distintas para el que las usa: marcar a alguien como lesionado es rutina y se
 * deshace, y retirarlo es el final de su carrera deportiva. Mezclarlas en el
 * mismo control haria que retirar a alguien sea tan facil como equivocarse de
 * linea en una lista.
 *
 * Ademas la baja pide confirmacion, porque el enunciado la exige para las
 * acciones destructivas, y el texto del dialogo aclara que el historial se
 * conserva: la baja es logica, el competidor pasa a RETIRED y sus resultados
 * siguen contando en la tabla de posiciones.
 */
export default function DetalleDeCompetidor() {
    const { id } = useParams();
    const navegar = useNavigate();
    const { puede } = useAuth();
    const avisos = useNotificaciones();

    const [confirmandoBaja, setConfirmandoBaja] = useState(false);
    const [trabajando, setTrabajando] = useState(false);

    const cargar = useCallback(() => competidores.buscarPorId(id), [id]);
    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    const cambiarEstado = async (estado) => {
        setTrabajando(true);
        try {
            await competidores.cambiarEstado(id, estado);
            avisos.exito(`Ahora figura como ${textoDe(ESTADO_DE_COMPETIDOR, estado).toLowerCase()}`);
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const retirar = async () => {
        setTrabajando(true);
        try {
            await competidores.retirar(id);
            avisos.exito('El competidor quedo retirado');
            // Se vuelve a la lista: quedarse en una ficha que acaba de cambiar de
            // sentido no le sirve a nadie, y la lista es de donde vino.
            navegar('/competidores');
        } catch (fallo) {
            avisos.error(fallo.message);
            setTrabajando(false);
            setConfirmandoBaja(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Buscando al competidor" />;
    }

    if (error) {
        // Un 404 aca no es una ruta inexistente sino un competidor que no esta, y
        // por eso se resuelve dentro de la seccion en vez de mandar a la pantalla
        // general de "no encontrado": asi no se pierde la navegacion.
        return (
            <div className="pagina">
                <ErrorDeCarga
                    mensaje={
                        error.status === 404
                            ? 'Este competidor no existe o fue dado de baja.'
                            : error.message
                    }
                    alReintentar={error.status === 404 ? undefined : recargar}
                />
                <Link to="/competidores" className="boton boton-secundario">
                    Volver al listado
                </Link>
            </div>
        );
    }

    return (
        <div className="pagina">
            <nav className="miga">
                <Link to="/competidores">Competidores</Link> / {datos.nickname}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{datos.nickname}</h1>
                    <p className="pagina-bajada">{datos.name}</p>
                    <div className="fila-de-insignias">
                        <Insignia tabla={TIPO_DE_COMPETIDOR} valor={datos.type} />
                        <Insignia tabla={ESTADO_DE_COMPETIDOR} valor={datos.status} />
                    </div>
                </div>

                {puede('gestionarCompetidores') && (
                    <div className="acciones">
                        <Link to={`/competidores/${id}/editar`} className="boton boton-secundario">
                            Editar
                        </Link>
                        {datos.status !== 'RETIRED' && (
                            <button
                                type="button"
                                className="boton boton-peligro"
                                onClick={() => setConfirmandoBaja(true)}
                            >
                                Retirar
                            </button>
                        )}
                    </div>
                )}
            </header>

            <div className="rejilla-dos">
                <section className="tarjeta">
                    <h2>Datos</h2>
                    <dl className="ficha">
                        <div>
                            <dt>Fecha de nacimiento</dt>
                            <dd>{soloFecha(datos.dateOfBirth)} ({datos.age} anos)</dd>
                        </div>
                        <div>
                            <dt>Peso</dt>
                            <dd>{datos.weightKg} kg</dd>
                        </div>
                        <div>
                            <dt>Altura</dt>
                            <dd>{datos.heightCm} cm</dd>
                        </div>
                        <div>
                            <dt>Pais</dt>
                            <dd>{datos.countryOfOrigin}</dd>
                        </div>
                        <div>
                            <dt>Equipo</dt>
                            <dd>
                                {datos.teamId ? (
                                    <Link to={`/equipos/${datos.teamId}`}>{datos.teamName}</Link>
                                ) : (
                                    <span className="tenue">Compite individualmente</span>
                                )}
                            </dd>
                        </div>
                        <div>
                            <dt>En la liga desde</dt>
                            <dd>{soloFecha(datos.registeredAt)}</dd>
                        </div>
                    </dl>
                </section>

                <section className="tarjeta">
                    <h2>Estadisticas</h2>
                    <div className="tarjetas-numero tarjetas-numero-chicas">
                        <div className="tarjeta-numero">
                            <span className="tarjeta-numero-valor">{datos.victories}</span>
                            <span className="tarjeta-numero-titulo">Victorias</span>
                        </div>
                        <div className="tarjeta-numero">
                            <span className="tarjeta-numero-valor">{datos.defeats}</span>
                            <span className="tarjeta-numero-titulo">Derrotas</span>
                        </div>
                        <div className="tarjeta-numero">
                            <span className="tarjeta-numero-valor">{datos.completedRaces}</span>
                            <span className="tarjeta-numero-titulo">Carreras terminadas</span>
                        </div>
                    </div>
                    <p className="nota">
                        Las lleva el sistema: se recalculan solas cada vez que se carga o se
                        corrige un resultado.
                    </p>

                    {puede('gestionarCompetidores') && datos.status !== 'RETIRED' && (
                        <div className="campo">
                            <label htmlFor="cambiar-estado">Cambiar el estado</label>
                            <select
                                id="cambiar-estado"
                                value={datos.status}
                                disabled={trabajando}
                                onChange={(evento) => cambiarEstado(evento.target.value)}
                            >
                                {opcionesDe(ESTADO_DE_COMPETIDOR)
                                    // "Retirado" no esta en la lista a proposito: para eso
                                    // esta el boton de retirar, que pide confirmacion.
                                    .filter((opcion) => opcion.valor !== 'RETIRED')
                                    .map((opcion) => (
                                        <option key={opcion.valor} value={opcion.valor}>
                                            {opcion.texto}
                                        </option>
                                    ))}
                            </select>
                        </div>
                    )}
                </section>
            </div>

            {confirmandoBaja && (
                <Dialogo
                    titulo={`Retirar a ${datos.nickname}`}
                    mensaje="Va a quedar marcado como retirado y no va a poder inscribirse en nuevas carreras. Sus resultados anteriores se conservan y siguen contando en la tabla de posiciones."
                    textoDeConfirmacion="Retirar"
                    peligroso
                    trabajando={trabajando}
                    alConfirmar={retirar}
                    alCancelar={() => setConfirmandoBaja(false)}
                />
            )}
        </div>
    );
}
