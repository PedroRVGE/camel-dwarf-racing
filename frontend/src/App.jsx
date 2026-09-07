import { Route, Routes } from 'react-router-dom';
import RutaProtegida from './auth/RutaProtegida.jsx';
import Layout from './comun/Layout.jsx';

import Entrar from './paginas/Entrar.jsx';
import Panel from './paginas/Panel.jsx';
import Perfil from './paginas/Perfil.jsx';
import Posiciones from './paginas/Posiciones.jsx';
import Bitacora from './paginas/Bitacora.jsx';
import AccesoDenegado from './paginas/AccesoDenegado.jsx';
import NoEncontrado from './paginas/NoEncontrado.jsx';

import ListaDeCompetidores from './paginas/competidores/ListaDeCompetidores.jsx';
import DetalleDeCompetidor from './paginas/competidores/DetalleDeCompetidor.jsx';
import FormularioDeCompetidor from './paginas/competidores/FormularioDeCompetidor.jsx';

import ListaDeEquipos from './paginas/equipos/ListaDeEquipos.jsx';
import DetalleDeEquipo from './paginas/equipos/DetalleDeEquipo.jsx';
import FormularioDeEquipo from './paginas/equipos/FormularioDeEquipo.jsx';

import ListaDeCarreras from './paginas/carreras/ListaDeCarreras.jsx';
import DetalleDeCarrera from './paginas/carreras/DetalleDeCarrera.jsx';
import FormularioDeCarrera from './paginas/carreras/FormularioDeCarrera.jsx';
import GestionDeInscripciones from './paginas/carreras/GestionDeInscripciones.jsx';
import CargaDeResultados from './paginas/carreras/CargaDeResultados.jsx';

/**
 * El mapa de la aplicacion: que direccion muestra que pantalla.
 *
 * COMO SE LEE ESTA LISTA
 * Todo lo que cuelga del <Route> del medio comparte dos cosas: el marco con la
 * barra de navegacion (Layout) y la exigencia de tener sesion (RutaProtegida).
 * Solo quedan afuera la pantalla de entrada, porque ahi todavia no hay sesion.
 *
 * Las rutas que ademas piden un ROL llevan su propio RutaProtegida con la accion
 * correspondiente. Fijate el patron: LEER nunca pide rol mas alla de estar
 * autenticado, y ESCRIBIR siempre lo pide. Es la misma division que hace
 * SecurityConfig en el backend, y que las dos se lean igual no es casualidad: es
 * lo que permite revisar de un vistazo que no se escapo ninguna.
 *
 * El "*" del final atrapa cualquier direccion que no exista. Sin el, escribir mal
 * una direccion dejaria la pagina en blanco sin explicacion, que es exactamente
 * lo que el enunciado quiere evitar cuando pide una pantalla de "not found".
 */
export default function App() {
    return (
        <Routes>
            <Route path="/entrar" element={<Entrar />} />

            <Route element={<RutaProtegida><Layout /></RutaProtegida>}>
                <Route index element={<Panel />} />

                {/* ---- Competidores ---- */}
                <Route path="competidores" element={<ListaDeCompetidores />} />
                <Route
                    path="competidores/nuevo"
                    element={
                        <RutaProtegida accion="gestionarCompetidores">
                            <FormularioDeCompetidor />
                        </RutaProtegida>
                    }
                />
                <Route
                    path="competidores/:id/editar"
                    element={
                        <RutaProtegida accion="gestionarCompetidores">
                            <FormularioDeCompetidor />
                        </RutaProtegida>
                    }
                />
                <Route path="competidores/:id" element={<DetalleDeCompetidor />} />

                {/* ---- Equipos ---- */}
                <Route path="equipos" element={<ListaDeEquipos />} />
                <Route
                    path="equipos/nuevo"
                    element={
                        <RutaProtegida accion="gestionarEquipos">
                            <FormularioDeEquipo />
                        </RutaProtegida>
                    }
                />
                <Route
                    path="equipos/:id/editar"
                    element={
                        <RutaProtegida accion="gestionarEquipos">
                            <FormularioDeEquipo />
                        </RutaProtegida>
                    }
                />
                <Route path="equipos/:id" element={<DetalleDeEquipo />} />

                {/* ---- Carreras ---- */}
                <Route path="carreras" element={<ListaDeCarreras />} />
                <Route
                    path="carreras/nueva"
                    element={
                        <RutaProtegida accion="gestionarCarreras">
                            <FormularioDeCarrera />
                        </RutaProtegida>
                    }
                />
                <Route
                    path="carreras/:id/editar"
                    element={
                        <RutaProtegida accion="gestionarCarreras">
                            <FormularioDeCarrera />
                        </RutaProtegida>
                    }
                />
                <Route
                    path="carreras/:id/inscripciones"
                    element={
                        <RutaProtegida accion="gestionarInscripciones">
                            <GestionDeInscripciones />
                        </RutaProtegida>
                    }
                />
                <Route
                    path="carreras/:id/resultados"
                    element={
                        <RutaProtegida accion="cargarResultados">
                            <CargaDeResultados />
                        </RutaProtegida>
                    }
                />
                <Route path="carreras/:id" element={<DetalleDeCarrera />} />

                {/* ---- Transversales ---- */}
                <Route path="posiciones" element={<Posiciones />} />
                <Route
                    path="bitacora"
                    element={
                        <RutaProtegida accion="verAuditoria">
                            <Bitacora />
                        </RutaProtegida>
                    }
                />
                <Route path="perfil" element={<Perfil />} />
                <Route path="acceso-denegado" element={<AccesoDenegado />} />
                <Route path="*" element={<NoEncontrado />} />
            </Route>
        </Routes>
    );
}
