import { api, consulta } from './cliente.js';

/**
 * La tabla de posiciones.
 *
 * Son dos clasificaciones independientes sobre las mismas carreras: la de
 * competidores individuales y la de equipos. Un resultado pertenece a quien
 * figura en la inscripcion, asi que la victoria de un equipo no se reparte entre
 * sus integrantes ni al reves.
 *
 * No se les manda "sort": el orden de una clasificacion no lo elige el que
 * consulta. Lo fija la consulta del backend (puntos, despues victorias, despues
 * nombre) y esta bien que sea asi, porque una tabla de posiciones ordenada por
 * otra cosa deja de ser una tabla de posiciones.
 */
export const posiciones = {
    competidores: (pagina) => api.get('/api/standings/competitors' + consulta(pagina)),
    equipos: (pagina) => api.get('/api/standings/teams' + consulta(pagina))
};
