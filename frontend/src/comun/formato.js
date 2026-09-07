// =============================================================================
//  Como se muestran los datos que devuelve la API
// =============================================================================
//  La API contesta en el idioma del codigo: DWARF, OPEN_FOR_REGISTRATION,
//  DID_NOT_FINISH. Eso esta bien para un contrato entre programas, pero nadie
//  quiere leerlo en una pantalla.
//
//  La traduccion vive aca y no en cada pantalla por una razon practica: si
//  estuviera repartida, el mismo estado terminaria escrito de tres maneras
//  distintas en tres tablas distintas, que es exactamente lo que el enunciado
//  llama "seven unrelated HTML pages".
//
//  El TONO que acompana a cada etiqueta es el color de la insignia. Tambien esta
//  aca a proposito: que "cancelada" se vea siempre igual en toda la aplicacion es
//  parte de que se entienda sin leer.
// =============================================================================

export const TIPO_DE_COMPETIDOR = {
    DWARF: { texto: 'Enano', tono: 'indigo' },
    CAMEL: { texto: 'Camello', tono: 'ambar' },
    MEDIUM: { texto: 'Mediano', tono: 'cian' }
};

export const ESTADO_DE_COMPETIDOR = {
    ACTIVE: { texto: 'Activo', tono: 'verde' },
    INJURED: { texto: 'Lesionado', tono: 'ambar' },
    SUSPENDED: { texto: 'Suspendido', tono: 'rojo' },
    RETIRED: { texto: 'Retirado', tono: 'gris' }
};

export const ESTADO_DE_EQUIPO = {
    ACTIVE: { texto: 'Activo', tono: 'verde' },
    INACTIVE: { texto: 'Inactivo', tono: 'gris' },
    SUSPENDED: { texto: 'Suspendido', tono: 'rojo' }
};

export const ESTADO_DE_CARRERA = {
    DRAFT: { texto: 'Borrador', tono: 'gris' },
    OPEN_FOR_REGISTRATION: { texto: 'Inscripciones abiertas', tono: 'verde' },
    CLOSED_FOR_REGISTRATION: { texto: 'Inscripciones cerradas', tono: 'ambar' },
    IN_PROGRESS: { texto: 'En curso', tono: 'cian' },
    COMPLETED: { texto: 'Terminada', tono: 'indigo' },
    CANCELLED: { texto: 'Cancelada', tono: 'rojo' }
};

export const TIPO_DE_CARRERA = {
    INDIVIDUAL: { texto: 'Individual', tono: 'indigo' },
    TEAM: { texto: 'Por equipos', tono: 'cian' },
    MIXED: { texto: 'Mixta', tono: 'ambar' }
};

export const ESTADO_DE_INSCRIPCION = {
    PENDING: { texto: 'Pendiente', tono: 'ambar' },
    APPROVED: { texto: 'Aprobada', tono: 'verde' },
    REJECTED: { texto: 'Rechazada', tono: 'rojo' },
    CANCELLED: { texto: 'Cancelada', tono: 'gris' }
};

export const ESTADO_DE_RESULTADO = {
    FINISHED: { texto: 'Termino', tono: 'verde' },
    DISQUALIFIED: { texto: 'Descalificado', tono: 'rojo' },
    DID_NOT_FINISH: { texto: 'Abandono', tono: 'ambar' },
    DID_NOT_START: { texto: 'No largo', tono: 'gris' }
};

/**
 * Las acciones de la bitacora.
 *
 * Son veinte y se escriben todas: una traduccion automatica del estilo "sacarle
 * los guiones bajos y poner la primera en mayuscula" daria "Competitor status
 * changed", que sigue estando en ingles y ademas no dice quien lo hizo ni sobre
 * que. Escribirlas a mano cuesta una vez y se lee bien siempre.
 */
export const ACCION_DE_AUDITORIA = {
    LOGIN: 'Inicio de sesion',
    COMPETITOR_CREATED: 'Alta de competidor',
    COMPETITOR_UPDATED: 'Edicion de competidor',
    COMPETITOR_STATUS_CHANGED: 'Cambio de estado de competidor',
    COMPETITOR_RETIRED: 'Retiro de competidor',
    TEAM_CREATED: 'Alta de equipo',
    TEAM_UPDATED: 'Edicion de equipo',
    TEAM_STATUS_CHANGED: 'Cambio de estado de equipo',
    TEAM_DEACTIVATED: 'Baja de equipo',
    TEAM_MEMBER_ADDED: 'Integrante agregado al equipo',
    TEAM_MEMBER_REMOVED: 'Integrante quitado del equipo',
    RACE_CREATED: 'Alta de carrera',
    RACE_UPDATED: 'Edicion de carrera',
    RACE_STATUS_CHANGED: 'Cambio de estado de carrera',
    RACE_CANCELLED: 'Carrera cancelada',
    REGISTRATION_CREATED: 'Nueva inscripcion',
    REGISTRATION_APPROVED: 'Inscripcion aprobada',
    REGISTRATION_REJECTED: 'Inscripcion rechazada',
    REGISTRATION_CANCELLED: 'Inscripcion cancelada',
    RESULT_RECORDED: 'Resultado cargado'
};

/** Busca la etiqueta de un valor, sin romperse si aparece uno nuevo. */
export function etiqueta(tabla, valor) {
    if (!valor) {
        return { texto: '-', tono: 'gris' };
    }
    // Si el backend agregara un estado nuevo, la interfaz muestra el codigo crudo
    // en vez de quedar en blanco. Es feo a proposito: se nota y se corrige.
    return tabla[valor] ?? { texto: valor, tono: 'gris' };
}

export function textoDe(tabla, valor) {
    return etiqueta(tabla, valor).texto;
}

/** Convierte una tabla de etiquetas en opciones para un desplegable. */
export function opcionesDe(tabla) {
    return Object.entries(tabla).map(([valor, { texto }]) => ({ valor, texto }));
}

// -----------------------------------------------------------------------------
//  Fechas
// -----------------------------------------------------------------------------
//  La API manda LocalDateTime, o sea "2026-12-20T10:00:00" SIN zona horaria. Eso
//  significa "las diez de la manana donde ocurre la carrera", y no un instante
//  universal.
//
//  Por eso NO se usa new Date(...).toLocaleString() a secas para mostrarlas: el
//  navegador interpretaria la cadena en la zona de la maquina y una carrera de
//  las 10:00 podria aparecer a las 05:00 en otra computadora. Se parte la cadena
//  y se muestran los numeros tal como vinieron.

export function fechaYHora(valor) {
    if (!valor) {
        return '-';
    }
    const [fecha, hora = ''] = valor.split('T');
    const [ano, mes, dia] = fecha.split('-');
    return `${dia}/${mes}/${ano} ${hora.slice(0, 5)}`;
}

export function soloFecha(valor) {
    if (!valor) {
        return '-';
    }
    const [ano, mes, dia] = valor.split('T')[0].split('-');
    return `${dia}/${mes}/${ano}`;
}

/** Lo que espera un <input type="datetime-local">: sin segundos. */
export function paraCampoDeFecha(valor) {
    return valor ? valor.slice(0, 16) : '';
}

/** Lo que espera la API: con segundos, porque el LocalDateTime los pide. */
export function desdeCampoDeFecha(valor) {
    if (!valor) {
        return null;
    }
    return valor.length === 16 ? `${valor}:00` : valor;
}

// -----------------------------------------------------------------------------
//  Numeros
// -----------------------------------------------------------------------------

/** 742 segundos se lee mucho mejor como 12:22. */
export function tiempo(segundos) {
    if (segundos === null || segundos === undefined) {
        return '-';
    }
    const minutos = Math.floor(segundos / 60);
    const resto = segundos % 60;
    return `${minutos}:${String(resto).padStart(2, '0')}`;
}

export function distancia(metros) {
    if (metros === null || metros === undefined) {
        return '-';
    }
    return metros >= 1000 ? `${(metros / 1000).toFixed(metros % 1000 === 0 ? 0 : 1)} km` : `${metros} m`;
}

export function numero(valor) {
    return valor === null || valor === undefined ? '-' : String(valor);
}
