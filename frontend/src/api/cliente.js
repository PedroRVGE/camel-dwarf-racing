import { config } from '../config.js';

// =============================================================================
//  El unico lugar de la aplicacion que habla con la API
// =============================================================================
//  Ninguna pantalla llama a fetch por su cuenta. Todo pasa por aca, y eso
//  concentra en un solo archivo cuatro cosas que si estuvieran repartidas se
//  harian distinto en cada pantalla:
//
//    1. Poner el token en cada pedido.
//    2. Renovarlo si esta por vencer.
//    3. Traducir los errores de la API a algo que una persona pueda leer.
//    4. Distinguir un 204 sin cuerpo de una respuesta con datos.
//
//  POR QUE EL NAVEGADOR LE PEGA DIRECTO A LA API Y NO A TRAVES DE NGINX
//  La otra opcion seria que nginx reenviara /api al backend, y asi todo saldria
//  del mismo origen y no haria falta CORS. No se hizo, por dos razones: el
//  backend ya declara su politica de CORS en SecurityConfig apuntando al origen
//  del frontend (CORS_ALLOWED_ORIGINS en compose.yml), y esconder la API detras
//  de un proxy haria que este proyecto no ejercite justamente la integracion
//  cliente-servidor entre dos origenes distintos, que es la situacion real de una
//  aplicacion de una sola pagina.
// =============================================================================

/**
 * De donde saca el token el cliente.
 *
 * Arranca devolviendo null y AuthProvider lo reemplaza al montarse. Esta vuelta
 * evita que este archivo importe el de autenticacion: si lo hiciera, los dos
 * quedarian importandose mutuamente (AuthProvider necesita instalar el proveedor,
 * el cliente necesita el token) y eso da un ciclo que rompe la carga de modulos.
 */
let proveedorDeToken = async () => null;

export function instalarProveedorDeToken(proveedor) {
    proveedorDeToken = proveedor;
}

/**
 * Un error de la API, ya masticado para la interfaz.
 *
 * Guarda el codigo por separado del mensaje porque las pantallas reaccionan
 * distinto segun el codigo: un 404 muestra la pantalla de "no encontrado", un 403
 * la de acceso denegado, y un 400 tiene que repartir los mensajes por campo
 * adentro del formulario en vez de mostrar uno solo arriba.
 */
export class ErrorDeApi extends Error {
    constructor(status, mensaje, erroresPorCampo) {
        super(mensaje);
        this.name = 'ErrorDeApi';
        this.status = status;
        this.erroresPorCampo = erroresPorCampo ?? null;
    }
}

/**
 * Que decirle al usuario cuando la API falla.
 *
 * El enunciado pide dos cosas a la vez: "API errors must be translated into
 * understandable messages" y "the interface must not expose stack traces".
 *
 * El backend ya devuelve mensajes escritos para leer (el GlobalExceptionHandler
 * se encarga), asi que cuando hay un "message" se usa ese: es el que explica la
 * regla de negocio concreta que se violo, y ninguna frase generica de aca la
 * puede reemplazar. La tabla de abajo es el respaldo para cuando no hay cuerpo,
 * que pasa cuando el que corta el pedido es el navegador o un proxy.
 */
const MENSAJES_POR_DEFECTO = {
    400: 'Hay datos invalidos en el formulario. Revisa los campos marcados.',
    401: 'La sesion vencio. Volve a entrar.',
    403: 'Tu rol no tiene permiso para hacer esto.',
    404: 'No encontramos lo que estabas buscando.',
    409: 'La operacion choca con algo que ya existe.',
    422: 'La operacion no cumple una regla del reglamento.',
    500: 'Algo fallo del lado del servidor. Intentalo de nuevo en un momento.'
};

function mensajeDe(status, cuerpo) {
    if (cuerpo && typeof cuerpo.message === 'string' && cuerpo.message.trim() !== '') {
        return cuerpo.message;
    }
    return MENSAJES_POR_DEFECTO[status] ?? `La API respondio con un error ${status}.`;
}

async function pedir(metodo, ruta, cuerpo) {
    const token = await proveedorDeToken();

    const cabeceras = {};
    if (token) {
        cabeceras.Authorization = `Bearer ${token}`;
    }
    if (cuerpo !== undefined) {
        cabeceras['Content-Type'] = 'application/json';
    }

    let respuesta;
    try {
        respuesta = await fetch(config.apiUrl + ruta, {
            method: metodo,
            headers: cabeceras,
            body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo)
        });
    } catch (error) {
        // fetch solo lanza cuando el pedido no llego a destino: la API esta
        // apagada, no hay red, o el navegador lo corto por CORS. Un 500 NO cae
        // aca, cae abajo con su respuesta. Por eso el mensaje habla de conexion.
        throw new ErrorDeApi(0, 'No se pudo conectar con el servidor. Revisa que la API este levantada.');
    }

    // 204 No Content: la API contesto bien y a proposito no mando cuerpo (es lo
    // que devuelven las bajas). Intentar leerlo como JSON reventaria.
    if (respuesta.status === 204) {
        return null;
    }

    const texto = await respuesta.text();
    let datos = null;
    if (texto) {
        try {
            datos = JSON.parse(texto);
        } catch {
            // Una respuesta que no es JSON casi siempre es una pagina de error de
            // un intermediario. No se muestra su contenido: puede traer detalles
            // internos y el usuario no tiene nada que hacer con ellos.
            datos = null;
        }
    }

    if (!respuesta.ok) {
        throw new ErrorDeApi(
            respuesta.status,
            mensajeDe(respuesta.status, datos),
            datos?.validationErrors
        );
    }

    return datos;
}

/**
 * Arma la parte de la direccion que va despues del signo de pregunta.
 *
 * Descarta los valores vacios en vez de mandarlos: un filtro sin elegir tiene que
 * DESAPARECER del pedido. Si se mandara "status=" vacio, Spring intentaria
 * convertir la cadena vacia al enum y responderia 400.
 */
export function consulta(parametros) {
    const partes = new URLSearchParams();
    Object.entries(parametros).forEach(([clave, valor]) => {
        if (valor !== undefined && valor !== null && valor !== '') {
            partes.append(clave, valor);
        }
    });
    const cadena = partes.toString();
    return cadena ? `?${cadena}` : '';
}

export const api = {
    get: (ruta) => pedir('GET', ruta),
    post: (ruta, cuerpo) => pedir('POST', ruta, cuerpo ?? {}),
    put: (ruta, cuerpo) => pedir('PUT', ruta, cuerpo),
    patch: (ruta, cuerpo) => pedir('PATCH', ruta, cuerpo ?? {}),
    delete: (ruta) => pedir('DELETE', ruta)
};
