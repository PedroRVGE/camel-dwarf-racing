import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { carreras } from '../api/carreras.js';
import { competidores } from '../api/competidores.js';
import { equipos } from '../api/equipos.js';
import { resultados } from '../api/resultados.js';
import { useAuth } from '../auth/AuthProvider.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../comun/Estados.jsx';
import Insignia from '../comun/Insignia.jsx';
import { useRecurso } from '../comun/useRecurso.js';
import {
    ESTADO_DE_CARRERA,
    ESTADO_DE_RESULTADO,
    distancia,
    fechaYHora,
    tiempo
} from '../comun/formato.js';

/**
 * El panel principal: el resumen que pide el enunciado.
 *
 * "Main dashboard with a summary of upcoming races, active competitors and
 * recent results". Son tres cosas distintas y cada una necesita su pedido.
 *
 * POR QUE SON VARIOS PEDIDOS Y NO UNO
 * La API no tiene un endpoint de resumen, y esta bien que no lo tenga: seria un
 * endpoint hecho a medida de esta pantalla, que cambiaria cada vez que cambie el
 * diseno. Se arma combinando los listados que ya existen.
 *
 * Van todos juntos con Promise.all y no uno detras del otro. Encadenados, la
 * pantalla tardaria la suma de los cinco; en paralelo tarda lo que tarde el mas
 * lento. Es la diferencia entre un panel que aparece y uno que se va armando de a
 * pedazos.
 *
 * LOS "RESULTADOS RECIENTES" SE ARMAN EN DOS PASOS
 * No hay un listado global de resultados: los resultados cuelgan de su carrera.
 * Asi que primero se busca la ultima carrera terminada y despues se le piden sus
 * resultados. Es una consecuencia del diseno de la API, no un rodeo: un resultado
 * sin su carrera no significa nada.
 */
export default function Panel() {
    const { nombre, puede } = useAuth();

    const cargar = useCallback(async () => {
        const [abiertas, enCurso, activos, equiposActivos, terminadas] = await Promise.all([
            carreras.listar({ status: 'OPEN_FOR_REGISTRATION', size: 5, sort: 'scheduledAt,asc' }),
            carreras.listar({ status: 'IN_PROGRESS', size: 5, sort: 'scheduledAt,asc' }),
            competidores.listar({ status: 'ACTIVE', size: 1 }),
            equipos.listar({ status: 'ACTIVE', size: 1 }),
            carreras.listar({ status: 'COMPLETED', size: 1, sort: 'scheduledAt,desc' })
        ]);

        const ultima = terminadas.content[0] ?? null;
        const podio = ultima
            ? await resultados.listarDeCarrera(ultima.id, { size: 5, sort: 'finalPosition,asc' })
            : null;

        return { abiertas, enCurso, activos, equiposActivos, terminadas, ultima, podio };
    }, []);

    const { datos, cargando, error, recargar } = useRecurso(cargar);

    if (cargando) {
        return <Cargando mensaje="Armando el panel" />;
    }

    if (error) {
        return <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />;
    }

    const proximas = [...datos.enCurso.content, ...datos.abiertas.content];

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Hola, {nombre}</h1>
                    <p className="pagina-bajada">Esto es lo que esta pasando en la liga</p>
                </div>
                {puede('gestionarCarreras') && (
                    <Link to="/carreras/nueva" className="boton boton-primario">
                        Nueva carrera
                    </Link>
                )}
            </header>

            <section className="tarjetas-numero">
                <Numero titulo="Inscripciones abiertas" valor={datos.abiertas.totalElements} enlace="/carreras" />
                <Numero titulo="Carreras en curso" valor={datos.enCurso.totalElements} enlace="/carreras" />
                <Numero titulo="Competidores activos" valor={datos.activos.totalElements} enlace="/competidores" />
                <Numero titulo="Equipos activos" valor={datos.equiposActivos.totalElements} enlace="/equipos" />
            </section>

            <div className="rejilla-dos">
                <section className="tarjeta">
                    <div className="tarjeta-encabezado">
                        <h2>Proximas carreras</h2>
                        <Link to="/carreras" className="enlace-discreto">Ver todas</Link>
                    </div>

                    {proximas.length === 0 ? (
                        <SinDatos
                            titulo="No hay carreras a la vista"
                            detalle="Cuando alguien abra las inscripciones de una carrera, va a aparecer aca."
                        >
                            {puede('gestionarCarreras') && (
                                <Link to="/carreras/nueva" className="boton boton-primario">
                                    Crear la primera
                                </Link>
                            )}
                        </SinDatos>
                    ) : (
                        <ul className="lista-tarjetas">
                            {proximas.map((carrera) => (
                                <li key={carrera.id}>
                                    <Link to={`/carreras/${carrera.id}`} className="fila-enlazada">
                                        <div>
                                            <strong>{carrera.name}</strong>
                                            <span className="fila-detalle">
                                                {fechaYHora(carrera.scheduledAt)} · {distancia(carrera.distanceMeters)}
                                                {' · '}
                                                {carrera.approvedCount}/{carrera.maxParticipants} inscriptos
                                            </span>
                                        </div>
                                        <Insignia tabla={ESTADO_DE_CARRERA} valor={carrera.status} />
                                    </Link>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section className="tarjeta">
                    <div className="tarjeta-encabezado">
                        <h2>Ultimos resultados</h2>
                        <Link to="/posiciones" className="enlace-discreto">Ver posiciones</Link>
                    </div>

                    {!datos.ultima ? (
                        <SinDatos
                            titulo="Todavia no termino ninguna carrera"
                            detalle="Los resultados aparecen cuando una carrera pasa a terminada."
                        />
                    ) : (
                        <>
                            <p className="fila-detalle">
                                <Link to={`/carreras/${datos.ultima.id}`}>{datos.ultima.name}</Link>
                                {' · '}
                                {fechaYHora(datos.ultima.scheduledAt)}
                            </p>
                            <table className="tabla">
                                <thead>
                                    <tr>
                                        <th scope="col">Puesto</th>
                                        <th scope="col">Participante</th>
                                        <th scope="col">Tiempo</th>
                                        <th scope="col">Puntos</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {datos.podio.content.map((resultado) => (
                                        <tr key={resultado.id}>
                                            <td>
                                                {resultado.finalPosition ?? (
                                                    <Insignia tabla={ESTADO_DE_RESULTADO} valor={resultado.status} />
                                                )}
                                            </td>
                                            <td>{resultado.participantName}</td>
                                            <td className="monoespaciado">{tiempo(resultado.totalTimeSeconds)}</td>
                                            <td className="monoespaciado">{resultado.points}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </>
                    )}
                </section>
            </div>
        </div>
    );
}

/** Una de las tarjetas de numero grande de arriba. */
function Numero({ titulo, valor, enlace }) {
    return (
        <Link to={enlace} className="tarjeta-numero">
            <span className="tarjeta-numero-valor">{valor}</span>
            <span className="tarjeta-numero-titulo">{titulo}</span>
        </Link>
    );
}
