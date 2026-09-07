import { useCallback, useState } from 'react';
import { auditoria } from '../api/auditoria.js';
import { Cargando, ErrorDeCarga, SinDatos } from '../comun/Estados.jsx';
import Paginacion from '../comun/Paginacion.jsx';
import { ACCION_DE_AUDITORIA, fechaYHora } from '../comun/formato.js';
import { useRecurso, useValorDemorado } from '../comun/useRecurso.js';

/**
 * La bitacora de auditoria: quien hizo que y cuando.
 *
 * Es la unica pantalla exclusiva del administrador. Es de solo lectura, y tiene
 * que serlo: un registro que se puede editar desde la misma aplicacion que
 * registra no sirve como registro de nada.
 *
 * LOS FILTROS DE FECHA SON DE DIA, PERO LA API PIDE FECHA Y HORA
 * Nadie quiere escribir la hora para filtrar un dia. El campo es de fecha y esta
 * pantalla completa las horas: el "desde" arranca a las 00:00:00 y el "hasta"
 * termina a las 23:59:59, porque los dos limites son inclusive. Sin ese detalle,
 * filtrar "hasta hoy" dejaria afuera todo lo que paso hoy despues de medianoche,
 * que es basicamente todo lo de hoy.
 *
 * QUE SE VE Y QUE NO
 * Se muestra el valor anterior y el nuevo, que es lo que convierte a la bitacora
 * en algo util: no solo que alguien cambio un estado, sino de que a que. No se
 * muestra el identificador interno de la entidad, salvo acortado: es ruido para
 * el que lee y el enunciado pide no exponerlos sin necesidad.
 */
export default function Bitacora() {
    const [usuario, setUsuario] = useState('');
    const [accion, setAccion] = useState('');
    const [desde, setDesde] = useState('');
    const [hasta, setHasta] = useState('');
    const [pagina, setPagina] = useState(0);

    const usuarioDemorado = useValorDemorado(usuario);

    const cargar = useCallback(
        () => auditoria.listar({
            username: usuarioDemorado,
            action: accion,
            desde: desde ? `${desde}T00:00:00` : '',
            hasta: hasta ? `${hasta}T23:59:59` : '',
            page: pagina,
            size: 20,
            sort: 'occurredAt,desc'
        }),
        [usuarioDemorado, accion, desde, hasta, pagina]
    );

    const { datos, cargando, error, recargar } = useRecurso(
        cargar,
        [usuarioDemorado, accion, desde, hasta, pagina]
    );

    const hayFiltros = usuario !== '' || accion !== '' || desde !== '' || hasta !== '';

    const limpiar = () => {
        setUsuario('');
        setAccion('');
        setDesde('');
        setHasta('');
        setPagina(0);
    };

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Bitacora</h1>
                    <p className="pagina-bajada">
                        Todo lo que se modifico en el sistema, con nombre y hora
                    </p>
                </div>
            </header>

            <form className="filtros" onSubmit={(evento) => evento.preventDefault()}>
                <div className="campo">
                    <label htmlFor="filtro-usuario">Usuario</label>
                    <input
                        id="filtro-usuario"
                        type="search"
                        placeholder="admin, organizer..."
                        value={usuario}
                        onChange={(evento) => { setUsuario(evento.target.value); setPagina(0); }}
                    />
                </div>

                <div className="campo campo-ancho">
                    <label htmlFor="filtro-accion">Accion</label>
                    <select
                        id="filtro-accion"
                        value={accion}
                        onChange={(evento) => { setAccion(evento.target.value); setPagina(0); }}
                    >
                        <option value="">Todas</option>
                        {Object.entries(ACCION_DE_AUDITORIA).map(([valor, texto]) => (
                            <option key={valor} value={valor}>{texto}</option>
                        ))}
                    </select>
                </div>

                <div className="campo">
                    <label htmlFor="filtro-desde">Desde</label>
                    <input
                        id="filtro-desde"
                        type="date"
                        value={desde}
                        onChange={(evento) => { setDesde(evento.target.value); setPagina(0); }}
                    />
                </div>

                <div className="campo">
                    <label htmlFor="filtro-hasta">Hasta</label>
                    <input
                        id="filtro-hasta"
                        type="date"
                        value={hasta}
                        onChange={(evento) => { setHasta(evento.target.value); setPagina(0); }}
                    />
                </div>

                {hayFiltros && (
                    <button type="button" className="boton boton-secundario" onClick={limpiar}>
                        Limpiar
                    </button>
                )}
            </form>

            {cargando && <Cargando mensaje="Buscando en la bitacora" />}
            {error && <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />}

            {datos && datos.content.length === 0 && (
                <SinDatos
                    titulo={hayFiltros ? 'Nada coincide con esos filtros' : 'La bitacora esta vacia'}
                    detalle={
                        hayFiltros
                            ? 'Proba con otro rango de fechas o con otra accion.'
                            : 'Se van a registrar aca todos los cambios que haga cualquier usuario.'
                    }
                />
            )}

            {datos && datos.content.length > 0 && (
                <>
                    <div className="tabla-envoltura">
                        <table className="tabla">
                            <thead>
                                <tr>
                                    <th scope="col">Cuando</th>
                                    <th scope="col">Quien</th>
                                    <th scope="col">Que hizo</th>
                                    <th scope="col">Sobre que</th>
                                    <th scope="col">Detalle</th>
                                </tr>
                            </thead>
                            <tbody>
                                {datos.content.map((registro) => (
                                    <tr key={registro.id}>
                                        <td className="monoespaciado">{fechaYHora(registro.occurredAt)}</td>
                                        <td>{registro.username}</td>
                                        <td>{ACCION_DE_AUDITORIA[registro.action] ?? registro.action}</td>
                                        <td>{registro.entityType}</td>
                                        <td>
                                            <span>{registro.description}</span>
                                            {(registro.previousValue || registro.newValue) && (
                                                <span className="cambio">
                                                    {registro.previousValue && (
                                                        <code className="cambio-antes">
                                                            {registro.previousValue}
                                                        </code>
                                                    )}
                                                    {registro.previousValue && registro.newValue && ' → '}
                                                    {registro.newValue && (
                                                        <code className="cambio-despues">
                                                            {registro.newValue}
                                                        </code>
                                                    )}
                                                </span>
                                            )}
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
