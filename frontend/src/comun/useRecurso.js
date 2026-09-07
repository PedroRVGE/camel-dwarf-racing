import { useCallback, useEffect, useState } from 'react';

/**
 * Traer datos de la API y llevar la cuenta de en que estado esta la pantalla.
 *
 * Todas las pantallas que leen algo hacen lo mismo: piden, muestran un indicador
 * mientras esperan, guardan lo que llego o el error, y a veces vuelven a pedir.
 * Escribir eso a mano en cada una es la forma segura de que en la tercera alguien
 * se olvide de apagar el indicador cuando el pedido falla, y la pantalla quede
 * cargando para siempre.
 *
 * EL DETALLE QUE NO SE VE: LAS RESPUESTAS QUE LLEGAN TARDE
 * La bandera "vigente" existe por una carrera real. Si el usuario escribe rapido
 * en el buscador salen varios pedidos, y no hay ninguna garantia de que vuelvan
 * en orden: la respuesta de "mar" puede llegar DESPUES de la de "martillo", y la
 * pantalla terminaria mostrando resultados que no corresponden a lo que dice el
 * buscador. La funcion de limpieza marca el pedido viejo como no vigente y su
 * respuesta se descarta al llegar.
 *
 * "dependencias" se pasa explicitamente porque la funcion de carga se vuelve a
 * crear en cada dibujado y no sirve como dependencia: usarla haria un pedido
 * nuevo por cada dibujado, y cada pedido provocaria otro dibujado.
 */
export function useRecurso(cargar, dependencias = []) {
    const [datos, setDatos] = useState(null);
    const [cargando, setCargando] = useState(true);
    const [error, setError] = useState(null);
    const [intento, setIntento] = useState(0);

    useEffect(() => {
        let vigente = true;

        setCargando(true);
        setError(null);

        cargar()
            .then((respuesta) => {
                if (vigente) {
                    setDatos(respuesta);
                }
            })
            .catch((fallo) => {
                if (vigente) {
                    setError(fallo);
                    // Se limpia lo anterior a proposito: dejar los datos viejos
                    // junto a un mensaje de error hace creer que lo que se ve es
                    // lo actual.
                    setDatos(null);
                }
            })
            .finally(() => {
                if (vigente) {
                    setCargando(false);
                }
            });

        return () => {
            vigente = false;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [...dependencias, intento]);

    // Sirve para dos cosas: el boton "Reintentar" del estado de error, y volver a
    // leer la lista despues de haber aprobado, rechazado o borrado algo.
    const recargar = useCallback(() => setIntento((numero) => numero + 1), []);

    return { datos, cargando, error, recargar };
}

/**
 * Espera a que el usuario deje de escribir antes de devolver el valor.
 *
 * Se usa en los buscadores. Sin esto, escribir "martillo" dispara ocho pedidos,
 * uno por letra, y siete no le sirven a nadie: el unico que importa es el ultimo.
 * Con 350 milisegundos de espera sale un solo pedido y la pantalla igual se
 * siente inmediata, porque el campo de texto se actualiza al instante; lo unico
 * que se demora es la consulta.
 *
 * No reemplaza a la bandera "vigente" de useRecurso, la complementa: esto reduce
 * la cantidad de pedidos, y aquello resuelve el orden en que vuelven.
 */
export function useValorDemorado(valor, milisegundos = 350) {
    const [demorado, setDemorado] = useState(valor);

    useEffect(() => {
        const reloj = window.setTimeout(() => setDemorado(valor), milisegundos);
        // Cada tecla cancela el reloj anterior. Por eso el pedido sale recien
        // cuando pasan los milisegundos SIN escribir nada.
        return () => window.clearTimeout(reloj);
    }, [valor, milisegundos]);

    return demorado;
}
