package com.eia.camelracing.team.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.team.dto.TeamRequest;
import com.eia.camelracing.team.dto.TeamResponse;
import com.eia.camelracing.team.dto.TeamStatusRequest;
import com.eia.camelracing.team.dto.TeamSummaryResponse;
import com.eia.camelracing.team.entity.TeamStatus;
import com.eia.camelracing.team.service.TeamService;
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
 * Endpoints de equipos y de sus integrantes.
 *
 * EL LISTADO Y EL DETALLE DEVUELVEN COSAS DISTINTAS
 * GET /api/teams devuelve TeamSummaryResponse, sin la lista de integrantes;
 * GET /api/teams/{id} devuelve TeamResponse, con todos. No es un descuido: traer
 * los integrantes en una consulta paginada rompe la paginacion, porque el JOIN
 * contra la coleccion multiplica las filas y el LIMIT deja de contar equipos. La
 * explicacion larga esta en TeamSummaryResponse.
 */
@RestController
@RequestMapping("/api/teams")
@AllArgsConstructor
@Slf4j
@Tag(name = "Equipos",
        description = "Equipos de competidores y gestion de sus integrantes")
public class TeamController {

    private final TeamService teamService;

    /** CONSULTAR con filtros, paginacion y ordenamiento. GET /api/teams */
    @GetMapping
    @Operation(summary = "Listar equipos",
            description = """
                    Devuelve una pagina de equipos **sin el detalle de sus integrantes**, solo
                    con la cantidad. Para ver quienes son hay que pedir el equipo puntual con
                    `GET /api/teams/{id}`.

                    El parametro `texto` busca en el nombre del equipo y en el del
                    responsable.
                    """)
    public ResponseEntity<PageResponse<TeamSummaryResponse>> getTeams(
            @Parameter(description = "Filtrar por estado", example = "ACTIVE")
            @RequestParam(required = false) TeamStatus status,

            @Parameter(description = "Buscar en el nombre del equipo y del responsable",
                    example = "exceptions")
            @RequestParam(required = false) String texto,

            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(teamService.getTeams(status, texto, pageable));
    }

    /** CONSULTAR uno, con sus integrantes. GET /api/teams/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar un equipo por id",
            description = "Trae el equipo con la lista completa de integrantes, ordenados por apodo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El equipo con sus integrantes"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id")
    })
    public ResponseEntity<TeamResponse> getTeamById(
            @Parameter(description = "Id del equipo") @PathVariable UUID id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    /** CREAR. POST /api/teams */
    @PostMapping
    @Operation(summary = "Crear un equipo",
            description = """
                    Nace ACTIVE y vacio. Los integrantes se suman despues, de a uno, con
                    `POST /api/teams/{teamId}/members/{competitorId}`.

                    El nombre del equipo no se puede repetir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipo creado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "Solo un administrador puede crear equipos"),
            @ApiResponse(responseCode = "409", description = "Ya existe un equipo con ese nombre")
    })
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(request));
    }

    /** EDITAR. PUT /api/teams/{id} */
    @PutMapping("/{id}")
    @Operation(summary = "Editar un equipo",
            description = """
                    Cambia el nombre, la descripcion y el responsable.

                    Ni el estado ni los integrantes se tocan por aca, y eso es a proposito:
                    es lo que hace imposible vaciar un equipo sin querer al corregirle el
                    nombre al entrenador.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo actualizado"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id"),
            @ApiResponse(responseCode = "409", description = "Ya existe otro equipo con ese nombre")
    })
    public ResponseEntity<TeamResponse> updateTeam(
            @Parameter(description = "Id del equipo") @PathVariable UUID id,
            @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    /** CAMBIAR DE ESTADO. PATCH /api/teams/{id}/status */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado de un equipo",
            description = """
                    Suspender un equipo, reactivarlo o darlo de baja.

                    Importa porque un equipo **SUSPENDED** no puede inscribirse en carreras,
                    y solo uno **ACTIVE** puede. Pasarlo a INACTIVE lo da de baja y libera a
                    sus integrantes, igual que el DELETE.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "El estado enviado no es uno de los valores validos"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id")
    })
    public ResponseEntity<TeamResponse> changeStatus(
            @Parameter(description = "Id del equipo") @PathVariable UUID id,
            @Valid @RequestBody TeamStatusRequest request) {
        return ResponseEntity.ok(teamService.changeStatus(id, request.status()));
    }

    /**
     * DAR DE BAJA. DELETE /api/teams/{id}
     *
     * Igual que con los competidores, este DELETE no borra: deja el equipo en
     * INACTIVE y libera a sus integrantes.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Dar de baja un equipo",
            description = """
                    **No lo borra de la base.** Lo pasa a estado INACTIVE y deja a sus
                    integrantes sin equipo, libres para sumarse a otro.

                    El enunciado prohibe eliminar un equipo con historial oficial de
                    carreras, porque dejaria resultados apuntando a un equipo inexistente.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Equipo dado de baja"),
            @ApiResponse(responseCode = "403", description = "Solo un administrador puede dar de baja equipos"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id")
    })
    public ResponseEntity<Void> deleteTeam(
            @Parameter(description = "Id del equipo") @PathVariable UUID id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Integrantes
    // ------------------------------------------------------------------

    /**
     * SUMAR un integrante. POST /api/teams/{teamId}/members/{competitorId}
     *
     * Los dos ids van en la ruta y no en el cuerpo porque la operacion se identifica
     * enteramente con ellos: es "esta relacion, entre este equipo y este
     * competidor". No hay ningun dato mas que mandar.
     *
     * Devuelve 200 con el equipo actualizado, y no 201: no se creo un recurso
     * nuevo con su propia URL, se modifico uno que ya existia. Devolver el equipo
     * entero le ahorra al frontend una segunda llamada para refrescar la pantalla.
     */
    @PostMapping("/{teamId}/members/{competitorId}")
    @Operation(summary = "Sumar un competidor al equipo",
            description = """
                    Reglas que se verifican:

                    - El equipo no puede superar su maximo de integrantes, que es
                      configurable (`camelracing.teams.max-members`).
                    - No se puede sumar gente a un equipo dado de baja.
                    - Un competidor pertenece a lo sumo a un equipo: si ya estaba en otro,
                      esta operacion lo mueve.

                    Que un competidor no pueda estar dos veces en el mismo equipo no hace
                    falta verificarlo: la pertenencia es una sola columna, asi que es
                    imposible por construccion.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integrante sumado; devuelve el equipo actualizado"),
            @ApiResponse(responseCode = "404", description = "No existe el equipo o el competidor"),
            @ApiResponse(responseCode = "409", description = "El equipo esta completo o esta dado de baja")
    })
    public ResponseEntity<TeamResponse> addMember(
            @Parameter(description = "Id del equipo") @PathVariable UUID teamId,
            @Parameter(description = "Id del competidor") @PathVariable UUID competitorId) {
        return ResponseEntity.ok(teamService.addMember(teamId, competitorId));
    }

    /** SACAR un integrante. DELETE /api/teams/{teamId}/members/{competitorId} */
    @DeleteMapping("/{teamId}/members/{competitorId}")
    @Operation(summary = "Sacar un competidor del equipo",
            description = """
                    El competidor **no se borra**: queda sin equipo, como competidor
                    individual, y conserva todo su historial.

                    Si el competidor no pertenece a este equipo devuelve 409 y no 204, para
                    que no parezca que funciono cuando en realidad no habia nada que sacar.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integrante retirado; devuelve el equipo actualizado"),
            @ApiResponse(responseCode = "404", description = "No existe el equipo o el competidor"),
            @ApiResponse(responseCode = "409", description = "Ese competidor no pertenece a este equipo")
    })
    public ResponseEntity<TeamResponse> removeMember(
            @Parameter(description = "Id del equipo") @PathVariable UUID teamId,
            @Parameter(description = "Id del competidor") @PathVariable UUID competitorId) {
        return ResponseEntity.ok(teamService.removeMember(teamId, competitorId));
    }
}
