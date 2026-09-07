/**
 * Reparte un error de la API entre los campos del formulario y el aviso general.
 *
 * EL PROBLEMA QUE RESUELVE
 * El backend devuelve validationErrors con el nombre del campo como clave, y esas
 * se pegan al campo correspondiente. Pero no todas las validaciones son de UN
 * campo: hay reglas que comparan dos, como "el cierre de inscripciones tiene que
 * ser anterior a la largada". Esas llegan con una clave que NO es ningun campo
 * del formulario (es el nombre del metodo que las verifica), y si se buscaran a
 * ciegas por nombre de campo, el mensaje no aparecerian en ningun lado: el
 * formulario se veria rechazado sin ninguna explicacion visible, que es la peor
 * forma de fallar de un formulario.
 *
 * Por eso se reparte explicitamente: lo que coincide con un campo va al campo, y
 * todo lo demas se junta arriba, donde el usuario mira despues de que su envio no
 * funciono.
 */
export function repartirErrores(error, camposDelFormulario) {
    const porCampo = {};
    const sueltos = [];

    const validaciones = error?.erroresPorCampo ?? null;

    if (validaciones) {
        Object.entries(validaciones).forEach(([clave, mensaje]) => {
            if (camposDelFormulario.includes(clave)) {
                porCampo[clave] = mensaje;
            } else {
                sueltos.push(mensaje);
            }
        });
    }

    // El mensaje general del error solo se muestra cuando no hay nada mas que
    // decir. Si ya hay errores repartidos por campo, agregar arriba un "hay datos
    // invalidos en el formulario" es ruido: el usuario ya ve los campos en rojo.
    const general = sueltos.length > 0
        ? sueltos.join(' ')
        : (Object.keys(porCampo).length === 0 ? (error?.message ?? null) : null);

    return { porCampo, general };
}
