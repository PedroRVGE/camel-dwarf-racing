import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { carreras } from '../../api/carreras.js';
import { competidores } from '../../api/competidores.js';
import { equipos } from '../../api/equipos.js';
import { inscripciones } from '../../api/inscripciones.js';
import Dialogo from '../../comun/Dialogo.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import { CampoDeArea, ErrorDelFormulario } from '../../comun/Formulario.jsx';
import Insignia from '../../comun/Insignia.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { useRecurso } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_CARRERA,
    ESTADO_DE_INSCRIPCION,
    numero,
    opcionesDe
} from '../../comun/formato.js';

/**
 * La pantalla de gestion de inscripciones de una carrera.
 *
 * Es una de las que el enunciado nombra por su nombre. Hace tres cosas: anotar
 * participantes, aprobarlos o rechazarlos, y dar de baja inscripciones.
 *
 * SE ANOTA UN COMPETIDOR O UN EQUIPO, NUNCA LOS DOS
 * El backend lo valida con un @AssertTrue ("hay que mandar competitorId o teamId,
 * y solo uno de los dos"). La interfaz lo resuelve antes, con un par de botones
 * que cambian el desplegable: no hay forma de armar un pedido invalido desde
 * aca, y el usuario no tiene que enterarse de que existe esa regla.
 *
 * EL CARRIL ES OPCIONAL AL ANOTARSE
 * Si no se elige, el backend le asigna uno libre al aprobar. Por eso el campo
 * dice "opcional" en vez de estar vacio y obligatorio: la mayoria de las veces no
 * hace falta pensarlo, y cuando hace falta (un pedido del participante, una
 * cuestion de organizacion) se puede.
 */
export default function GestionDeInscripciones() {
    const { id } = useParams();
    const avisos = useNotificaciones();

    const [filtro, setFiltro] = useState('');
    const [modo, setModo] = useState('competidor');
    const [participante, setParticipante] = useState('');
    const [carril, setCarril] = useState('');
    const [errorDelAlta, setErrorDelAlta] = useState(null);
    const [trabajando, setTrabajando] = useState(false);

    const [aRechazar, setARechazar] = useState(null);
    const [motivo, setMotivo] = useState('');
    const [aCancelar, setACancelar] = useState(null);

    const cargar = useCallback(async () => {
        const [carrera, anotados, listaDeCompetidores, listaDeEquipos] = await Promise.all([
            carreras.buscarPorId(id),
            inscripciones.listarDeCarrera(id, { status: filtro, size: 50, sort: 'registeredAt,asc' }),
            competidores.listar({ status: 'ACTIVE', size: 100, sort: 'nickname,asc' }),
            equipos.listar({ status: 'ACTIVE', size: 100, sort: 'name,asc' })
        ]);
        return { carrera, anotados, listaDeCompetidores, listaDeEquipos };
    }, [id, filtro]);

    const { datos, cargando, error, recargar } = useRecurso(cargar, [id, filtro]);

    const anotar = async (evento) => {
        evento.preventDefault();
        setTrabajando(true);
        setErrorDelAlta(null);

        const cuerpo = {
            competitorId: modo === 'competidor' ? participante : null,
            teamId: modo === 'equipo' ? participante : null,
            lane: carril === '' ? null : Number(carril)
        };

        try {
            await inscripciones.inscribir(id, cuerpo);
            avisos.exito('Inscripcion registrada, queda pendiente de aprobacion');
            setParticipante('');
            setCarril('');
            recargar();
        } catch (fallo) {
            // Aca aparecen las reglas mas visibles del sistema: competidor
            // suspendido, inscripcion repetida, cupo lleno, plazo vencido. El
            // mensaje del backend explica cual de las cuatro fue, asi que se
            // muestra tal cual.
            setErrorDelAlta(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const aprobar = async (inscripcion) => {
        setTrabajando(true);
        try {
            const aprobada = await inscripciones.aprobar(inscripcion.id);
            avisos.exito(`${aprobada.participantName} corre en el carril ${aprobada.lane}`);
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const rechazar = async () => {
        setTrabajando(true);
        try {
            await inscripciones.rechazar(aRechazar.id, motivo);
            avisos.exito('Inscripcion rechazada');
            setARechazar(null);
            setMotivo('');
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    const cancelar = async () => {
        setTrabajando(true);
        try {
            await inscripciones.cancelar(aCancelar.id);
            avisos.exito('Inscripcion cancelada');
            setACancelar(null);
            recargar();
        } catch (fallo) {
            avisos.error(fallo.message);
        } finally {
            setTrabajando(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Cargando las inscripciones" />;
    }

    if (error) {
        return (
            <div className="pagina">
                <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />
                <Link to="/carreras" className="boton boton-secundario">Volver a las carreras</Link>
            </div>
        );
    }

    const carrera = datos.carrera;
    const abierta = carrera.status === 'OPEN_FOR_REGISTRATION';

    const opcionesDeParticipante = modo === 'competidor'
        ? datos.listaDeCompetidores.content.map((c) => ({ valor: c.id, texto: c.nickname }))
        : datos.listaDeEquipos.content.map((e) => ({ valor: e.id, texto: e.name }));

    return (
        <div className="pagina">
            <nav className="miga">
                <Link to="/carreras">Carreras</Link> /{' '}
                <Link to={`/carreras/${id}`}>{carrera.name}</Link> / Inscripciones
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>Inscripciones</h1>
                    <p className="pagina-bajada">
                        {carrera.name} · {carrera.approvedCount} de {carrera.maxParticipants} lugares
                        ocupados · {carrera.pendingCount} por revisar
                    </p>
                    <div className="fila-de-insignias">
                        <Insignia tabla={ESTADO_DE_CARRERA} valor={carrera.status} />
                    </div>
                </div>
            </header>

            {abierta ? (
                <section className="tarjeta">
                    <h2>Anotar un participante</h2>

                    <form className="formulario" onSubmit={anotar}>
                        <ErrorDelFormulario mensaje={errorDelAlta} />

                        <div className="selector-de-modo" role="group" aria-label="Que se anota">
                            <button
                                type="button"
                                className={`boton ${modo === 'competidor' ? 'boton-primario' : 'boton-secundario'}`}
                                onClick={() => { setModo('competidor'); setParticipante(''); }}
                            >
                                Un competidor
                            </button>
                            <button
                                type="button"
                                className={`boton ${modo === 'equipo' ? 'boton-primario' : 'boton-secundario'}`}
                                onClick={() => { setModo('equipo'); setParticipante(''); }}
                            >
                                Un equipo
                            </button>
                        </div>

                        <div className="formulario-fila">
                            <div className="campo campo-ancho">
                                <label htmlFor="participante">
                                    {modo === 'competidor' ? 'Competidor' : 'Equipo'}
                                </label>
                                <select
                                    id="participante"
                                    value={participante}
                                    onChange={(evento) => setParticipante(evento.target.value)}
                                    required
                                >
                                    <option value="">Elegi uno</option>
                                    {opcionesDeParticipante.map((opcion) => (
                                        <option key={opcion.valor} value={opcion.valor}>
                                            {opcion.texto}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="campo">
                                <label htmlFor="carril">Carril</label>
                                <input
                                    id="carril"
                                    type="number"
                                    min="1"
                                    max={carrera.maxParticipants}
                                    value={carril}
                                    onChange={(evento) => setCarril(evento.target.value)}
                                    placeholder="Opcional"
                                />
                                <p className="campo-ayuda">Si lo dejas vacio, se asigna al aprobar</p>
                            </div>

                            <button
                                type="submit"
                                className="boton boton-primario"
                                disabled={trabajando || participante === ''}
                            >
                                Anotar
                            </button>
                        </div>
                    </form>
                </section>
            ) : (
                <p className="aviso aviso-neutro">
                    Las inscripciones estan cerradas. Solo se puede anotar gente mientras la
                    carrera esta en "inscripciones abiertas".
                </p>
            )}

            <section className="tarjeta">
                <div className="tarjeta-encabezado">
                    <h2>Anotados ({datos.anotados.totalElements})</h2>
                    <div className="campo">
                        <label htmlFor="filtro-estado">Estado</label>
                        <select
                            id="filtro-estado"
                            value={filtro}
                            onChange={(evento) => setFiltro(evento.target.value)}
                        >
                            <option value="">Todos</option>
                            {opcionesDe(ESTADO_DE_INSCRIPCION).map((opcion) => (
                                <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                            ))}
                        </select>
                    </div>
                </div>

                {datos.anotados.content.length === 0 ? (
                    <SinDatos
                        titulo={filtro ? 'Ninguna inscripcion en ese estado' : 'Todavia no se anoto nadie'}
                        detalle={
                            filtro
                                ? 'Proba con otro estado o mira todas.'
                                : 'Las inscripciones que se registren van a aparecer en esta tabla.'
                        }
                    />
                ) : (
                    <div className="tabla-envoltura">
                        <table className="tabla">
                            <thead>
                                <tr>
                                    <th scope="col">Participante</th>
                                    <th scope="col" className="numerica">Carril</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col">Anotado por</th>
                                    <th scope="col">Observaciones</th>
                                    <th scope="col"><span className="oculto">Acciones</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.anotados.content.map((inscripcion) => (
                                    <tr key={inscripcion.id}>
                                        <td>
                                            {inscripcion.competitorId ? (
                                                <Link to={`/competidores/${inscripcion.competitorId}`}>
                                                    {inscripcion.participantName}
                                                </Link>
                                            ) : (
                                                <Link to={`/equipos/${inscripcion.teamId}`}>
                                                    {inscripcion.participantName}
                                                </Link>
                                            )}
                                        </td>
                                        <td className="numerica monoespaciado">{numero(inscripcion.lane)}</td>
                                        <td>
                                            <Insignia
                                                tabla={ESTADO_DE_INSCRIPCION}
                                                valor={inscripcion.status}
                                            />
                                        </td>
                                        <td className="tenue">{inscripcion.registeredBy}</td>
                                        <td className="tenue">{inscripcion.validationNotes ?? '-'}</td>
                                        <td className="acciones-de-fila">
                                            {inscripcion.status === 'PENDING' && (
                                                <>
                                                    <button
                                                        type="button"
                                                        className="boton boton-texto"
                                                        onClick={() => aprobar(inscripcion)}
                                                        disabled={trabajando}
                                                    >
                                                        Aprobar
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="boton boton-texto boton-texto-peligro"
                                                        onClick={() => setARechazar(inscripcion)}
                                                        disabled={trabajando}
                                                    >
                                                        Rechazar
                                                    </button>
                                                </>
                                            )}
                                            {inscripcion.status === 'APPROVED' && (
                                                <button
                                                    type="button"
                                                    className="boton boton-texto boton-texto-peligro"
                                                    onClick={() => setACancelar(inscripcion)}
                                                    disabled={trabajando}
                                                >
                                                    Dar de baja
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            {aRechazar && (
                <Dialogo
                    titulo={`Rechazar a ${aRechazar.participantName}`}
                    mensaje="El motivo queda guardado en la inscripcion y en la bitacora, asi que conviene que se entienda solo."
                    textoDeConfirmacion="Rechazar"
                    peligroso
                    trabajando={trabajando}
                    confirmacionHabilitada={motivo.trim() !== ''}
                    alConfirmar={rechazar}
                    alCancelar={() => { setARechazar(null); setMotivo(''); }}
                >
                    <CampoDeArea
                        etiqueta="Motivo del rechazo"
                        nombre="motivo"
                        valor={motivo}
                        alCambiar={setMotivo}
                        requerido
                        maxLength={500}
                        filas={3}
                    />
                </Dialogo>
            )}

            {aCancelar && (
                <Dialogo
                    titulo={`Dar de baja a ${aCancelar.participantName}`}
                    mensaje="La inscripcion queda cancelada y su lugar vuelve a estar disponible para otro participante."
                    textoDeConfirmacion="Dar de baja"
                    peligroso
                    trabajando={trabajando}
                    alConfirmar={cancelar}
                    alCancelar={() => setACancelar(null)}
                />
            )}
        </div>
    );
}
