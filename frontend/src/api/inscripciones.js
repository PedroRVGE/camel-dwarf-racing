import { api, consulta } from './cliente.js';

/**
 * Los pedidos del modulo de inscripciones.
 *
 * Las direcciones estan partidas en dos familias y no es un capricho del
 * backend: para CREAR o LISTAR hace falta saber de que carrera se habla, asi que
 * cuelgan de /api/races/{id}/registrations. Una vez creada, la inscripcion tiene
 * identidad propia y se la trata por /api/registrations/{id}.
 */
export const inscripciones = {
    listarDeCarrera: (carreraId, filtros) =>
        api.get(`/api/races/${carreraId}/registrations` + consulta(filtros)),

    inscribir: (carreraId, datos) =>
        api.post(`/api/races/${carreraId}/registrations`, datos),

    buscarPorId: (id) => api.get(`/api/registrations/${id}`),

    // Al aprobar, el backend asigna el carril si no venia elegido y verifica que
    // no este ocupado. Por eso la aprobacion es un endpoint y no un PUT del
    // estado: tiene logica propia.
    aprobar: (id) => api.patch(`/api/registrations/${id}/approve`),

    rechazar: (id, reason) => api.patch(`/api/registrations/${id}/reject`, { reason }),

    cancelar: (id) => api.delete(`/api/registrations/${id}`)
};
