import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// =============================================================================
//  Configuracion de Vite
// =============================================================================
//  Vite cumple dos papeles distintos:
//
//    - En desarrollo (npm run dev) es un servidor que sirve los archivos y
//      recarga el navegador cuando algo cambia.
//    - En produccion (npm run build) empaqueta todo en la carpeta dist/, que
//      son archivos estaticos: HTML, CSS y JavaScript. Eso es lo que despues
//      copia el Dockerfile adentro de nginx. No queda ningun proceso de Node
//      corriendo en produccion.
// =============================================================================
export default defineConfig({
    plugins: [react()],

    server: {
        // 5173 es el puerto por defecto de Vite y esta declarado en el realm de
        // Keycloak (keycloak/realm-camelracing.json, cliente camelracing-web)
        // junto con el 3000 de Docker. Keycloak solo redirige el navegador de
        // vuelta a direcciones que tenga registradas: si esto cambiara, el login
        // fallaria con "Invalid parameter: redirect_uri" y el error aparece del
        // lado de Keycloak, no de la aplicacion.
        port: 5173,
        // Sin esto, levantar la app dentro de un contenedor la dejaria escuchando
        // solo en 127.0.0.1 y no seria alcanzable desde afuera.
        host: true
    },

    build: {
        outDir: 'dist',
        // Los mapas de codigo fuente van desactivados a proposito: en produccion
        // publicarian el codigo original completo a cualquiera que abra las
        // herramientas del navegador.
        sourcemap: false
    }
});
