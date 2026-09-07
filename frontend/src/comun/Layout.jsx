import { NavLink, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider.jsx';
import { NOMBRE_DE_ROL } from '../auth/permisos.js';

/**
 * El marco que rodea a todas las pantallas: la barra de arriba y la navegacion.
 *
 * Que exista uno solo es lo que hace que la aplicacion se vea como un producto y
 * no como varias paginas sueltas, que es literalmente lo que el enunciado pide
 * que no pase. La navegacion siempre esta en el mismo lugar, marca donde estas
 * parado y ofrece las mismas salidas desde cualquier pantalla.
 *
 * LA BITACORA APARECE Y DESAPARECE
 * El enlace a la bitacora solo se dibuja para el administrador. No es que este
 * deshabilitado: no esta. Ofrecerle a un espectador un enlace que lo va a llevar
 * a una pantalla de acceso denegado es peor que no ofrecerselo, porque le hace
 * creer que la aplicacion tiene una seccion rota.
 */
export default function Layout() {
    const { nombre, roles, puede, salir } = useAuth();

    // El rol mas alto es el que se muestra en la barra. El realm le da al
    // administrador los tres roles a la vez (admin, organizer y viewer), asi que
    // mostrarlos todos diria "Administrador, Organizador, Espectador", que es
    // cierto pero no le sirve a nadie.
    const rolPrincipal = ['admin', 'organizer', 'viewer'].find((rol) => roles.includes(rol));

    return (
        <div className="marco">
            <header className="barra">
                <Link to="/" className="marca">
                    <span className="marca-icono" aria-hidden="true">🐪</span>
                    <span>
                        <strong>Liga EIA</strong>
                        <small>Camellos contra enanos</small>
                    </span>
                </Link>

                <nav className="navegacion" aria-label="Secciones">
                    <NavLink to="/" end>Panel</NavLink>
                    <NavLink to="/carreras">Carreras</NavLink>
                    <NavLink to="/competidores">Competidores</NavLink>
                    <NavLink to="/equipos">Equipos</NavLink>
                    <NavLink to="/posiciones">Posiciones</NavLink>
                    {puede('verAuditoria') && <NavLink to="/bitacora">Bitacora</NavLink>}
                </nav>

                <div className="sesion">
                    <Link to="/perfil" className="sesion-usuario">
                        <span className="sesion-nombre">{nombre}</span>
                        <span className="sesion-rol">{NOMBRE_DE_ROL[rolPrincipal] ?? 'Sin rol'}</span>
                    </Link>
                    <button type="button" className="boton boton-secundario" onClick={salir}>
                        Salir
                    </button>
                </div>
            </header>

            <main className="contenido">
                {/* Aca dibuja react-router la pantalla que corresponda a la ruta. */}
                <Outlet />
            </main>

            <footer className="pie">
                Universidad EIA · Implementacion de Software · Sistema de gestion de carreras
            </footer>
        </div>
    );
}
