import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider.jsx';
import { Cargando } from '../comun/Estados.jsx';

/**
 * La pantalla de entrada.
 *
 * POR QUE NO HAY CAMPOS DE USUARIO Y CONTRASENA
 * Porque la contrasena no es asunto de esta aplicacion. Al apretar el boton, el
 * navegador se va al formulario de Keycloak, escribe ahi sus datos y vuelve con
 * un token. Este codigo nunca ve la contrasena, asi que no puede perderla,
 * registrarla ni mandarla a ningun lado por error.
 *
 * Es el mismo modelo que usa el backend, que tampoco tiene /api/auth/login: los
 * usuarios viven en Keycloak y la aplicacion solo verifica tokens.
 *
 * ACA TAMPOCO SE MUESTRAN CREDENCIALES DE PRUEBA
 * Es tentador poner "admin / admin123" en la pantalla para la demostracion, y es
 * exactamente lo que el enunciado prohibe cuando dice que la interfaz no debe
 * exponer contrasenas. Los usuarios de prueba estan documentados en
 * keycloak/README.md, que es donde corresponde.
 */
export default function Entrar() {
    const { listo, autenticado, entrar } = useAuth();
    const ubicacion = useLocation();

    if (!listo) {
        return (
            <div className="portada">
                <Cargando mensaje="Buscando una sesion abierta" />
            </div>
        );
    }

    // Si ya hay sesion, esta pantalla no tiene sentido: lo devuelve a donde
    // queria ir antes de que lo mandaran aca, o al panel si vino directo.
    if (autenticado) {
        return <Navigate to={ubicacion.state?.destino ?? '/'} replace />;
    }

    return (
        <div className="portada">
            <div className="portada-tarjeta">
                <span className="portada-icono" aria-hidden="true">🐪</span>
                <h1>Liga EIA de Carreras</h1>
                <p className="portada-bajada">
                    Camellos contra enanos desde hace cuarenta y siete anos, ahora sin
                    la planilla de calculo.
                </p>

                <button type="button" className="boton boton-primario boton-grande" onClick={entrar}>
                    Entrar
                </button>

                <p className="portada-nota">
                    La identificacion la maneja Keycloak. Te va a pedir tu usuario y tu
                    contrasena en su propia pantalla y despues te trae de vuelta.
                </p>
            </div>
        </div>
    );
}
