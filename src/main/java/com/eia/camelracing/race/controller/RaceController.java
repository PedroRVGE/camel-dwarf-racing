package com.eia.camelracing.race.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.race.dto.RaceRequest;
import com.eia.camelracing.race.dto.RaceResponse;
import com.eia.camelracing.race.dto.RaceStatusRequest;
import com.eia.camelracing.race.dto.RaceSummaryResponse;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.entity.RaceType;
import com.eia.camelracing.race.service.RaceService;
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
 * Endpoints de carreras.
 *
 * Mismo reparto de tareas que en los otros controladores: aca solo se valida el
 * formato de lo que entra, se llama al servicio y se elige el codigo HTTP. Todo lo
 * que es regla —que no se pueda programar una carrera para ayer, que no se pueda
 * largar con un solo participante, que una carrera terminada no vuelva a
 * borrador— vive en RaceService y en RaceStatus.
 *
 * QUIEN PUEDE QUE (ver SecurityConfig)
 *   GET       los tres roles, espectador incluido
 *   el resto  ADMIN u ORGANIZER. Organizar carreras es, literalmente, el trabajo
 *             del organizador: es el modulo donde ese rol escribe.
 *
 * DOS RUTAS PARA CAMBIAR UNA CARRERA, Y NO UNA
 *   PUT   /api/races/{id}          cambia los DATOS (nombre, fecha, distancia)
 *   PATCH /api/races/{id}/status   cambia el ESTADO
 * Estan separadas porque son operaciones distintas: la primera corrige
 * informacion, la segunda hace avanzar el ciclo de vida y tiene reglas propias
 * sobre que transicion es legal. Mezclarlas en un solo PUT significaria que
 * corregirle una letra al nombre pasa por la maquina de estados.
 */
@RestController
@RequestMapping("/api/races")
@AllArgsConstructor
@Slf4j
@Tag(name = "Carreras",
        description = "Alta, edicion, ciclo de vida y cancelacion de carreras")
public class RaceController {

    private final RaceService raceService;

    /**
     * CONSULTAR con filtros, paginacion y ordenamiento. GET /api/races
     *
     * El orden por defecto es por fecha de largada ascendente: la pregunta que se
     * le hace casi siempre a este listado es "que se corre proximamente".
     */
    @GetMapping
    @Operation(summary = "Listar carreras",
            description = """
                    Devuelve una pagina de carreras. Todos los filtros son opcionales y se
                    pueden combinar.

                    El parametro `texto` busca en el nombre y en los lugares de largada y
                    llegada, sin distinguir mayusculas.

                    Cada fila trae `approvedCount`, o sea cuantos participantes confirmados
                    tiene la carrera, para poder mostrar el cupo sin pedir el detalle de
                    cada una.

                    La paginacion se controla con `page` (empieza en 0), `size` y `sort`
                    (por ejemplo `sort=scheduledAt,desc`).
                    """)
    public ResponseEntity<PageResponse<RaceSummaryResponse>> getRaces(
            @Parameter(description = "Filtrar por estado", example = "OPEN_FOR_REGISTRATION")
            @RequestParam(required = false) RaceStatus status,

            @Parameter(description = "Filtrar por tipo", example = "MIXED")
            @RequestParam(required = false) RaceType type,

            @Parameter(description = "Buscar en nombre, largada y llegada", example = "palmas")
            @RequestParam(required = false) String texto,

            @ParameterObject
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(raceService.getRaces(status, type, texto, pageable));
    }

    /**
     * CONSULTAR una. GET /api/races/{id}
     *
     * El detalle agrega lo que el listado no trae: la descripcion, el organizador,
     * las marcas de tiempo y los dos conteos de inscripciones (confirmadas y
     * pendientes de aprobar).
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar una carrera por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La carrera"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id")
    })
    public ResponseEntity<RaceResponse> getRaceById(
            @Parameter(description = "Id de la carrera") @PathVariable UUID id) {
        return ResponseEntity.ok(raceService.getRaceById(id));
    }

    /**
     * CREAR. POST /api/races
     *
     * La carrera nace en DRAFT: creada, pero todavia sin inscripciones abiertas.
     * El organizador no viene en el cuerpo, sale del token de quien la crea.
     */
    @PostMapping
    @Operation(summary = "Crear una carrera",
            description = """
                    Nace en estado **DRAFT**. Para que se pueda anotar gente hay que pasarla
                    a `OPEN_FOR_REGISTRATION` con `PATCH /api/races/{id}/status`.

                    El campo `organizer` no se manda: se toma del usuario autenticado.

                    Dos reglas del enunciado se validan sobre el cuerpo y devuelven 400: la
                    fecha de la carrera tiene que estar en el futuro, y el cierre de
                    inscripciones tiene que ser anterior a la largada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Carrera creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos: fecha en el pasado, cierre posterior a la largada, distancia o cupo fuera de rango"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador")
    })
    public ResponseEntity<RaceResponse> createRace(@Valid @RequestBody RaceRequest request) {
        RaceResponse response = raceService.createRace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * EDITAR los datos. PUT /api/races/{id}
     *
     * PUT y no PATCH porque se manda la carrera entera: lo que no venga se pisa.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Editar una carrera",
            description = """
                    Se manda la carrera completa: lo que no venga se pisa igual.

                    **No se puede editar** una carrera COMPLETED, CANCELLED ni IN_PROGRESS:
                    en los tres casos cambiar las condiciones falsearia algo que ya esta
                    pasando o que ya paso. Intentarlo devuelve 409.

                    El estado NO se cambia por aca: para eso esta
                    `PATCH /api/races/{id}/status`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrera actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id"),
            @ApiResponse(responseCode = "409", description = "La carrera esta terminada, cancelada o en curso")
    })
    public ResponseEntity<RaceResponse> updateRace(
            @Parameter(description = "Id de la carrera") @PathVariable UUID id,
            @Valid @RequestBody RaceRequest request) {
        return ResponseEntity.ok(raceService.updateRace(id, request));
    }

    /**
     * CAMBIAR DE ESTADO. PATCH /api/races/{id}/status
     *
     * Es el endpoint mas importante del modulo: el que hace avanzar la carrera de
     * borrador a inscripciones abiertas, a cerradas, a en curso y a terminada. Las
     * transiciones validas estan declaradas en el enum RaceStatus.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado de una carrera",
            description = """
                    Hace avanzar la carrera por su ciclo de vida:

                    `DRAFT` -> `OPEN_FOR_REGISTRATION` -> `CLOSED_FOR_REGISTRATION` ->
                    `IN_PROGRESS` -> `COMPLETED`

                    Desde CLOSED_FOR_REGISTRATION se pueden volver a abrir las
                    inscripciones. Desde cualquier estado no final se puede pasar a
                    CANCELLED. COMPLETED y CANCELLED son finales: de ahi no se sale.

                    Para pasar a **IN_PROGRESS** hacen falta al menos **dos inscripciones
                    aprobadas**: una carrera de uno no es una carrera.

                    Pedir el estado que la carrera ya tiene no es un error: devuelve 200 sin
                    cambiar nada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "El estado enviado no es uno de los valores validos"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id"),
            @ApiResponse(responseCode = "409", description = "La transicion no es valida, o la carrera no tiene los dos participantes aprobados para largar")
    })
    public ResponseEntity<RaceResponse> changeStatus(
            @Parameter(description = "Id de la carrera") @PathVariable UUID id,
            @Valid @RequestBody RaceStatusRequest request) {
        return ResponseEntity.ok(raceService.changeStatus(id, request.status()));
    }

    /**
     * CANCELAR. DELETE /api/races/{id}
     *
     * OJO CON EL NOMBRE: igual que con competidores y equipos, este DELETE no
     * borra. Deja la carrera en CANCELLED. Borrarla dejaria inscripciones y, mas
     * adelante, resultados apuntando a algo inexistente, y la haria desaparecer del
     * historial de todos los que corrieron en ella.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una carrera",
            description = """
                    **No la borra de la base.** La pasa a estado CANCELLED.

                    Una carrera ya COMPLETED no se puede cancelar: no se puede "des-correr"
                    algo que ya se corrio. Intentarlo devuelve 409.

                    Es idempotente: cancelar una carrera ya cancelada devuelve 204 igual.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Carrera cancelada"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id"),
            @ApiResponse(responseCode = "409", description = "La carrera ya se corrio y no se puede cancelar")
    })
    public ResponseEntity<Void> deleteRace(
            @Parameter(description = "Id de la carrera") @PathVariable UUID id) {
        raceService.deleteRace(id);
        return ResponseEntity.noContent().build();
    }
}
