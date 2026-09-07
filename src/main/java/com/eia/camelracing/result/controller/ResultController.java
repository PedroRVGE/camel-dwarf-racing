package com.eia.camelracing.result.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.result.dto.ResultRequest;
import com.eia.camelracing.result.dto.ResultResponse;
import com.eia.camelracing.result.service.ResultService;
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
 * Endpoints de resultados.
 *
 * Las rutas siguen el mismo criterio que las de inscripciones: anidadas cuando la
 * carrera es parte de la identidad de la operacion (cargar y listar), planas
 * cuando el resultado ya tiene id propio (consultar y corregir).
 *
 * NO HAY DELETE, Y NO ES UN OLVIDO
 * El enunciado no lo pide, y ademas no tendria sentido. En el resto del sistema
 * "borrar" es un cambio de estado, pero un resultado no puede cambiar de estado
 * hacia la nada: o el participante corrio, o no corrio, y para el segundo caso ya
 * existe DID_NOT_START. Si alguien no tenia que estar en la clasificacion, lo que
 * corresponde es corregir el resultado, no hacerlo desaparecer.
 *
 * QUIEN PUEDE QUE (ver SecurityConfig)
 *   GET       los tres roles: los resultados son publicos, es de lo unico que un
 *             espectador quiere enterarse
 *   el resto  ADMIN u ORGANIZER
 */
@RestController
@RequestMapping("/api")
@AllArgsConstructor
@Slf4j
@Tag(name = "Resultados",
        description = "Carga y correccion de los resultados oficiales de cada carrera")
public class ResultController {

    private final ResultService resultService;

    /**
     * CARGAR. POST /api/races/{raceId}/results
     *
     * Un resultado por participante. La carrera tiene que estar corriendose.
     */
    @PostMapping("/races/{raceId}/results")
    @Operation(summary = "Cargar el resultado de un participante",
            description = """
                    Solo se pueden cargar resultados de una carrera **IN_PROGRESS** y de
                    participantes cuya inscripcion este **APPROVED**.

                    Segun el `status` cambia que campos hay que mandar:

                    - `FINISHED`: obligatorios `completionTimeSeconds` y `finalPosition`.
                    - `DISQUALIFIED`, `DID_NOT_FINISH`, `DID_NOT_START`: los dos tienen que
                      ir en null. Un descalificado no tiene puesto, y por lo tanto tampoco
                      puede ser el ganador.

                    El puesto no se puede repetir dentro de la carrera, y los tiempos tienen
                    que ser coherentes con los puestos: el segundo no puede haber tardado
                    menos que el primero.

                    La posicion de largada no se manda: se copia del carril de la
                    inscripcion.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Resultado cargado"),
            @ApiResponse(responseCode = "400", description = "Datos incoherentes con el estado, tiempo no positivo o falta la inscripcion"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador"),
            @ApiResponse(responseCode = "404", description = "No existe la carrera o la inscripcion"),
            @ApiResponse(responseCode = "409", description = "La carrera no esta IN_PROGRESS, el participante no esta aprobado, ya tiene resultado, el puesto esta ocupado o los tiempos se contradicen")
    })
    public ResponseEntity<ResultResponse> record(
            @Parameter(description = "Id de la carrera") @PathVariable UUID raceId,
            @Valid @RequestBody ResultRequest request) {
        ResultResponse response = resultService.record(raceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * LISTAR la clasificacion de una carrera.
     * GET /api/races/{raceId}/results
     *
     * El orden por defecto es por puesto ascendente. Los que no terminaron tienen
     * la posicion en null y Postgres los manda al final, que es exactamente como se
     * lee una clasificacion: primero el podio, al final los abandonos.
     */
    @GetMapping("/races/{raceId}/results")
    @Operation(summary = "Ver la clasificacion de una carrera",
            description = """
                    Devuelve los resultados de la carrera ordenados por puesto, con los que
                    no terminaron al final.

                    Cada fila trae el tiempo de carrera, la penalizacion, el tiempo total
                    (que es el que define el orden) y los puntos que el resultado suma para
                    la tabla de posiciones.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La clasificacion"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id")
    })
    public ResponseEntity<PageResponse<ResultResponse>> getByRace(
            @Parameter(description = "Id de la carrera") @PathVariable UUID raceId,

            @ParameterObject
            @PageableDefault(size = 20, sort = "finalPosition", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(resultService.getByRace(raceId, pageable));
    }

    /** CONSULTAR uno. GET /api/results/{id} */
    @GetMapping("/results/{id}")
    @Operation(summary = "Buscar un resultado por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El resultado"),
            @ApiResponse(responseCode = "404", description = "No existe un resultado con ese id")
    })
    public ResponseEntity<ResultResponse> getById(
            @Parameter(description = "Id del resultado") @PathVariable UUID id) {
        return ResponseEntity.ok(resultService.getById(id));
    }

    /**
     * CORREGIR. PUT /api/results/{id}
     *
     * PUT y no PATCH porque se manda el resultado completo. Se puede corregir con
     * la carrera en curso o ya terminada: las revisiones del jurado son parte normal
     * de una competencia, y cada correccion queda registrada en la bitacora.
     */
    @PutMapping("/results/{id}")
    @Operation(summary = "Corregir un resultado",
            description = """
                    Se manda el resultado completo. Valen las mismas reglas que al cargarlo:
                    coherencia entre estado, tiempo y puesto, puestos sin repetir y tiempos
                    que no se contradigan.

                    El `registrationId` tiene que ser el mismo: un resultado no cambia de
                    participante. Si es de otro, hay que corregir el que corresponde.

                    Al guardar se **recalculan las estadisticas** del participante desde
                    cero, asi que descalificar a un ganador le saca la victoria del
                    historial y se la devuelve a nadie: el puesto 1 queda vacante hasta que
                    alguien lo ocupe.

                    Se permite con la carrera IN_PROGRESS o COMPLETED. Cada correccion queda
                    en la bitacora con el antes, el despues y quien la hizo.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado corregido"),
            @ApiResponse(responseCode = "400", description = "Datos incoherentes con el estado"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador"),
            @ApiResponse(responseCode = "404", description = "No existe un resultado con ese id"),
            @ApiResponse(responseCode = "409", description = "Se intento cambiar el participante, el puesto esta ocupado, los tiempos se contradicen o la carrera no admite correcciones")
    })
    public ResponseEntity<ResultResponse> update(
            @Parameter(description = "Id del resultado") @PathVariable UUID id,
            @Valid @RequestBody ResultRequest request) {
        return ResponseEntity.ok(resultService.update(id, request));
    }
}
