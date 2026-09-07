import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { carreras } from '../../api/carreras.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import Paginacion from '../../comun/Paginacion.jsx';
import {
    ESTADO_DE_CARRERA,
    TIPO_DE_CARRERA,
    distancia,
    fechaYHora,
    opcionesDe
} from '../../comun/formato.js';
import { useRecurso, useValorDemorado } from '../../comun/useRecurso.js';

/**
 * El listado de carreras.
 *
 * Se ordena por fecha ascendente, que es el orden que se espera de una agenda: lo
 * que viene primero, primero. El desplegable de ordenamiento permite darlo vuelta
 * para mirar el historial, que es la otra forma en que se consulta esta pantalla.
 */
export default function ListaDeCarreras() {
    const { puede } = useAuth();

    const [texto, setTexto] = useState('');
    const [estado, setEstado] = useState('');
    const [tipo, setTipo] = useState('');
    const [orden, setOrden] = useState('scheduledAt,asc');
    const [pagina, setPagina] = useState(0);

    const textoDemorado = useValorDemorado(texto);

    const cargar = useCallback(
        () => carreras.listar({
            texto: textoDemorado,
            status: estado,
            type: tipo,
            sort: orden,
            page: pagina,
            size: 10
        }),
        [textoDemorado, estado, tipo, orden, pagina]
    );

    const { datos, cargando, error, recargar } = useRecurso(
        cargar,
        [textoDemorado, estado, tipo, orden, pagina]
    );

    const hayFiltros = texto !== '' || estado !== '' || tipo !== '';

    const limpiar = () => {
        setTexto('');
        setEstado('');
        setTipo('');
        setPagina(0);
    };

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Carreras</h1>
                    <p className="pagina-bajada">La agenda completa de la liga</p>
                </div>
                {puede('gestionarCarreras') && (
                    <Link to="/carreras/nueva" className="boton boton-primario">Nueva carrera</Link>
                )}
            </header>

            <form className="filtros" role="search" onSubmit={(evento) => evento.preventDefault()}>
                <div className="campo campo-ancho">
                    <label htmlFor="buscador">Buscar</label>
                    <input
                        id="buscador"
                        type="search"
                        placeholder="Nombre o lugar"
                        value={texto}
                        onChange={(evento) => { setTexto(evento.target.value); setPagina(0); }}
                    />
                </div>

                <div className="campo">
                    <label htmlFor="filtro-estado">Estado</label>
                    <select
                        id="filtro-estado"
                        value={estado}
                        onChange={(evento) => { setEstado(evento.target.value); setPagina(0); }}
                    >
                        <option value="">Todos</option>
                        {opcionesDe(ESTADO_DE_CARRERA).map((opcion) => (
                            <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                        ))}
                    </select>
                </div>

                <div className="campo">
                    <label htmlFor="filtro-tipo">Tipo</label>
                    <select
                        id="filtro-tipo"
                        value={tipo}
                        onChange={(evento) => { setTipo(evento.target.value); setPagina(0); }}
                    >
                        <option value="">Todos</option>
                        {opcionesDe(TIPO_DE_CARRERA).map((opcion) => (
                            <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                        ))}
                    </select>
                </div>

                <div className="campo">
                    <label htmlFor="orden">Orden</label>
                    <select
                        id="orden"
                        value={orden}
                        onChange={(evento) => { setOrden(evento.target.value); setPagina(0); }}
                    >
                        <option value="scheduledAt,asc">Proximas primero</option>
                        <option value="scheduledAt,desc">Ultimas primero</option>
                        <option value="name,asc">Por nombre</option>
                    </select>
                </div>

                {hayFiltros && (
                    <button type="button" className="boton boton-secundario" onClick={limpiar}>
                        Limpiar
                    </button>
                )}
            </form>

            {cargando && <Cargando mensaje="Buscando carreras" />}
            {error && <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />}

            {datos && datos.content.length === 0 && (
                <SinDatos
                    titulo={hayFiltros ? 'Ninguna carrera coincide' : 'La agenda esta vacia'}
                    detalle={
                        hayFiltros
                            ? 'Proba con otros filtros o limpialos para ver todo.'
                            : 'Toda carrera nace en borrador y se abre a inscripciones cuando esta lista.'
                    }
                >
                    {!hayFiltros && puede('gestionarCarreras') && (
                        <Link to="/carreras/nueva" className="boton boton-primario">
                            Programar la primera
                        </Link>
                    )}
                </SinDatos>
            )}

            {datos && datos.content.length > 0 && (
                <>
                    <div className="tabla-envoltura">
                        <table className="tabla">
                            <thead>
                                <tr>
                                    <th scope="col">Carrera</th>
                                    <th scope="col">Fecha</th>
                                    <th scope="col">Tipo</th>
                                    <th scope="col">Distancia</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="numerica">Inscriptos</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.content.map((carrera) => (
                                    <tr key={carrera.id}>
                                        <td><Link to={`/carreras/${carrera.id}`}>{carrera.name}</Link></td>
                                        <td>{fechaYHora(carrera.scheduledAt)}</td>
                                        <td><Insignia tabla={TIPO_DE_CARRERA} valor={carrera.type} /></td>
                                        <td className="monoespaciado">{distancia(carrera.distanceMeters)}</td>
                                        <td><Insignia tabla={ESTADO_DE_CARRERA} valor={carrera.status} /></td>
                                        <td className="numerica monoespaciado">
                                            {carrera.approvedCount}/{carrera.maxParticipants}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <Paginacion pagina={datos} alCambiarPagina={setPagina} />
                </>
            )}
        </div>
    );
}
