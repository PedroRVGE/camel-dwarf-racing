import { api, consulta } from './cliente.js';

/** Los pedidos del modulo de carreras. */
export const carreras = {
    listar: (filtros) => api.get('/api/races' + consulta(filtros)),

    buscarPorId: (id) => api.get(`/api/races/${id}`),

    crear: (datos) => api.post('/api/races', datos),

    actualizar: (id, datos) => api.put(`/api/races/${id}`, datos),

    /**
     * El avance de estado de la carrera.
     *
     * Es PATCH y no PUT porque cambia UN campo, no reemplaza la carrera entera. Y
     * es un endpoint aparte porque el backend valida que la transicion sea legal:
     * no se puede pasar de borrador a terminada salteando el medio, ni terminar
     * una carrera a la que le faltan resultados. La interfaz solo ofrece los
     * estados siguientes, pero la regla la hace cumplir el backend.
     */
    cambiarEstado: (id, status) => api.patch(`/api/races/${id}/status`, { status }),

    // Cancelar, no borrar: la carrera queda en CANCELLED con su historial.
    cancelar: (id) => api.delete(`/api/races/${id}`)
};
