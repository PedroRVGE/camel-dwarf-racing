import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider.jsx';
import { Cargando } from '../comun/Estados.jsx';

/**
 * El portero de las rutas.
 *
 * El enunciado lo pide como "route protection: prevents unauthorized users from
 * opening restricted pages". Distingue TRES situaciones que se parecen y no son
 * lo mismo, igual que las distingue la API con sus codigos:
 *
 *   1. Todavia no sabemos si hay sesion  -> indicador de carga.
 *      Es el caso que mas se olvida, y el que peor se ve cuando falta: sin el, al
 *      recargar la pagina el usuario ve pasar la pantalla de bienvenida antes de
 *      volver a su lugar.
 *
 *   2. No hay sesion                     -> a la pantalla de entrada (como un 401).
 *      Se guarda a donde queria ir para devolverlo ahi despues de entrar. Sin
 *      eso, alguien que abre un enlace a una carrera termina en el panel general
 *      y tiene que buscarla de nuevo.
 *
 *   3. Hay sesion pero le falta el rol   -> acceso denegado (como un 403).
 *      NO se lo manda a loguearse: ya esta logueado. Mandarlo seria el error
 *      clasico de confundir 401 con 403, y deja al usuario dando vueltas
 *      escribiendo bien su contrasena una y otra vez sin que nada cambie.
 */
export default function RutaProtegida({ accion, children }) {
    const { listo, autenticado, puede } = useAuth();
    const ubicacion = useLocation();

    if (!listo) {
        return <Cargando mensaje="Verificando la sesion" />;
    }

    if (!autenticado) {
        return <Navigate to="/entrar" replace state={{ destino: ubicacion.pathname }} />;
    }

    if (accion && !puede(accion)) {
        return <Navigate to="/acceso-denegado" replace />;
    }

    return children;
}
