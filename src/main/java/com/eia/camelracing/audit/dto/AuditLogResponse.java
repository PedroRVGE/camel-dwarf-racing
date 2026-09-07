package com.eia.camelracing.audit.dto;

import com.eia.camelracing.audit.entity.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una linea de la bitacora, como se le devuelve al administrador.
 *
 * No hay ningun Request en este paquete, y es a proposito: la bitacora no se
 * escribe desde afuera. Las lineas las genera el propio sistema cuando pasa algo,
 * y no existe ningun endpoint para agregar, editar ni borrar una. Si hubiera un
 * POST /api/audit, cualquiera con permisos podria fabricar historia.
 */
@Schema(description = "Una accion registrada en la bitacora del sistema")
public record AuditLogResponse(

        UUID id,

        @Schema(description = "Usuario que ejecuto la accion", example = "admin")
        String username,

        @Schema(description = "Que se hizo", example = "RACE_STATUS_CHANGED")
        AuditAction action,

        @Schema(description = "Sobre que tipo de entidad", example = "Race")
        String entityType,

        @Schema(description = "Id de la entidad afectada, o null si la accion no es sobre ninguna")
        UUID entityId,

        @Schema(description = "Cuando paso")
        LocalDateTime occurredAt,

        @Schema(description = "Explicacion legible de lo que ocurrio",
                example = "Se cambio el estado de la carrera 'Gran Premio Alto de Las Palmas'")
        String description,

        @Schema(description = "Como estaba antes, o null si es un alta", example = "status=CLOSED_FOR_REGISTRATION")
        String previousValue,

        @Schema(description = "Como quedo, o null si es una baja", example = "status=IN_PROGRESS")
        String newValue
) {
}
