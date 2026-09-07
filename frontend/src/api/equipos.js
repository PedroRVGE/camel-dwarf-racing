import { api, consulta } from './cliente.js';

/** Los pedidos del modulo de equipos, incluida la gestion de integrantes. */
export const equipos = {
    listar: (filtros) => api.get('/api/teams' + consulta(filtros)),

    buscarPorId: (id) => api.get(`/api/teams/${id}`),

    crear: (datos) => api.post('/api/teams', datos),

    actualizar: (id, datos) => api.put(`/api/teams/${id}`, datos),

    cambiarEstado: (id, status) => api.patch(`/api/teams/${id}/status`, { status }),

    desactivar: (id) => api.delete(`/api/teams/${id}`),

    /**
     * Agregar y quitar integrantes.
     *
     * Van por la direccion del EQUIPO y no por la del competidor porque el que
     * manda la relacion es el equipo: es el que tiene el cupo y las reglas de
     * cuantos integrantes admite. Los dos devuelven el equipo entero ya
     * actualizado, asi que la pantalla no necesita volver a pedirlo.
     */
    agregarIntegrante: (equipoId, competidorId) =>
        api.post(`/api/teams/${equipoId}/members/${competidorId}`),

    quitarIntegrante: (equipoId, competidorId) =>
        api.delete(`/api/teams/${equipoId}/members/${competidorId}`)
};
