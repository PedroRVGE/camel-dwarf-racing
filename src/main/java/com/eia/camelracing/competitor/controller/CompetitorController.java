package com.eia.camelracing.competitor.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.competitor.dto.CompetitorRequest;
import com.eia.camelracing.competitor.dto.CompetitorResponse;
import com.eia.camelracing.competitor.dto.CompetitorStatusRequest;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import com.eia.camelracing.competitor.service.CompetitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints de competidores.
 *
 * El controlador no tiene logica: valida el formato de lo que entra (@Valid),
 * llama al servicio y elige el codigo HTTP. Las reglas —el apodo unico, el cupo
 * del equipo, que retirar no sea borrar— viven en CompetitorService.
 *
 * QUIEN PUEDE QUE (ver SecurityConfig)
 *   GET     los tres roles, espectador incluido
 *   el resto  solo ADMIN. Segun la tabla del enunciado, el organizador de carreras
 *             unicamente CONSULTA competidores; darlos de alta o editarlos es del
 *             administrador.
 */
@RestController
@RequestMapping("/api/competitors")
@AllArgsConstructor
@Slf4j
@Tag(name = "Competidores",
        description = "Enanos, camellos y competidores medianos. Alta, edicion, cambio de estado y baja")
public class CompetitorController {

    private final CompetitorService competitorService;

    /**
     * CONSULTAR con filtros, paginacion y ordenamiento. GET /api/competitors
     *
     * Los tres son obligatorios segun el enunciado. Ejemplos:
     *   /api/competitors?type=DWARF&status=ACTIVE
     *   /api/competitors?texto=docker
     *   /api/competitors?page=1&size=10&sort=weightKg,desc
     *
     * @ParameterObject es lo que hace que Swagger muestre page, size y sort como
     * tres campos sueltos. Sin el, springdoc dibuja el objeto Pageable entero, con
     * sus propiedades internas, y la pantalla queda inusable.
     *
     * El orden por defecto es por apodo. Un listado sin ORDER BY no tiene orden
     * garantizado: la base devuelve las filas como le conviene y puede cambiarlo
     * entre llamadas, con lo cual un elemento puede aparecer en la pagina 1 y en
     * la 3, o en ninguna.
     */
    @GetMapping
    @Operation(summary = "Listar competidores",
            description = """
                    Devuelve una pagina de competidores. Todos los filtros son opcionales y
                    se pueden combinar.

                    El parametro `texto` busca en el nombre y en el apodo, sin distinguir
                    mayusculas.

                    La paginacion se controla con `page` (empieza en 0), `size` y `sort`
                    (por ejemplo `sort=nickname,asc` o `sort=victories,desc`).
                    """)
    public ResponseEntity<PageResponse<CompetitorResponse>> getCompetitors(
            @Parameter(description = "Filtrar por categoria", example = "DWARF")
            @RequestParam(required = false) CompetitorType type,

            @Parameter(description = "Filtrar por estado", example = "ACTIVE")
            @RequestParam(required = false) CompetitorStatus status,

            @Parameter(description = "Filtrar por equipo")
            @RequestParam(required = false) UUID teamId,

            @Parameter(description = "Buscar en nombre y apodo", example = "docker")
            @RequestParam(required = false) String texto,

            @ParameterObject
            @PageableDefault(size = 20, sort = "nickname", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(
                competitorService.getCompetitors(type, status, teamId, texto, pageable));
    }

    /** CONSULTAR uno. GET /api/competitors/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar un competidor por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El competidor"),
            @ApiResponse(responseCode = "404", description = "No existe un competidor con ese id")
    })
    public ResponseEntity<CompetitorResponse> getCompetitorById(
            @Parameter(description = "Id del competidor") @PathVariable UUID id) {
        return ResponseEntity.ok(competitorService.getCompetitorById(id));
    }

    /**
     * CREAR. POST /api/competitors
     * Devuelve 201, que es el codigo que corresponde al crear un recurso.
     */
    @PostMapping
    @Operation(summary = "Dar de alta un competidor",
            description = """
                    Nace en estado ACTIVE, o sea listo para inscribirse en carreras.

                    El apodo no se puede repetir. El equipo es opcional: sin `teamId`, el
                    competidor corre por su cuenta.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Competidor creado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos: falta el nombre, peso o altura no positivos, fecha de nacimiento futura"),
            @ApiResponse(responseCode = "403", description = "Solo un administrador puede dar de alta competidores"),
            @ApiResponse(responseCode = "404", description = "El teamId no corresponde a ningun equipo"),
            @ApiResponse(responseCode = "409", description = "El apodo ya esta en uso, o el equipo esta completo")
    })
    public ResponseEntity<CompetitorResponse> createCompetitor(
            @Valid @RequestBody CompetitorRequest request) {
        CompetitorResponse response = competitorService.createCompetitor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * EDITAR por completo. PUT /api/competitors/{id}
     *
     * PUT y no PATCH porque se manda el competidor entero: lo que no venga se pisa
     * igual. En particular, omitir teamId lo deja sin equipo, que es lo que
     * significa "este competidor no tiene equipo".
     */
    @PutMapping("/{id}")
    @Operation(summary = "Editar un competidor",
            description = """
                    Se manda el competidor completo: lo que no venga se pisa igual. Mandar
                    `teamId` en null lo saca del equipo y lo deja como competidor individual.

                    El estado NO se cambia por aca: para eso esta
                    `PATCH /api/competitors/{id}/status`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Competidor actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "404", description = "No existe el competidor, o el teamId no existe"),
            @ApiResponse(responseCode = "409", description = "El apodo ya lo tiene otro, o el equipo esta completo")
    })
    public ResponseEntity<CompetitorResponse> updateCompetitor(
            @Parameter(description = "Id del competidor") @PathVariable UUID id,
            @Valid @RequestBody CompetitorRequest request) {
        return ResponseEntity.ok(competitorService.updateCompetitor(id, request));
    }

    /**
     * CAMBIAR DE ESTADO. PATCH /api/competitors/{id}/status
     *
     * PATCH y no PUT porque se modifica una sola cosa, y sobre una ruta propia
     * porque es una transicion con significado, no un campo mas: de esto depende
     * quien puede inscribirse en una carrera.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado de un competidor",
            description = """
                    Lesionarlo, suspenderlo, reactivarlo o retirarlo.

                    Importa porque solo un competidor **ACTIVE** puede inscribirse en
                    carreras nuevas. Un INJURED o un SUSPENDED sigue existiendo y conserva
                    su historial, pero no compite.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "El estado enviado no es uno de los valores validos"),
            @ApiResponse(responseCode = "404", description = "No existe un competidor con ese id")
    })
    public ResponseEntity<CompetitorResponse> changeStatus(
            @Parameter(description = "Id del competidor") @PathVariable UUID id,
            @Valid @RequestBody CompetitorStatusRequest request) {
        return ResponseEntity.ok(competitorService.changeStatus(id, request.status()));
    }

    /**
     * DAR DE BAJA. DELETE /api/competitors/{id}
     *
     * 204 NO CONTENT: salio bien y no hay nada que devolver.
     *
     * OJO CON EL NOMBRE: este DELETE no borra. Deja al competidor en estado
     * RETIRED, porque el enunciado prohibe eliminar fisicamente a alguien con
     * resultados oficiales. Sigue figurando en las carreras que corrio y no se lo
     * puede inscribir en ninguna nueva.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Retirar un competidor",
            description = """
                    **No lo borra de la base.** Lo pasa a estado RETIRED.

                    El enunciado prohibe eliminar fisicamente a un competidor con resultados
                    oficiales: hacerlo reescribiria carreras que ya pasaron y dejaria
                    clasificaciones sin ganador. Retirado, conserva todo su historial y deja
                    de poder inscribirse.

                    Es idempotente: retirar a alguien ya retirado devuelve 204 igual.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Competidor retirado"),
            @ApiResponse(responseCode = "403", description = "Solo un administrador puede retirar competidores"),
            @ApiResponse(responseCode = "404", description = "No existe un competidor con ese id")
    })
    public ResponseEntity<Void> deleteCompetitor(
            @Parameter(description = "Id del competidor") @PathVariable UUID id) {
        competitorService.deleteCompetitor(id);
        return ResponseEntity.noContent().build();
    }
}
