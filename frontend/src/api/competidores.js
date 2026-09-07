import { api, consulta } from './cliente.js';

/**
 * Los pedidos del modulo de competidores.
 *
 * Hay un archivo por dominio, igual que en el backend hay un paquete por
 * dominio. La ventaja es la misma: al cambiar un endpoint se sabe exactamente
 * que archivo abrir, y ninguna pantalla tiene direcciones escritas adentro.
 */
export const competidores = {
    /**
     * El listado, con filtros, paginacion y ordenamiento.
     *
     * "sort" viaja como "campo,direccion" porque asi lo espera el Pageable de
     * Spring Data. El backend ordena por apodo cuando no se le dice nada.
     */
    listar: (filtros) => api.get('/api/competitors' + consulta(filtros)),

    buscarPorId: (id) => api.get(`/api/competitors/${id}`),

    crear: (datos) => api.post('/api/competitors', datos),

    actualizar: (id, datos) => api.put(`/api/competitors/${id}`, datos),

    cambiarEstado: (id, status) => api.patch(`/api/competitors/${id}/status`, { status }),

    // La baja es logica: el backend lo pasa a RETIRED y conserva su historial.
    // Por eso el boton de la interfaz dice "Retirar" y no "Eliminar".
    retirar: (id) => api.delete(`/api/competitors/${id}`)
};
