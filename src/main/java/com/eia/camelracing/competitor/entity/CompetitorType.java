package com.eia.camelracing.competitor.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Que clase de competidor es.
 *
 * Es un enum y no un String libre porque el enunciado lo pide asi y porque de eso
 * dependen reglas: "un camello no debe inscribirse como enano, por mas que crea
 * mucho en si mismo". Con texto libre esa regla es inaplicable, y ademas la base
 * termina con "DWARF", "dwarf", "Enano" y "enano " conviviendo como si fueran
 * cuatro cosas distintas.
 *
 * Se guarda con @Enumerated(EnumType.STRING), o sea el nombre como texto. La
 * alternativa, ORDINAL, guarda la POSICION (0, 1, 2...) y es una trampa clasica:
 * el dia que alguien agregue un valor en el medio de esta lista, todas las filas
 * ya guardadas pasan a significar otra cosa, en silencio y sin forma de saber
 * cuales estaban mal.
 */
@Schema(description = "Categoria del competidor")
public enum CompetitorType {

    /** Enano. Compite solo o en equipo; historicamente, de a cinco. */
    DWARF,

    /** Camello. Larga cuando se siente emocionalmente preparado. */
    CAMEL,

    /** Competidor de tamano mediano. */
    MEDIUM,

    /** Cualquier otra categoria que apruebe un administrador. */
    OTHER
}
