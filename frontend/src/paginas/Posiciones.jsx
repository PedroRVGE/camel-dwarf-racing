import { useCallback, useState } from 'react';
import { posiciones } from '../api/posiciones.js';
import { Cargando, ErrorDeCarga, SinDatos } from '../comun/Estados.jsx';
import Paginacion from '../comun/Paginacion.jsx';
import { useRecurso } from '../comun/useRecurso.js';

/**
 * La tabla de posiciones de la liga.
 *
 * Son dos clasificaciones sobre las mismas carreras, y se eligen con los botones
 * de arriba. No estan en dos pantallas distintas porque se consultan juntas: la
 * pregunta natural es "quien va ganando", y la respuesta depende de si se habla
 * de individuales o de equipos.
 *
 * EL PUESTO LO CALCULA EL SERVIDOR, NO ESTA PANTALLA
 * Podria numerarse contando filas, y andaria bien en la primera pagina. En la
 * segunda, la fila once volveria a decir "1". El campo "position" viene resuelto
 * desde el backend justamente por eso.
 *
 * QUIENES APARECEN
 * Todos los que compitieron, incluidos los que sumaron cero. Aparecer con cero
 * puntos no es lo mismo que no aparecer: significa que corrio y no puntuo, y esa
 * diferencia se pierde si la tabla filtra a los que no sumaron.
 */
export default function Posiciones() {
    const [tabla, setTabla] = useState('competidores');
    const [pagina, setPagina] = useState(0);

    const cargar = useCallback(
        () => (tabla === 'competidores'
            ? posiciones.competidores({ page: pagina, size: 20 })
            : posiciones.equipos({ page: pagina, size: 20 })),
        [tabla, pagina]
    );

    const { datos, cargando, error, recargar } = useRecurso(cargar, [tabla, pagina]);

    const cambiarTabla = (cual) => {
        setTabla(cual);
        // Se vuelve a la primera pagina: la pagina 3 de una tabla no tiene nada
        // que ver con la pagina 3 de la otra.
        setPagina(0);
    };

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Tabla de posiciones</h1>
                    <p className="pagina-bajada">
                        Diez puntos al primero, siete al segundo, cinco al tercero, tres al
                        cuarto y uno al quinto
                    </p>
                </div>
            </header>

            <div className="pestanas" role="tablist" aria-label="Clasificacion">
                <button
                    type="button"
                    role="tab"
                    aria-selected={tabla === 'competidores'}
                    className={`pestana ${tabla === 'competidores' ? 'pestana-activa' : ''}`}
                    onClick={() => cambiarTabla('competidores')}
                >
                    Competidores
                </button>
                <button
                    type="button"
                    role="tab"
                    aria-selected={tabla === 'equipos'}
                    className={`pestana ${tabla === 'equipos' ? 'pestana-activa' : ''}`}
                    onClick={() => cambiarTabla('equipos')}
                >
                    Equipos
                </button>
            </div>

            {cargando && <Cargando mensaje="Calculando posiciones" />}
            {error && <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />}

            {datos && datos.content.length === 0 && (
                <SinDatos
                    titulo="La tabla esta vacia"
                    detalle="Aparecen aca los participantes de carreras que ya tengan resultados cargados."
                />
            )}

            {datos && datos.content.length > 0 && (
                <>
                    <div className="tabla-envoltura">
                        <table className="tabla tabla-posiciones">
                            <thead>
                                <tr>
                                    <th scope="col" className="numerica">#</th>
                                    <th scope="col">{tabla === 'equipos' ? 'Equipo' : 'Competidor'}</th>
                                    <th scope="col" className="numerica">Puntos</th>
                                    <th scope="col" className="numerica">Carreras</th>
                                    <th scope="col" className="numerica">Victorias</th>
                                    <th scope="col" className="numerica">Derrotas</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.content.map((fila) => (
                                    <tr
                                        key={fila.participantId}
                                        className={fila.position <= 3 ? 'fila-podio' : ''}
                                    >
                                        <td className="numerica monoespaciado">{fila.position}</td>
                                        <td>{fila.participantName}</td>
                                        <td className="numerica monoespaciado destacado">{fila.points}</td>
                                        <td className="numerica monoespaciado">{fila.racesFinished}</td>
                                        <td className="numerica monoespaciado">{fila.victories}</td>
                                        <td className="numerica monoespaciado">{fila.defeats}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <Paginacion pagina={datos} alCambiarPagina={setPagina} />

                    <p className="nota">
                        El que no largo no suma derrota: no compitio, asi que no perdio. El
                        descalificado y el que abandono si.
                    </p>
                </>
            )}
        </div>
    );
}
