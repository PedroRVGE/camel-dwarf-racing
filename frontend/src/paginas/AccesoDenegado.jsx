import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider.jsx';
import { NOMBRE_DE_ROL } from '../auth/permisos.js';

/**
 * La pantalla de acceso denegado: el 403 de la interfaz.
 *
 * Dice con que rol entraste. Es el dato que convierte un cartel inutil en uno
 * util: sin el, el usuario no sabe si se equivoco de seccion, si le falta un
 * permiso o si el sistema esta roto. Con el, entiende que tiene que pedirle al
 * administrador y a quien.
 *
 * Y no ofrece "volver a entrar", que es el error clasico de confundir esto con la
 * pantalla de sesion vencida. Loguearse de nuevo con el mismo usuario da el mismo
 * resultado; lo unico que cambia algo es que le den otro rol.
 */
export default function AccesoDenegado() {
    const { roles } = useAuth();

    const legibles = roles
        .map((rol) => NOMBRE_DE_ROL[rol])
        .filter(Boolean)
        .join(', ');

    return (
        <div className="pagina-mensaje">
            <span className="pagina-mensaje-codigo">403</span>
            <h1>Esta seccion no es para tu rol</h1>
            <p>
                Entraste como <strong>{legibles || 'un usuario sin roles asignados'}</strong>, y
                esta pantalla pide permisos que ese rol no tiene.
            </p>
            <p className="pagina-mensaje-detalle">
                Si necesitas acceso, pediselo a un administrador de la liga.
            </p>
            <Link to="/" className="boton boton-primario">Volver al panel</Link>
        </div>
    );
}
