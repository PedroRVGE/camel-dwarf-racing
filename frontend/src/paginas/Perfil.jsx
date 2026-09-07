import { useCallback } from 'react';
import { perfil as apiDePerfil } from '../api/auditoria.js';
import { useAuth } from '../auth/AuthProvider.jsx';
import { NOMBRE_DE_ROL } from '../auth/permisos.js';
import { Cargando, ErrorDeCarga } from '../comun/Estados.jsx';
import { useRecurso } from '../comun/useRecurso.js';

/**
 * El perfil del usuario y la salida de la sesion.
 *
 * POR QUE PREGUNTA A LA API SI EL TOKEN YA TRAE ESTOS DATOS
 * Es una decision consciente y vale la pena explicarla. El token efectivamente
 * trae el nombre, el correo y los roles, y la barra de arriba los usa de ahi
 * porque los necesita al instante para decidir que dibujar.
 *
 * Esta pantalla, en cambio, muestra lo que la API RECONOCE de vos, que es lo
 * unico que importa a la hora de operar: si el backend leyera los roles de otra
 * manera, aca se veria la diferencia. Es la forma de comprobar, sin abrir
 * herramientas de desarrollador, que el token llega y que se interpreta bien del
 * otro lado.
 */
export default function Perfil() {
    const { salir } = useAuth();

    const cargar = useCallback(() => apiDePerfil.consultar(), []);
    const { datos, cargando, error, recargar } = useRecurso(cargar);

    if (cargando) {
        return <Cargando mensaje="Cargando tu perfil" />;
    }

    if (error) {
        return <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />;
    }

    return (
        <div className="pagina">
            <header className="pagina-encabezado">
                <div>
                    <h1>Tu perfil</h1>
                    <p className="pagina-bajada">Los datos que la API reconoce de tu sesion</p>
                </div>
                <button type="button" className="boton boton-secundario" onClick={salir}>
                    Cerrar sesion
                </button>
            </header>

            <div className="tarjeta">
                <dl className="ficha">
                    <div>
                        <dt>Usuario</dt>
                        <dd>{datos.username}</dd>
                    </div>
                    <div>
                        <dt>Nombre</dt>
                        <dd>{[datos.firstName, datos.lastName].filter(Boolean).join(' ') || '-'}</dd>
                    </div>
                    <div>
                        <dt>Correo</dt>
                        <dd>{datos.email || '-'}</dd>
                    </div>
                    <div>
                        <dt>Roles</dt>
                        <dd>
                            <div className="fila-de-insignias">
                                {datos.roles.map((rol) => (
                                    <span key={rol} className="insignia insignia-indigo">
                                        {NOMBRE_DE_ROL[rol] ?? rol}
                                    </span>
                                ))}
                            </div>
                        </dd>
                    </div>
                </dl>
            </div>

            <div className="tarjeta tarjeta-tenue">
                <h2>Que puede hacer cada rol</h2>
                <ul className="lista-explicativa">
                    <li>
                        <strong>Administrador:</strong> todo. Ademas es el unico que da de alta
                        competidores y equipos, y el unico que ve la bitacora.
                    </li>
                    <li>
                        <strong>Organizador de carreras:</strong> crea y opera carreras, aprueba
                        inscripciones y carga resultados.
                    </li>
                    <li>
                        <strong>Espectador:</strong> consulta todo lo publico de la liga, sin
                        modificar nada.
                    </li>
                </ul>
            </div>
        </div>
    );
}
