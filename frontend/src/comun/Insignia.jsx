import { etiqueta } from './formato.js';

/**
 * La pastilla de color que muestra un estado.
 *
 * Recibe la TABLA de traduccion y el valor crudo, en vez de un texto ya armado.
 * Asi la pantalla escribe <Insignia tabla={ESTADO_DE_CARRERA} valor={c.status} />
 * y no tiene que saber ni como se traduce ni de que color va: las dos cosas viven
 * en formato.js y valen para toda la aplicacion.
 *
 * El color NO es la unica senal: el texto siempre esta. Una insignia que dependa
 * solo del color deja afuera a quien no los distingue.
 */
export default function Insignia({ tabla, valor }) {
    const { texto, tono } = etiqueta(tabla, valor);
    return <span className={`insignia insignia-${tono}`}>{texto}</span>;
}
