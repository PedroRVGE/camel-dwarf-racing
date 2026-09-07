// =============================================================================
//  Los pasos que puede dar una carrera
// =============================================================================
//  Es la copia de RaceStatus.transicionesPosibles(), del backend. Sirve para que
//  la pantalla ofrezca SOLO los estados a los que la carrera realmente puede
//  pasar, en vez de un desplegable con los seis y un error despues.
//
//  Vale la misma aclaracion que en la tabla de permisos: esto no hace cumplir
//  nada. Si alguien manda una transicion imposible, el que la rechaza es el
//  backend. Lo que gana la interfaz es no ofrecer caminos que no existen.
//
//  Que la carrera avance de a un paso no es burocracia: cada estado habilita
//  cosas distintas. Solo se puede inscribir gente con las inscripciones abiertas,
//  y solo se pueden cargar resultados con la carrera en curso. Saltearse un
//  estado dejaria carreras con resultados y sin inscriptos.
// =============================================================================

export const TRANSICIONES = {
    DRAFT: ['OPEN_FOR_REGISTRATION', 'CANCELLED'],
    OPEN_FOR_REGISTRATION: ['CLOSED_FOR_REGISTRATION', 'CANCELLED'],
    CLOSED_FOR_REGISTRATION: ['OPEN_FOR_REGISTRATION', 'IN_PROGRESS', 'CANCELLED'],
    IN_PROGRESS: ['COMPLETED', 'CANCELLED'],
    COMPLETED: [],
    CANCELLED: []
};

/**
 * El texto del boton de cada paso.
 *
 * Se escribe como una ACCION y no como el nombre del estado destino. "Abrir las
 * inscripciones" dice que va a pasar; "Inscripciones abiertas" parece una
 * etiqueta y deja al usuario adivinando si esta describiendo o proponiendo.
 */
export const NOMBRE_DEL_PASO = {
    OPEN_FOR_REGISTRATION: 'Abrir las inscripciones',
    CLOSED_FOR_REGISTRATION: 'Cerrar las inscripciones',
    IN_PROGRESS: 'Largar la carrera',
    COMPLETED: 'Dar la carrera por terminada',
    CANCELLED: 'Cancelar la carrera'
};

/** Lo que hay que advertir antes de dar el paso, si hay algo que advertir. */
export const ADVERTENCIA_DEL_PASO = {
    CLOSED_FOR_REGISTRATION: 'No se van a poder anotar mas participantes. Se puede volver a abrir mientras la carrera no haya largado.',
    IN_PROGRESS: 'A partir de aca ya no se pueden modificar los datos de la carrera ni sus inscripciones.',
    COMPLETED: 'La carrera queda cerrada definitivamente. Antes hay que haber cargado el resultado de todos los participantes aprobados.',
    CANCELLED: 'La carrera se cancela y no se puede reabrir. Las inscripciones quedan sin efecto.'
};
