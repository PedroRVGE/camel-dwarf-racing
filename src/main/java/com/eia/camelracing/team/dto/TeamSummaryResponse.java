package com.eia.camelracing.team.dto;

import com.eia.camelracing.team.entity.TeamStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Un equipo en el LISTADO, sin sus integrantes.
 *
 * POR QUE EL LISTADO Y EL DETALLE DEVUELVEN COSAS DISTINTAS
 * No es una optimizacion opcional: es lo que permite que el listado se pueda
 * paginar. Traer los integrantes de cada equipo en una consulta paginada obliga a
 * Hibernate a hacer un JOIN contra una coleccion, y ahi el LIMIT deja de
 * funcionar: el JOIN multiplica las filas (un equipo de cinco aparece cinco
 * veces), asi que "traeme 20" ya no significa 20 equipos. Hibernate lo resuelve
 * trayendo TODO a memoria y recortando en Java, que es exactamente lo que la
 * paginacion venia a evitar.
 *
 * En este proyecto ni siquiera lo hace en silencio: application.yml tiene
 * fail_on_pagination_over_collection_fetch en true, asi que la consulta falla y
 * el problema aparece en el arranque en vez de aparecer como lentitud rara meses
 * despues.
 *
 * memberCount se calcula en la base con SIZE(), que Hibernate traduce a una
 * subconsulta con COUNT. Se obtiene el numero sin traer una sola fila de
 * competidores.
 *
 * Este record se construye DIRECTAMENTE desde la consulta JPQL, con un
 * constructor expression (SELECT new ...TeamSummaryResponse(...)). Por eso el
 * orden y el tipo de los parametros tienen que coincidir exacto con el SELECT: si
 * se cambia uno hay que cambiar el otro, y el error recien aparece al arrancar la
 * aplicacion, no al compilar.
 */
@Schema(description = "Un equipo en el listado, sin el detalle de sus integrantes")
public record TeamSummaryResponse(

        UUID id,
        String name,
        String coach,
        TeamStatus status,

        @Schema(description = "Cantidad de integrantes", example = "5")
        int memberCount,

        int victories,
        int defeats
) {
}
