package com.eia.camelracing.result.entity;

/**
 * Como le fue al participante en la carrera.
 *
 * Son los cuatro estados que pide el enunciado, y cubren los cuatro finales
 * posibles: llego, lo descalificaron, largo pero no llego, o nunca largo.
 *
 * LA DIFERENCIA ENTRE LOS TRES QUE NO SON FINISHED NO ES DECORATIVA
 *   DISQUALIFIED   corrio, pero se le anulo la actuacion (por ejemplo, un enano
 *                  que se colgo del camello). Cuenta como derrota.
 *   DID_NOT_FINISH largo y abandono. Tambien cuenta como derrota: estuvo.
 *   DID_NOT_START  ni siquiera largo. NO cuenta como derrota, porque no compitio.
 *                  Una lesion en el calentamiento no deberia ensuciarle el
 *                  historial a nadie.
 *
 * Solo FINISHED tiene posicion final y tiempo. Los otros tres no: no se puede ser
 * tercero de una carrera que no se termino.
 */
public enum ResultStatus {

    FINISHED,
    DISQUALIFIED,
    DID_NOT_FINISH,
    DID_NOT_START;

    /** Si el participante llego a la meta y su actuacion vale. */
    public boolean llegoALaMeta() {
        return this == FINISHED;
    }

    /**
     * Si este estado admite una posicion final y un tiempo.
     *
     * Es la misma condicion que llegoALaMeta(), pero con otro nombre porque
     * responde otra pregunta: una es sobre el corredor y la otra sobre que datos
     * puede tener la fila. Tenerlas separadas hace que el codigo que valida se lea
     * como la regla que esta aplicando.
     */
    public boolean admitePosicionYTiempo() {
        return this == FINISHED;
    }

    /**
     * Si cuenta como derrota en las estadisticas del participante.
     *
     * DID_NOT_START queda afuera: no compitio. FINISHED tambien queda afuera aca,
     * porque para saber si fue victoria o derrota hace falta ademas la posicion, y
     * eso lo resuelve el servicio de resultados.
     */
    public boolean cuentaComoDerrota() {
        return this == DISQUALIFIED || this == DID_NOT_FINISH;
    }

    /**
     * Los puntos que suma este resultado para la tabla de posiciones.
     *
     * ESTA ES LA TABLA DEL ENUNCIADO, Y ESTE ES SU UNICO LUGAR EN JAVA
     *   1ro 10 | 2do 7 | 3ro 5 | 4to 3 | 5to 1 | del 6to en adelante 0
     *   no termino 0 | descalificado 0 | no largo 0
     *
     * ATENCION: la misma escala esta escrita una segunda vez, en SQL, dentro de
     * IStandingsRepository. No se puede evitar sin renunciar a que la suma la haga
     * la base, que es lo que permite ordenar y paginar la tabla de posiciones sin
     * traer todos los resultados a memoria. Si alguna vez cambia el puntaje, hay
     * que tocar LOS DOS lugares, y por eso cada uno menciona al otro.
     */
    public int puntos(Integer posicionFinal) {
        if (this != FINISHED || posicionFinal == null) {
            return 0;
        }
        return switch (posicionFinal) {
            case 1 -> 10;
            case 2 -> 7;
            case 3 -> 5;
            case 4 -> 3;
            case 5 -> 1;
            default -> 0;
        };
    }
}
