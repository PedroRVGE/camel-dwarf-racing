import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { ProveedorDeNotificaciones } from './comun/Notificaciones.jsx';
import './estilos.css';

/**
 * El arranque de la aplicacion.
 *
 * El orden de las envolturas importa, y no es alfabetico:
 *
 *   BrowserRouter  va afuera de todo porque adentro se usan enlaces y redirecciones.
 *   AuthProvider   va antes que la aplicacion porque casi toda pantalla pregunta
 *                  por la sesion, y porque es el que le entrega el token al
 *                  cliente de la API.
 *   Notificaciones envuelve a la aplicacion para que un aviso sobreviva a un
 *                  cambio de pantalla: se disparan justo antes de navegar.
 *
 * StrictMode monta cada componente dos veces en desarrollo a proposito, para
 * hacer visibles los efectos mal escritos. Es la razon por la que la
 * inicializacion de Keycloak esta protegida contra la doble llamada (ver
 * auth/keycloak.js). Se deja puesto: encontrar esos problemas es justamente para
 * lo que sirve.
 */
createRoot(document.getElementById('root')).render(
    <StrictMode>
        <BrowserRouter>
            <AuthProvider>
                <ProveedorDeNotificaciones>
                    <App />
                </ProveedorDeNotificaciones>
            </AuthProvider>
        </BrowserRouter>
    </StrictMode>
);
