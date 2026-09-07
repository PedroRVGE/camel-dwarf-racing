package com.eia.camelracing.standings.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.standings.dto.StandingResponse;
import com.eia.camelracing.standings.service.StandingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La tabla de posiciones del campeonato.
 *
 * SOLO LECTURA, Y NO POR UNA CUESTION DE PERMISOS
 * No hay POST ni PUT porque no hay nada que escribir: las posiciones se calculan
 * a partir de los resultados cada vez que se piden. La forma de cambiar la tabla es
 * cargar o corregir un resultado, y eso ya tiene sus endpoints y sus reglas.
 *
 * Un endpoint para editar las posiciones a mano seria justamente la puerta que todo
 * lo demas se ocupa de cerrar.
 *
 * El @PageableDefault no lleva sort a proposito: el orden de una clasificacion lo
 * define la clasificacion, no quien la mira. StandingsService descarta cualquier
 * sort que llegue, y explica por que.
 */
@RestController
@RequestMapping("/api/standings")
@AllArgsConstructor
@Slf4j
@Tag(name = "Posiciones",
        description = "Tabla de posiciones del campeonato, para competidores y para equipos")
public class StandingsController {

    private final StandingsService standingsService;

    /** LA TABLA DE COMPETIDORES. GET /api/standings/competitors */
    @GetMapping("/competitors")
    @Operation(summary = "Tabla de posiciones de competidores",
            description = """
                    Los competidores ordenados por puntos, de mayor a menor.

                    La escala es la del enunciado: **10** puntos el primero, **7** el
                    segundo, **5** el tercero, **3** el cuarto y **1** el quinto. Del sexto
                    en adelante, y para abandonos, descalificaciones y los que no largaron,
                    **0**.

                    Los empates se desempatan por cantidad de victorias y, si tambien
                    empatan, por apodo, para que el orden sea siempre el mismo.

                    **Solo aparecen los competidores con al menos un resultado**, y solo
                    cuentan sus carreras individuales: lo que hicieron corriendo con un
                    equipo se ve en la tabla de equipos.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La tabla de posiciones"),
            @ApiResponse(responseCode = "401", description = "No se mando token, o no es valido")
    })
    public ResponseEntity<PageResponse<StandingResponse>> getCompetitorStandings(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(standingsService.getCompetitorStandings(pageable));
    }

    /** LA TABLA DE EQUIPOS. GET /api/standings/teams */
    @GetMapping("/teams")
    @Operation(summary = "Tabla de posiciones de equipos",
            description = """
                    Los equipos ordenados por puntos, con la misma escala y los mismos
                    desempates que la tabla de competidores.

                    Cuentan las carreras que el equipo corrio como tal. Solo aparecen los
                    equipos con al menos un resultado.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La tabla de posiciones"),
            @ApiResponse(responseCode = "401", description = "No se mando token, o no es valido")
    })
    public ResponseEntity<PageResponse<StandingResponse>> getTeamStandings(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(standingsService.getTeamStandings(pageable));
    }
}
