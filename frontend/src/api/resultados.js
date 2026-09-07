import { api, consulta } from './cliente.js';

/** Los pedidos del modulo de resultados. */
export const resultados = {
    listarDeCarrera: (carreraId, filtros) =>
        api.get(`/api/races/${carreraId}/results` + consulta(filtros)),

    /**
     * Cargar un resultado.
     *
     * Es la operacion mas cargada de reglas de todo el sistema: la carrera tiene
     * que estar en curso, el participante tiene que estar aprobado, no puede
     * haber dos primeros puestos, y el tiempo tiene que ser coherente con el
     * puesto. Todas esas validaciones son del backend; la pantalla solo evita los
     * errores de forma antes de mandar.
     */
    cargar: (carreraId, datos) => api.post(`/api/races/${carreraId}/results`, datos),

    buscarPorId: (id) => api.get(`/api/results/${id}`),

    // Corregir un resultado ya cargado. El backend recalcula las estadisticas del
    // competidor desde cero, asi que corregir no deja rastros de la version vieja
    // en los contadores.
    corregir: (id, datos) => api.put(`/api/results/${id}`, datos)
};
