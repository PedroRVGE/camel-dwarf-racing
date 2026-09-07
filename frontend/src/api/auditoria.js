import { api, consulta } from './cliente.js';

/**
 * La bitacora de auditoria.
 *
 * Es de solo lectura y solo para el administrador: nadie escribe en ella desde la
 * interfaz. Las filas las genera el backend cada vez que alguien cambia algo, y
 * ese es justamente el punto: si se pudieran editar desde afuera no servirian
 * como registro de nada.
 */
export const auditoria = {
    listar: (filtros) => api.get('/api/audit' + consulta(filtros))
};

/** El perfil del usuario que tiene la sesion abierta, tal como lo ve la API. */
export const perfil = {
    consultar: () => api.get('/api/auth/profile')
};
