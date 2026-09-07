import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { competidores } from '../../api/competidores.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import Paginacion from '../../comun/Paginacion.jsx';
import { useRecurso, useValorDemorado } from '../../comun/useRecurso.js';
import {
    ESTADO_DE_COMPETIDOR,
    TIPO_DE_COMPETIDOR,
    numero,
    opcionesDe
} from '../../comun/formato.js';

/**
 * El listado de competidores: busqueda, filtros, paginacion y detalle.
 *
 * Es la pantalla que mas veces se repite el patron en el proyecto, asi que vale
 * aclarar como esta armada, porque las de equipos y carreras siguen el mismo
 * molde.
 *
 * EL FILTRADO ES DEL SERVIDOR, NO DEL NAVEGADOR
 * Los filtros viajan como parametros y la base hace el trabajo. La tentacion es
 * pedir todo una vez y filtrar en memoria, y anda bien con los nueve competidores
 * de ejemplo; con mil, el navegador se los descarga todos para mostrar veinte.
 * Ademas seria mentir: el contador diria "1000 resultados" mientras la pantalla
 * muestra otra cosa.
 *
 * CAMBIAR UN FILTRO VUELVE A LA PRIMERA PAGINA
 * Si no, alguien parado en la pagina 4 que filtra por "lesionados" veria una
 * pantalla vacia, no porque no haya lesionados sino porque no hay cuatro paginas
 * de lesionados. Es un vacio que miente.
 */
export default function ListaDeCompetidores() {
    const { puede } = useAuth();

    const [texto, setTexto] = useState('');
    const [tipo, setTipo] = useState('');
    const [estado, setEstado] = useState('');
    const [pagina, setPagina] = useState(0);

    const textoDemorado = useValorDemorado(texto);

    const cargar = useCallback(
        () => competidores.listar({
            texto: textoDemorado,
            type: tipo,
            status: estado,
            page: pagina,
            size: 10,
            sort: 'nickname,asc'
        }),
        [textoDemorado, tipo, estado, pagina]
    );

    const { datos, cargando, error, recargar } = useRecurso(
        cargar,
        [textoDemorado, tipo, estado, pagina]
    );

    /** Todo cambio de filtro pasa por aca, para no olvidarse de volver a la 1. */
    const filtrar = (aplicar) => {
        aplicar();
        setPagina(0);
    };

    const limpiar = () => {
        setTexto('');
        setTipo('');
        setEstado('');
        setPagina(0);
    };

    const hayFiltros = texto !== '' || tipo !== '' || estado !== '';

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Competidores</h1>
                    <p className="pagina-bajada">Camellos, enanos y medianos inscriptos en la liga</p>
                </div>
                {puede('gestionarCompetidores') && (
                    <Link to="/competidores/nuevo" className="boton boton-primario">
                        Nuevo competidor
                    </Link>
                )}
            </header>

            <form className="filtros" role="search" onSubmit={(evento) => evento.preventDefault()}>
                <div className="campo campo-ancho">
                    <label htmlFor="buscador">Buscar</label>
                    <input
                        id="buscador"
                        type="search"
                        placeholder="Nombre, apodo o pais"
                        value={texto}
                        onChange={(evento) => filtrar(() => setTexto(evento.target.value))}
                    />
                </div>

                <div className="campo">
                    <label htmlFor="filtro-tipo">Tipo</label>
                    <select
                        id="filtro-tipo"
                        value={tipo}
                        onChange={(evento) => filtrar(() => setTipo(evento.target.value))}
                    >
                        <option value="">Todos</option>
                        {opcionesDe(TIPO_DE_COMPETIDOR).map((opcion) => (
                            <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                        ))}
                    </select>
                </div>

                <div className="campo">
                    <label htmlFor="filtro-estado">Estado</label>
                    <select
                        id="filtro-estado"
                        value={estado}
                        onChange={(evento) => filtrar(() => setEstado(evento.target.value))}
                    >
                        <option value="">Todos</option>
                        {opcionesDe(ESTADO_DE_COMPETIDOR).map((opcion) => (
                            <option key={opcion.valor} value={opcion.valor}>{opcion.texto}</option>
                        ))}
                    </select>
                </div>

                {hayFiltros && (
                    <button type="button" className="boton boton-secundario" onClick={limpiar}>
                        Limpiar
                    </button>
                )}
            </form>

            {cargando && <Cargando mensaje="Buscando competidores" />}

            {error && <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />}

            {datos && datos.content.length === 0 && (
                <SinDatos
                    titulo={hayFiltros ? 'Ningun competidor coincide' : 'Todavia no hay competidores'}
                    detalle={
                        hayFiltros
                            ? 'Proba con otros filtros o limpialos para ver la lista completa.'
                            : 'Cuando se den de alta competidores, van a aparecer en esta tabla.'
                    }
                >
                    {hayFiltros ? (
                        <button type="button" className="boton boton-secundario" onClick={limpiar}>
                            Limpiar los filtros
                        </button>
                    ) : (
                        puede('gestionarCompetidores') && (
                            <Link to="/competidores/nuevo" className="boton boton-primario">
                                Dar de alta el primero
                            </Link>
                        )
                    )}
                </SinDatos>
            )}

            {datos && datos.content.length > 0 && (
                <>
                    <div className="tabla-envoltura">
                        <table className="tabla">
                            <thead>
                                <tr>
                                    <th scope="col">Apodo</th>
                                    <th scope="col">Nombre</th>
                                    <th scope="col">Tipo</th>
                                    <th scope="col">Equipo</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="numerica">Victorias</th>
                                    <th scope="col" className="numerica">Carreras</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.content.map((competidor) => (
                                    <tr key={competidor.id}>
                                        <td>
                                            <Link to={`/competidores/${competidor.id}`}>
                                                {competidor.nickname}
                                            </Link>
                                        </td>
                                        <td>{competidor.name}</td>
                                        <td><Insignia tabla={TIPO_DE_COMPETIDOR} valor={competidor.type} /></td>
                                        <td>
                                            {competidor.teamId ? (
                                                <Link to={`/equipos/${competidor.teamId}`}>
                                                    {competidor.teamName}
                                                </Link>
                                            ) : (
                                                <span className="tenue">Individual</span>
                                            )}
                                        </td>
                                        <td><Insignia tabla={ESTADO_DE_COMPETIDOR} valor={competidor.status} /></td>
                                        <td className="numerica monoespaciado">{numero(competidor.victories)}</td>
                                        <td className="numerica monoespaciado">{numero(competidor.completedRaces)}</td>
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
