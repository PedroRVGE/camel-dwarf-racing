import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { carreras } from '../../api/carreras.js';
import { inscripciones } from '../../api/inscripciones.js';
import { resultados } from '../../api/resultados.js';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import {
    CampoDeArea,
    CampoDeSeleccion,
    CampoDeTexto,
    ErrorDelFormulario
} from '../../comun/Formulario.jsx';
import Insignia from '../../comun/Insignia.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { repartirErrores } from '../../comun/errores.js';
import { useRecurso } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_CARRERA,
    ESTADO_DE_RESULTADO,
    numero,
    opcionesDe,
    tiempo
} from '../../comun/formato.js';

/**
 * La pantalla de carga de resultados.
 *
 * LO IMPORTANTE ES LO QUE MUESTRA, NO LO QUE CARGA
 * Cargar un resultado es un formulario mas. Lo que hace util a esta pantalla es
 * la lista de QUIENES FALTAN: sin ella, el organizador tiene que acordarse de a
 * quien ya cargo, y la carrera no se puede dar por terminada hasta que no falte
 * ninguno. Esa lista sale de cruzar las inscripciones aprobadas con los
 * resultados que ya existen.
 *
 * EL FORMULARIO CAMBIA SEGUN EL ESTADO ELEGIDO
 * Un participante que TERMINO necesita tiempo y puesto. Uno que abandono, fue
 * descalificado o no largo, no: no tiene ni uno ni otro, y el backend lo rechaza
 * si se los mandan. Por eso esos dos campos aparecen y desaparecen en vez de
 * quedar siempre visibles y fallar despues.
 *
 * LAS REGLAS FINAS SIGUEN SIENDO DEL BACKEND
 * Que no haya dos primeros puestos, que el tiempo sea coherente con la posicion,
 * que la carrera este en curso: todo eso lo valida el servidor, y sus mensajes se
 * muestran tal cual porque explican exactamente cual regla se violo.
 */
const CAMPOS = [
    'registrationId', 'status', 'completionTimeSeconds',
    'penaltyTimeSeconds', 'finalPosition', 'notes'
];

const VACIO = {
    registrationId: '',
    status: 'FINISHED',
    completionTimeSeconds: '',
    penaltyTimeSeconds: '',
    finalPosition: '',
    notes: ''
};

export default function CargaDeResultados() {
    const { id } = useParams();
    const avisos = useNotificaciones();

    const [formulario, setFormulario] = useState(VACIO);
    const [erroresDeCampo, setErroresDeCampo] = useState({});
    const [errorGeneral, setErrorGeneral] = useState(null);
    const [guardando, setGuardando] = useState(false);

    const cargar = useCallback(async () => {
        const [carrera, anotados, marcas] = await Promise.all([
            carreras.buscarPorId(id),
            inscripciones.listarDeCarrera(id, { status: 'APPROVED', size: 50, sort: 'lane,asc' }),
            resultados.listarDeCarrera(id, { size: 50, sort: 'finalPosition,asc' })
        ]);
        return { carrera, anotados, marcas };
    }, [id]);

    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    const cambiar = (campo) => (valor) => {
        setFormulario((actual) => ({ ...actual, [campo]: valor }));
        setErroresDeCampo((actuales) => ({ ...actuales, [campo]: undefined }));
        setErrorGeneral(null);
    };

    const enviar = async (evento) => {
        evento.preventDefault();
        setGuardando(true);
        setErroresDeCampo({});
        setErrorGeneral(null);

        const termino = formulario.status === 'FINISHED';

        const cuerpo = {
            registrationId: formulario.registrationId,
            status: formulario.status,
            // Los que no terminaron van con null explicito: mandar una cadena
            // vacia o un cero seria decir que hicieron un tiempo de cero segundos.
            completionTimeSeconds: termino && formulario.completionTimeSeconds !== ''
                ? Number(formulario.completionTimeSeconds)
                : null,
            finalPosition: termino && formulario.finalPosition !== ''
                ? Number(formulario.finalPosition)
                : null,
            penaltyTimeSeconds: formulario.penaltyTimeSeconds === ''
                ? null
                : Number(formulario.penaltyTimeSeconds),
            notes: formulario.notes === '' ? null : formulario.notes
        };

        try {
            const cargado = await resultados.cargar(id, cuerpo);
            avisos.exito(`Resultado de ${cargado.participantName} cargado (${cargado.points} puntos)`);
            setFormulario(VACIO);
            recargar();
        } catch (fallo) {
            const { porCampo, general } = repartirErrores(fallo, CAMPOS);
            setErroresDeCampo(porCampo);
            setErrorGeneral(general);
        } finally {
            setGuardando(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Cargando la carrera" />;
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
    const enCurso = carrera.status === 'IN_PROGRESS';

    // Quienes ya tienen resultado, para no ofrecerlos de nuevo.
    const yaCargados = new Set(datos.marcas.content.map((marca) => marca.registrationId));
    const faltan = datos.anotados.content.filter((anotado) => !yaCargados.has(anotado.id));

    const termino = formulario.status === 'FINISHED';

    return (
        <div className="pagina">
            <nav className="miga">
                <Link to="/carreras">Carreras</Link> /{' '}
                <Link to={`/carreras/${id}`}>{carrera.name}</Link> / Resultados
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>Carga de resultados</h1>
                    <p className="pagina-bajada">
                        {carrera.name} · {datos.marcas.totalElements} cargados · {faltan.length} pendientes
                    </p>
                    <div className="fila-de-insignias">
                        <Insignia tabla={ESTADO_DE_CARRERA} valor={carrera.status} />
                    </div>
                </div>
            </header>

            {!enCurso && (
                <p className="aviso aviso-neutro">
                    Los resultados solo se cargan con la carrera en curso. Esta carrera esta en
                    otro estado, asi que la tabla de abajo es de consulta.
                </p>
            )}

            {enCurso && faltan.length === 0 && (
                <p className="aviso aviso-exito">
                    Todos los participantes aprobados tienen su resultado. Ya se puede dar la
                    carrera por terminada desde su detalle.
                </p>
            )}

            {enCurso && faltan.length > 0 && (
                <section className="tarjeta">
                    <h2>Cargar un resultado</h2>

                    <form className="formulario" onSubmit={enviar}>
                        <ErrorDelFormulario mensaje={errorGeneral} />

                        <div className="formulario-fila">
                            <CampoDeSeleccion
                                etiqueta="Participante"
                                nombre="registrationId"
                                valor={formulario.registrationId}
                                alCambiar={cambiar('registrationId')}
                                opciones={faltan.map((anotado) => ({
                                    valor: anotado.id,
                                    texto: `Carril ${anotado.lane ?? '-'} · ${anotado.participantName}`
                                }))}
                                vacio="Elegi a quien cargarle el resultado"
                                error={erroresDeCampo.registrationId}
                                requerido
                                required
                            />
                            <CampoDeSeleccion
                                etiqueta="Como termino"
                                nombre="status"
                                valor={formulario.status}
                                alCambiar={cambiar('status')}
                                opciones={opcionesDe(ESTADO_DE_RESULTADO)}
                                vacio={null}
                                error={erroresDeCampo.status}
                                requerido
                                required
                            />
                        </div>

                        {termino && (
                            <div className="formulario-fila">
                                <CampoDeTexto
                                    etiqueta="Puesto"
                                    nombre="finalPosition"
                                    tipo="number"
                                    min="1"
                                    valor={formulario.finalPosition}
                                    alCambiar={cambiar('finalPosition')}
                                    error={erroresDeCampo.finalPosition}
                                    ayuda="Del 1 al 5 suman puntos: 10, 7, 5, 3 y 1"
                                    requerido
                                    required
                                />
                                <CampoDeTexto
                                    etiqueta="Tiempo (segundos)"
                                    nombre="completionTimeSeconds"
                                    tipo="number"
                                    min="1"
                                    valor={formulario.completionTimeSeconds}
                                    alCambiar={cambiar('completionTimeSeconds')}
                                    error={erroresDeCampo.completionTimeSeconds}
                                    requerido
                                    required
                                />
                                <CampoDeTexto
                                    etiqueta="Penalizacion (segundos)"
                                    nombre="penaltyTimeSeconds"
                                    tipo="number"
                                    min="0"
                                    valor={formulario.penaltyTimeSeconds}
                                    alCambiar={cambiar('penaltyTimeSeconds')}
                                    error={erroresDeCampo.penaltyTimeSeconds}
                                    ayuda="Se suma al tiempo. Vacio es sin penalizacion"
                                />
                            </div>
                        )}

                        <CampoDeArea
                            etiqueta="Observaciones"
                            nombre="notes"
                            valor={formulario.notes}
                            alCambiar={cambiar('notes')}
                            error={erroresDeCampo.notes}
                            ayuda="Opcional. Hasta 500 caracteres"
                            maxLength={500}
                            filas={2}
                        />

                        <div className="formulario-botones">
                            <button type="submit" className="boton boton-primario" disabled={guardando}>
                                {guardando ? 'Cargando...' : 'Cargar el resultado'}
                            </button>
                        </div>
                    </form>
                </section>
            )}

            {enCurso && faltan.length > 0 && (
                <section className="tarjeta tarjeta-tenue">
                    <h2>Faltan cargar ({faltan.length})</h2>
                    <ul className="lista-simple">
                        {faltan.map((anotado) => (
                            <li key={anotado.id}>
                                <span className="monoespaciado">Carril {numero(anotado.lane)}</span>
                                {' · '}
                                {anotado.participantName}
                            </li>
                        ))}
                    </ul>
                    <p className="nota">
                        Mientras quede alguno en esta lista, la carrera no se puede dar por
                        terminada: la API lo rechaza y dice cuantos faltan.
                    </p>
                </section>
            )}

            <section className="tarjeta">
                <h2>Resultados cargados ({datos.marcas.totalElements})</h2>

                {datos.marcas.content.length === 0 ? (
                    <SinDatos
                        titulo="Todavia no se cargo ninguno"
                        detalle="Los resultados que cargues van a aparecer en esta tabla."
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
                                    <th scope="col" className="numerica">Total</th>
                                    <th scope="col" className="numerica">Puntos</th>
                                    <th scope="col">Cargado por</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.marcas.content.map((marca) => (
                                    <tr key={marca.id}>
                                        <td className="numerica monoespaciado">{numero(marca.finalPosition)}</td>
                                        <td>{marca.participantName}</td>
                                        <td><Insignia tabla={ESTADO_DE_RESULTADO} valor={marca.status} /></td>
                                        <td className="numerica monoespaciado">
                                            {tiempo(marca.completionTimeSeconds)}
                                        </td>
                                        <td className="numerica monoespaciado">
                                            {tiempo(marca.totalTimeSeconds)}
                                        </td>
                                        <td className="numerica monoespaciado">{marca.points}</td>
                                        <td className="tenue">{marca.recordedBy}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}
