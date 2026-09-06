package com.eia.camelracing.registration.controller;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.registration.dto.RegistrationRejectRequest;
import com.eia.camelracing.registration.dto.RegistrationRequest;
import com.eia.camelracing.registration.dto.RegistrationResponse;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.registration.service.RegistrationService;
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
 * Endpoints de inscripciones.
 *
 * POR QUE LAS RUTAS SON DE DOS FORMAS DISTINTAS
 * Este controlador tiene @RequestMapping("/api") a secas y las rutas completas en
 * cada metodo, porque las inscripciones se piden de dos maneras que son las dos
 * correctas:
 *
 *   /api/races/{raceId}/registrations   ANIDADA. Una inscripcion no existe sin una
 *                                       carrera: inscribirse es siempre inscribirse
 *                                       EN algo. Por eso crear y listar cuelgan de
 *                                       la carrera, que ademas es como se consulta
 *                                       en la practica ("los anotados en esta
 *                                       carrera").
 *
 *   /api/registrations/{id}             PLANA. Una vez creada, la inscripcion tiene
 *                                       id propio y se la aprueba, rechaza o
 *                                       cancela sin necesidad de repetir a que
 *                                       carrera pertenece. Pedir el raceId ademas
 *                                       del id abriria la puerta a que los dos no
 *                                       correspondan entre si.
 *
 * QUIEN PUEDE QUE (ver SecurityConfig)
 *   GET       los tres roles
 *   el resto  ADMIN u ORGANIZER: aprobar y rechazar inscripciones es la tarea de
 *             validacion que el enunciado le asigna al organizador.
 *
 * Las reglas duras —cupo, carril duplicado, competidor suspendido, equipo sin
 * integrantes, doble inscripcion, correr individualmente y en equipo en la misma
 * carrera— estan todas en RegistrationService.
 */
@RestController
@RequestMapping("/api")
@AllArgsConstructor
@Slf4j
@Tag(name = "Inscripciones",
        description = "Anotar participantes en carreras, aprobarlos, rechazarlos y darlos de baja")
public class RegistrationController {

    private final RegistrationService registrationService;

    // ------------------------------------------------------------------
    // RUTAS ANIDADAS: cuelgan de la carrera
    // ------------------------------------------------------------------

    /**
     * INSCRIBIR. POST /api/races/{raceId}/registrations
     *
     * La inscripcion nace PENDING: pedir un lugar no es tenerlo. Quien la aprueba
     * es el organizador, y recien ahi el participante cuenta para largar.
     */
    @PostMapping("/races/{raceId}/registrations")
    @Operation(summary = "Inscribir un participante en una carrera",
            description = """
                    Se manda **o** `competitorId` **o** `teamId`, nunca los dos ni ninguno.

                    La inscripcion nace en estado **PENDING**. El `lane` (carril de largada)
                    es opcional: si no viene, se asigna solo al aprobarla.

                    Se valida, en este orden, que la carrera este en
                    OPEN_FOR_REGISTRATION, que no haya pasado el cierre de inscripciones,
                    que quede cupo, que el carril este libre, que el tipo de participante
                    corresponda al tipo de carrera, que el competidor este ACTIVE (o que el
                    equipo lo este y tenga integrantes), que no este ya inscripto, y que
                    nadie compita a la vez por su cuenta y como parte de un equipo en la
                    misma carrera.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inscripcion registrada, en estado PENDING"),
            @ApiResponse(responseCode = "400", description = "Vinieron los dos ids, o ninguno, o el carril no es positivo"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador"),
            @ApiResponse(responseCode = "404", description = "No existe la carrera, el competidor o el equipo"),
            @ApiResponse(responseCode = "409", description = "Inscripciones cerradas, cupo lleno, carril ocupado, tipo de participante que no corresponde, participante no habilitado o ya inscripto")
    })
    public ResponseEntity<RegistrationResponse> register(
            @Parameter(description = "Id de la carrera") @PathVariable UUID raceId,
            @Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = registrationService.register(raceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * LISTAR las inscripciones de una carrera.
     * GET /api/races/{raceId}/registrations
     *
     * El orden por defecto es por fecha de inscripcion ascendente: el que pidio
     * primero aparece primero, que es como se revisa una lista de espera.
     */
    @GetMapping("/races/{raceId}/registrations")
    @Operation(summary = "Listar las inscripciones de una carrera",
            description = """
                    Devuelve una pagina con los anotados en la carrera.

                    El filtro `status` es opcional y sirve para las dos consultas que se
                    hacen todo el tiempo: `PENDING` para ver que falta aprobar y `APPROVED`
                    para ver la grilla de largada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La pagina de inscripciones"),
            @ApiResponse(responseCode = "404", description = "No existe una carrera con ese id")
    })
    public ResponseEntity<PageResponse<RegistrationResponse>> getByRace(
            @Parameter(description = "Id de la carrera") @PathVariable UUID raceId,

            @Parameter(description = "Filtrar por estado de la inscripcion", example = "PENDING")
            @RequestParam(required = false) RegistrationStatus status,

            @ParameterObject
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(registrationService.getByRace(raceId, status, pageable));
    }

    // ------------------------------------------------------------------
    // RUTAS PLANAS: sobre una inscripcion puntual
    // ------------------------------------------------------------------

    /** CONSULTAR una. GET /api/registrations/{id} */
    @GetMapping("/registrations/{id}")
    @Operation(summary = "Buscar una inscripcion por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La inscripcion"),
            @ApiResponse(responseCode = "404", description = "No existe una inscripcion con ese id")
    })
    public ResponseEntity<RegistrationResponse> getById(
            @Parameter(description = "Id de la inscripcion") @PathVariable UUID id) {
        return ResponseEntity.ok(registrationService.getById(id));
    }

    /**
     * APROBAR. PATCH /api/registrations/{id}/approve
     *
     * Es lo que convierte un pedido en un lugar en la grilla: solo las aprobadas
     * cuentan para el minimo de dos participantes que hace falta para largar.
     */
    @PatchMapping("/registrations/{id}/approve")
    @Operation(summary = "Aprobar una inscripcion",
            description = """
                    Pasa la inscripcion de PENDING a **APPROVED**.

                    Si la inscripcion no tenia carril asignado, se le da el siguiente libre
                    de la carrera. Solo se pueden aprobar inscripciones PENDING, y solo
                    mientras la carrera todavia este decidiendo quien corre: una vez que
                    largo, la grilla esta cerrada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripcion aprobada"),
            @ApiResponse(responseCode = "404", description = "No existe una inscripcion con ese id"),
            @ApiResponse(responseCode = "409", description = "La inscripcion no esta PENDING, o la carrera ya largo o termino")
    })
    public ResponseEntity<RegistrationResponse> approve(
            @Parameter(description = "Id de la inscripcion") @PathVariable UUID id) {
        return ResponseEntity.ok(registrationService.approve(id));
    }

    /**
     * RECHAZAR. PATCH /api/registrations/{id}/reject
     *
     * El motivo es OBLIGATORIO, y por eso este endpoint tiene cuerpo mientras que
     * approve no: el enunciado pide que "rejected registrations must include a
     * clear reason". Un rechazo sin explicacion deja al participante sin saber que
     * corregir.
     */
    @PatchMapping("/registrations/{id}/reject")
    @Operation(summary = "Rechazar una inscripcion",
            description = """
                    Pasa la inscripcion de PENDING a **REJECTED** y guarda el motivo, que es
                    obligatorio: sin `reason` la respuesta es 400.

                    Si tenia un carril asignado, se libera para que lo pueda usar otro
                    participante.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripcion rechazada"),
            @ApiResponse(responseCode = "400", description = "No se explico el motivo del rechazo"),
            @ApiResponse(responseCode = "404", description = "No existe una inscripcion con ese id"),
            @ApiResponse(responseCode = "409", description = "La inscripcion no esta PENDING, o la carrera ya largo o termino")
    })
    public ResponseEntity<RegistrationResponse> reject(
            @Parameter(description = "Id de la inscripcion") @PathVariable UUID id,
            @Valid @RequestBody RegistrationRejectRequest request) {
        return ResponseEntity.ok(registrationService.reject(id, request.reason()));
    }

    /**
     * DAR DE BAJA. DELETE /api/registrations/{id}
     *
     * Mismo criterio que en el resto del proyecto: no borra, pasa la inscripcion a
     * CANCELLED. Que alguien se haya bajado de una carrera es informacion, y
     * borrandola se pierde.
     */
    @DeleteMapping("/registrations/{id}")
    @Operation(summary = "Dar de baja una inscripcion",
            description = """
                    **No la borra de la base.** La pasa a estado CANCELLED y libera el
                    carril, si tenia uno.

                    A diferencia de aprobar y rechazar, se puede cancelar una inscripcion ya
                    aprobada: bajarse de una carrera antes de que largue es legitimo.

                    Es idempotente: cancelar una inscripcion ya cancelada devuelve 204
                    igual.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inscripcion dada de baja"),
            @ApiResponse(responseCode = "403", description = "Hay que ser administrador u organizador"),
            @ApiResponse(responseCode = "404", description = "No existe una inscripcion con ese id"),
            @ApiResponse(responseCode = "409", description = "La carrera ya largo o termino")
    })
    public ResponseEntity<Void> cancel(
            @Parameter(description = "Id de la inscripcion") @PathVariable UUID id) {
        registrationService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
