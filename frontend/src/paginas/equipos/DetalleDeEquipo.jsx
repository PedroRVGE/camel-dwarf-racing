import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { competidores } from '../../api/competidores.js';
import { equipos } from '../../api/equipos.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import Dialogo from '../../comun/Dialogo.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { useRecurso } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_COMPETIDOR,
    ESTADO_DE_EQUIPO,
    TIPO_DE_COMPETIDOR,
    opcionesDe,
    soloFecha
} from '../../comun/formato.js';

/**
 * El detalle de un equipo y la administracion de sus integrantes.
 *
 * El enunciado pide esta pantalla por su nombre: "team list, team detail and
 * member-management screen". Las tres cosas estan aca porque administrar
 * integrantes sin ver el equipo no tiene sentido.
 *
 * EL DESPLEGABLE DE "AGREGAR INTEGRANTE" YA VIENE FILTRADO
 * Solo trae competidores ACTIVOS y sin equipo. Los que ya tienen equipo no
 * aparecen porque un competidor pertenece a uno solo: si apareciera y alguien lo
 * eligiera, la API lo rechazaria, y es mejor no ofrecer una opcion imposible que
 * explicar despues por que no se puede.
 */
export default function DetalleDeEquipo() {
    const { id } = useParams();
    const navegar = useNavigate();
    const { puede } = useAuth();
    const avisos = useNotificaciones();

    const [aAgregar, setAAgregar] = useState('');
    const [trabajando, setTrabajando] = useState(false);
    const [confirmandoBaja, setConfirmandoBaja] = useState(false);
    const [aQuitar, setAQuitar] = useState(null);

    const puedeGestionar = puede('gestionarEquipos');

    const cargar = useCallback(async () => {
        const [equipo, disponibles] = await Promise.all([
            equipos.buscarPorId(id),
            // Solo hace falta la lista de candidatos si se van a poder agregar.
            puedeGestionar
                ? competidores.listar({ status: 'ACTIVE', size: 100, sort: 'nickname,asc' })
                : Promise.resolve({ content: [] })
        ]);
        return { equipo, disponibles };
    }, [id, puedeGestionar]);

    const { datos, cargando, error, recargar } = useRecurso(cargar, [id, puedeGestionar]);

    const agregar = async () => {
        if (!aAgregar) {
            return;
        }
        setTrabajando(true);
        try {
            await equipos.agregarIntegrante(id, aAgregar);
            avisos.exito('Integrante agregado al equipo');
            setAAgregar('');
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const quitar = async () => {
        setTrabajando(true);
        try {
            await equipos.quitarIntegrante(id, aQuitar.id);
            avisos.exito(`${aQuitar.nickname} ya no forma parte del equipo`);
            setAQuitar(null);
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const desactivar = async () => {
        setTrabajando(true);
        try {
            await equipos.desactivar(id);
            avisos.exito('El equipo quedo desactivado');
            navegar('/equipos');
        } catch (fallo) {
            avisos.error(fallo.message);
            setTrabajando(false);
            setConfirmandoBaja(false);
        }
    };

    const cambiarEstado = async (estado) => {
        setTrabajando(true);
        try {
            await equipos.cambiarEstado(id, estado);
            avisos.exito('Estado del equipo actualizado');
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Buscando el equipo" />;
    }

    if (error) {
        return (
            <div className="pagina">
                <ErrorDeCarga
                    mensaje={error.status === 404 ? 'Este equipo no existe.' : error.message}
                    alReintentar={error.status === 404 ? undefined : recargar}
                />
                <Link to="/equipos" className="boton boton-secundario">Volver al listado</Link>
            </div>
        );
    }

    const equipo = datos.equipo;
    const yaEstan = new Set(equipo.members.map((integrante) => integrante.id));
    const candidatos = datos.disponibles.content
        .filter((competidor) => !competidor.teamId && !yaEstan.has(competidor.id))
        .map((competidor) => ({ valor: competidor.id, texto: competidor.nickname }));

    return (
        <div className="pagina">
            <nav className="miga">
                <Link to="/equipos">Equipos</Link> / {equipo.name}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{equipo.name}</h1>
                    <p className="pagina-bajada">{equipo.description}</p>
                    <div className="fila-de-insignias">
                        <Insignia tabla={ESTADO_DE_EQUIPO} valor={equipo.status} />
                        <span className="tenue">Responsable: {equipo.coach}</span>
                    </div>
                </div>

                {puedeGestionar && (
                    <div className="acciones">
                        <Link to={`/equipos/${id}/editar`} className="boton boton-secundario">Editar</Link>
                        {equipo.status !== 'INACTIVE' && (
                            <button
                                type="button"
                                className="boton boton-peligro"
                                onClick={() => setConfirmandoBaja(true)}
                            >
                                Desactivar
                            </button>
                        )}
                    </div>
                )}
            </header>

            <div className="rejilla-dos">
                <section className="tarjeta">
                    <div className="tarjeta-encabezado">
                        <h2>Integrantes ({equipo.memberCount})</h2>
                    </div>

                    {equipo.members.length === 0 ? (
                        <SinDatos
                            titulo="El equipo no tiene integrantes"
                            detalle="Un equipo sin integrantes no puede inscribirse en una carrera."
                        />
                    ) : (
                        <div className="tabla-envoltura">
                            <table className="tabla">
                                <thead>
                                    <tr>
                                        <th scope="col">Apodo</th>
                                        <th scope="col">Tipo</th>
                                        <th scope="col">Estado</th>
                                        {puedeGestionar && <th scope="col"><span className="oculto">Acciones</span></th>}
                                    </tr>
                                </thead>
                                <tbody>
                                    {equipo.members.map((integrante) => (
                                        <tr key={integrante.id}>
                                            <td>
                                                <Link to={`/competidores/${integrante.id}`}>
                                                    {integrante.nickname}
                                                </Link>
                                            </td>
                                            <td><Insignia tabla={TIPO_DE_COMPETIDOR} valor={integrante.type} /></td>
                                            <td><Insignia tabla={ESTADO_DE_COMPETIDOR} valor={integrante.status} /></td>
                                            {puedeGestionar && (
                                                <td className="numerica">
                                                    <button
                                                        type="button"
                                                        className="boton boton-texto boton-texto-peligro"
                                                        onClick={() => setAQuitar(integrante)}
                                                    >
                                                        Quitar
                                                    </button>
                                                </td>
                                            )}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {puedeGestionar && (
                        <div className="agregar-integrante">
                            <div className="campo campo-ancho">
                                <label htmlFor="nuevo-integrante">Agregar un integrante</label>
                                <select
                                    id="nuevo-integrante"
                                    value={aAgregar}
                                    onChange={(evento) => setAAgregar(evento.target.value)}
                                    disabled={trabajando || candidatos.length === 0}
                                >
                                    <option value="">
                                        {candidatos.length === 0
                                            ? 'No hay competidores libres'
                                            : 'Elegi un competidor'}
                                    </option>
                                    {candidatos.map((candidato) => (
                                        <option key={candidato.valor} value={candidato.valor}>
                                            {candidato.texto}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <button
                                type="button"
                                className="boton boton-primario"
                                onClick={agregar}
                                disabled={trabajando || aAgregar === ''}
                            >
                                Agregar
                            </button>
                        </div>
                    )}
                </section>

                <section className="tarjeta">
                    <h2>Rendimiento</h2>
                    <div className="tarjetas-numero tarjetas-numero-chicas">
                        <div className="tarjeta-numero">
                            <span className="tarjeta-numero-valor">{equipo.victories}</span>
                            <span className="tarjeta-numero-titulo">Victorias</span>
                        </div>
                        <div className="tarjeta-numero">
                            <span className="tarjeta-numero-valor">{equipo.defeats}</span>
                            <span className="tarjeta-numero-titulo">Derrotas</span>
                        </div>
                    </div>

                    <dl className="ficha">
                        <div>
                            <dt>Creado</dt>
                            <dd>{soloFecha(equipo.createdAt)}</dd>
                        </div>
                    </dl>

                    {puedeGestionar && equipo.status !== 'INACTIVE' && (
                        <div className="campo">
                            <label htmlFor="estado-del-equipo">Cambiar el estado</label>
                            <select
                                id="estado-del-equipo"
                                value={equipo.status}
                                disabled={trabajando}
                                onChange={(evento) => cambiarEstado(evento.target.value)}
                            >
                                {opcionesDe(ESTADO_DE_EQUIPO)
                                    .filter((opcion) => opcion.valor !== 'INACTIVE')
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
                    titulo={`Desactivar ${equipo.name}`}
                    mensaje="El equipo deja de poder inscribirse en carreras. Sus integrantes y sus resultados anteriores se conservan."
                    textoDeConfirmacion="Desactivar"
                    peligroso
                    trabajando={trabajando}
                    alConfirmar={desactivar}
                    alCancelar={() => setConfirmandoBaja(false)}
                />
            )}

            {aQuitar && (
                <Dialogo
                    titulo={`Quitar a ${aQuitar.nickname}`}
                    mensaje="Deja de formar parte del equipo y pasa a competir individualmente. No se borra ni pierde su historial."
                    textoDeConfirmacion="Quitar del equipo"
                    peligroso
                    trabajando={trabajando}
                    alConfirmar={quitar}
                    alCancelar={() => setAQuitar(null)}
                />
            )}
        </div>
    );
}
