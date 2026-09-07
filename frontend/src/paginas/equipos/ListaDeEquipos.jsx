import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { equipos } from '../../api/equipos.js';
import { useAuth } from '../../auth/AuthProvider.jsx';
import { Cargando, ErrorDeCarga, SinDatos } from '../../comun/Estados.jsx';
import Insignia from '../../comun/Insignia.jsx';
import Paginacion from '../../comun/Paginacion.jsx';
import { ESTADO_DE_EQUIPO, opcionesDe } from '../../comun/formato.js';
import { useRecurso, useValorDemorado } from '../../comun/useRecurso.js';

/** El listado de equipos, con el mismo molde que el de competidores. */
export default function ListaDeEquipos() {
    const { puede } = useAuth();

    const [texto, setTexto] = useState('');
    const [estado, setEstado] = useState('');
    const [pagina, setPagina] = useState(0);

    const textoDemorado = useValorDemorado(texto);

    const cargar = useCallback(
        () => equipos.listar({
            texto: textoDemorado,
            status: estado,
            page: pagina,
            size: 10,
            sort: 'name,asc'
        }),
        [textoDemorado, estado, pagina]
    );

    const { datos, cargando, error, recargar } = useRecurso(cargar, [textoDemorado, estado, pagina]);

    const hayFiltros = texto !== '' || estado !== '';

    const limpiar = () => {
        setTexto('');
        setEstado('');
        setPagina(0);
    };

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Equipos</h1>
                    <p className="pagina-bajada">
                        Los que corren juntos: la victoria es del equipo, no de sus integrantes
                    </p>
                </div>
                {puede('gestionarEquipos') && (
                    <Link to="/equipos/nuevo" className="boton boton-primario">Nuevo equipo</Link>
                )}
            </header>

            <form className="filtros" role="search" onSubmit={(evento) => evento.preventDefault()}>
                <div className="campo campo-ancho">
                    <label htmlFor="buscador">Buscar</label>
                    <input
                        id="buscador"
                        type="search"
                        placeholder="Nombre del equipo o responsable"
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
                        {opcionesDe(ESTADO_DE_EQUIPO).map((opcion) => (
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

            {cargando && <Cargando mensaje="Buscando equipos" />}
            {error && <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />}

            {datos && datos.content.length === 0 && (
                <SinDatos
                    titulo={hayFiltros ? 'Ningun equipo coincide' : 'Todavia no hay equipos'}
                    detalle={
                        hayFiltros
                            ? 'Proba con otro nombre o limpia los filtros.'
                            : 'Un equipo agrupa competidores para correr como uno solo.'
                    }
                >
                    {!hayFiltros && puede('gestionarEquipos') && (
                        <Link to="/equipos/nuevo" className="boton boton-primario">Crear el primero</Link>
                    )}
                </SinDatos>
            )}

            {datos && datos.content.length > 0 && (
                <>
                    <div className="tabla-envoltura">
                        <table className="tabla">
                            <thead>
                                <tr>
                                    <th scope="col">Equipo</th>
                                    <th scope="col">Responsable</th>
                                    <th scope="col">Estado</th>
                                    <th scope="col" className="numerica">Integrantes</th>
                                    <th scope="col" className="numerica">Victorias</th>
                                    <th scope="col" className="numerica">Derrotas</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.content.map((equipo) => (
                                    <tr key={equipo.id}>
                                        <td><Link to={`/equipos/${equipo.id}`}>{equipo.name}</Link></td>
                                        <td>{equipo.coach}</td>
                                        <td><Insignia tabla={ESTADO_DE_EQUIPO} valor={equipo.status} /></td>
                                        <td className="numerica monoespaciado">{equipo.memberCount}</td>
                                        <td className="numerica monoespaciado">{equipo.victories}</td>
                                        <td className="numerica monoespaciado">{equipo.defeats}</td>
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
