package com.eia.camelracing.audit.service;

import com.eia.camelracing.audit.dto.AuditLogResponse;
import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.entity.AuditLog;
import com.eia.camelracing.audit.mapper.AuditMapper;
import com.eia.camelracing.audit.repository.IAuditLogRepository;
import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * La bitacora del sistema: registra y consulta, nunca modifica.
 *
 * LO USAN TODOS LOS DEMAS SERVICIOS
 * Este es el unico servicio del proyecto que se inyecta en casi todos los otros.
 * CompetitorService, TeamService, RaceService, RegistrationService y ResultService
 * le avisan cuando pasa algo que vale la pena registrar.
 *
 * POR QUE EL REGISTRO VA EN LA MISMA TRANSACCION Y NO EN UNA APARTE
 * Se podria anotar registrar() con Propagation.REQUIRES_NEW para que la linea de
 * bitacora se guarde aunque la operacion falle. Esta hecho al reves a proposito:
 * el registro comparte la transaccion del que lo llama, asi que si la operacion se
 * deshace, la linea tambien.
 *
 * El motivo es que esta bitacora dice "esto paso". Una linea que afirma que se
 * cancelo una carrera, cuando en realidad la transaccion se deshizo y la carrera
 * sigue viva, es peor que no tener bitacora: es una bitacora que miente. Para
 * registrar intentos fallidos estan los logs de la aplicacion, que es otra cosa y
 * tiene otro proposito.
 *
 * La contrapartida honesta: si guardar la bitacora fallara, se caeria tambien la
 * operacion de negocio. Es aceptable porque el enunciado pide auditoria de las
 * acciones importantes, y una accion importante que no se pudo auditar es
 * justamente la que no conviene dejar pasar en silencio.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final IAuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    /**
     * El tope de los campos previousValue y newValue en la base.
     *
     * Se recorta aca en vez de dejar que falle el INSERT. Una descripcion cortada
     * es un detalle menor; una operacion de negocio que se cae porque el texto de
     * la bitacora era largo es un problema serio, y ademas dificil de entender
     * cuando pasa.
     */
    private static final int MAXIMO_VALOR = 1000;
    private static final int MAXIMO_DESCRIPCION = 500;

    // ------------------------------------------------------------------
    // REGISTRAR
    // ------------------------------------------------------------------

    /**
     * Registra una accion que no cambia valores: un alta, un login, una baja.
     *
     * El usuario no se pasa por parametro: sale del token, igual que en el resto
     * del sistema. Si el que llama pudiera elegir a quien atribuirle la accion, la
     * bitacora dejaria de servir como prueba.
     */
    @Transactional
    public void registrar(AuditAction action, String entityType, UUID entityId, String description) {
        registrar(action, entityType, entityId, description, null, null);
    }

    /**
     * Registra un cambio, con el antes y el despues.
     *
     * Los dos valores son textos cortos con lo que efectivamente cambio, del estilo
     * "status=ACTIVE". La explicacion de por que no se guarda la entidad entera
     * esta en la entidad AuditLog.
     */
    @Transactional
    public void registrar(AuditAction action, String entityType, UUID entityId,
                          String description, String previousValue, String newValue) {

        AuditLog log = AuditLog.builder()
                .username(currentUser.username())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(recortar(description, MAXIMO_DESCRIPCION))
                .previousValue(recortar(previousValue, MAXIMO_VALOR))
                .newValue(recortar(newValue, MAXIMO_VALOR))
                .build();

        auditLogRepository.save(log);
    }

    // ------------------------------------------------------------------
    // CONSULTAR
    // ------------------------------------------------------------------

    /**
     * La consulta de la bitacora, solo para administradores.
     *
     * Que sea solo para administradores no se decide aca sino en SecurityConfig,
     * donde /api/audit/** pide el rol ADMIN. El enunciado es explicito: "only
     * administrators may view the complete audit log". Tiene sentido: la bitacora
     * cuenta quien hizo cada cosa, y eso es informacion sobre las personas, no
     * sobre las carreras.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getLogs(String username, AuditAction action,
                                                  String entityType, UUID entityId,
                                                  LocalDateTime desde, LocalDateTime hasta,
                                                  Pageable pageable) {

        return PageResponse.of(
                auditLogRepository.buscar(normalizar(username), action, normalizar(entityType),
                        entityId, desde, hasta, pageable),
                AuditMapper::toResponse);
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /**
     * Deja el texto listo para comparar: sin espacios sobrantes y en minusculas, o
     * null si no vino.
     *
     * El null es importante: es lo que hace que la condicion ":username IS NULL"
     * de la consulta desactive el filtro. Un string vacio NO es lo mismo que un
     * filtro ausente, y sin esta normalizacion un "?username=" en la URL buscaria
     * literalmente al usuario que se llama "".
     */
    private String normalizar(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
    }

    private String recortar(String texto, int maximo) {
        if (texto == null) return null;
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
