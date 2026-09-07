package com.eia.camelracing.audit.controller;

import com.eia.camelracing.audit.dto.AuditLogResponse;
import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * La bitacora del sistema.
 *
 * TIENE UN SOLO ENDPOINT, Y ES DE LECTURA
 * No hay POST, ni PUT, ni PATCH, ni DELETE. Las lineas las escribe el propio
 * sistema cuando ocurre algo; nadie las crea, las corrige ni las borra desde
 * afuera. Una bitacora con endpoints de escritura seria un cuaderno donde el
 * investigado puede tachar.
 *
 * SOLO ADMINISTRADORES
 * SecurityConfig exige el rol ADMIN para todo /api/audit/**, tal como pide el
 * enunciado ("only administrators may view the complete audit log"). Un organizador
 * puede manejar carreras, pero no le corresponde ver quien hizo que en todo el
 * sistema: eso es informacion sobre las personas.
 */
@RestController
@RequestMapping("/api/audit")
@AllArgsConstructor
@Slf4j
@Tag(name = "Auditoria",
        description = "Registro de las acciones importantes del sistema. Solo administradores")
public class AuditController {

    private final AuditService auditService;

    /**
     * CONSULTAR la bitacora. GET /api/audit
     *
     * El orden por defecto es por fecha DESCENDENTE, al reves que en el resto de
     * los listados del proyecto. Es a proposito: a una bitacora se le pregunta que
     * paso recien, no que paso el primer dia.
     */
    @GetMapping
    @Operation(summary = "Consultar la bitacora del sistema",
            description = """
                    Devuelve una pagina con las acciones registradas, de la mas reciente a
                    la mas vieja. Todos los filtros son opcionales y se combinan entre si.

                    Ejemplos de las preguntas que resuelve:

                    - que hizo un usuario: `?username=organizer`
                    - quien cancelo carreras: `?action=RACE_CANCELLED`
                    - todo lo que le paso a una carrera: `?entityType=Race&entityId=...`
                    - que se toco anoche: `?desde=2026-09-05T20:00:00&hasta=2026-09-06T02:00:00`

                    Las fechas van en formato ISO, sin zona horaria
                    (`2026-09-05T20:00:00`).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La pagina de la bitacora"),
            @ApiResponse(responseCode = "400", description = "Alguno de los filtros tiene un formato invalido"),
            @ApiResponse(responseCode = "401", description = "No se mando token, o no es valido"),
            @ApiResponse(responseCode = "403", description = "Solo un administrador puede ver la bitacora")
    })
    public ResponseEntity<PageResponse<AuditLogResponse>> getLogs(

            @Parameter(description = "Filtrar por usuario, exacto y sin distinguir mayusculas", example = "admin")
            @RequestParam(required = false) String username,

            @Parameter(description = "Filtrar por accion", example = "RACE_CANCELLED")
            @RequestParam(required = false) AuditAction action,

            @Parameter(description = "Filtrar por tipo de entidad", example = "Race")
            @RequestParam(required = false) String entityType,

            @Parameter(description = "Filtrar por la entidad puntual")
            @RequestParam(required = false) UUID entityId,

            @Parameter(description = "Desde esta fecha y hora, inclusive", example = "2026-09-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,

            @Parameter(description = "Hasta esta fecha y hora, inclusive", example = "2026-09-30T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,

            @ParameterObject
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                auditService.getLogs(username, action, entityType, entityId, desde, hasta, pageable));
    }
}
