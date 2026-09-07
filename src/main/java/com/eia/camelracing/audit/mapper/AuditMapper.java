package com.eia.camelracing.audit.mapper;

import com.eia.camelracing.audit.dto.AuditLogResponse;
import com.eia.camelracing.audit.entity.AuditLog;

/**
 * Traduce la entidad AuditLog a su DTO de salida.
 *
 * Tiene un solo metodo, y no dos como los otros mappers, porque la bitacora no
 * entra: se escribe desde adentro del sistema, con la entidad armada por
 * AuditService. No hay ningun toEntity() porque no hay ningun Request.
 */
public class AuditMapper {

    private AuditMapper() {}

    public static AuditLogResponse toResponse(AuditLog log) {
        if (log == null) return null;
        return new AuditLogResponse(
                log.getId(),
                log.getUsername(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOccurredAt(),
                log.getDescription(),
                log.getPreviousValue(),
                log.getNewValue()
        );
    }
}
