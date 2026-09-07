package com.eia.camelracing.auth.service;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.service.AuditService;
import com.eia.camelracing.auth.dto.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce el token de Keycloak a los datos de perfil que muestra la interfaz.
 *
 * Es el servicio mas chico del proyecto y existe igual, en vez de resolver esto
 * en tres lineas dentro del controlador, porque el enunciado es explicito:
 * "Controllers must not contain business logic". Y aunque parezca poca cosa, aca
 * SI hay una decision de negocio: cuales roles se le muestran al usuario y cuales
 * no (ver rolesDeLaAplicacion mas abajo).
 *
 * No consulta ningun repositorio para responder: todo lo que devuelve ya viene
 * adentro del token, firmado por Keycloak. Esa es justamente la gracia de un JWT,
 * la aplicacion sabe quien sos sin consultarle a nadie.
 *
 * Lo unico que si escribe es la linea de la bitacora que deja constancia de que
 * este usuario empezo a usar el sistema, y por eso el metodo lleva @Transactional.
 * La explicacion de por que ese registro esta aca y no en un endpoint de login
 * —que no existe— esta sobre getProfile().
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuditService auditService;

    /**
     * Los roles que le importan a esta aplicacion.
     *
     * Keycloak mete en el token varios roles propios que no significan nada aca:
     * "offline_access", "uma_authorization" y "default-roles-camelracing". Si se
     * devolvieran tal cual, el frontend tendria que saber cuales ignorar, y la
     * pantalla de perfil le mostraria al usuario tres roles que no puede explicar
     * nadie.
     *
     * Filtrar aca deja una sola definicion de "los roles del sistema" y hace que
     * agregar un rol nuevo sea tocar un solo lugar.
     */
    private static final Set<String> ROLES_DE_LA_APLICACION = Set.of("admin", "organizer", "viewer");

    /**
     * Arma el perfil a partir del token ya validado.
     *
     * El Jwt que llega aca YA paso por la verificacion de firma, emisor y
     * expiracion: si algo de eso hubiera fallado, el request nunca habria llegado
     * al controlador. Por eso se puede confiar en el contenido sin volver a
     * chequear nada.
     *
     * Los nombres de los claims (preferred_username, given_name, family_name) son
     * del estandar OpenID Connect, no inventos de Keycloak. Cualquier otro
     * proveedor de identidad usaria los mismos.
     */
    /**
     * Quien sos, segun el token que mandaste.
     *
     * ACA SE REGISTRA EL "LOGIN" DE LA BITACORA, Y HAY QUE SER PRECISO CON ESO
     * El enunciado pide que la auditoria registre el login. Esta aplicacion no hace
     * login: la contrasena la valida Keycloak, que emite el token y lleva su propia
     * bitacora de accesos. Lo unico que este backend puede ver es la primera vez que
     * ese token se usa contra la API, y eso es exactamente esta llamada: la interfaz
     * grafica pide /api/auth/profile apenas vuelve de Keycloak, para saber a quien
     * mostrar y que botones ofrecer.
     *
     * O sea que la linea LOGIN de la bitacora significa "este usuario empezo a usar
     * el sistema", no "este usuario escribio bien su contrasena". Es una diferencia
     * que conviene tener clara al leer la auditoria: si alguien intenta entrar con
     * una contrasena equivocada, eso no aparece aca, aparece en Keycloak.
     *
     * La consecuencia practica es que un frontend que llamara a este endpoint en
     * cada pantalla llenaria la bitacora de lineas repetidas. Por eso se pide una
     * sola vez, al arrancar la sesion.
     */
    @Transactional
    public ProfileResponse getProfile(Jwt jwt) {
        auditService.registrar(AuditAction.LOGIN, "User", null,
                "El usuario '" + jwt.getClaimAsString("preferred_username")
                        + "' empezo a usar el sistema");

        return new ProfileResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email"),
                extraerRoles(jwt)
        );
    }

    /**
     * Saca los roles de realm_access.roles y se queda solo con los del sistema.
     *
     * Repite la lectura del claim que ya hace KeycloakRolesConverter en vez de
     * reusar las authorities, y es a proposito: aquellas vienen con el prefijo
     * ROLE_ y en mayuscula, que es un detalle interno de Spring Security. El
     * frontend no tiene por que conocerlo, y si manana se cambia el prefijo, la
     * respuesta de este endpoint cambiaria sola sin que nadie lo pida.
     *
     * El instanceof con patron cubre el caso del usuario sin ningun rol asignado:
     * ahi realm_access puede no venir, y sin esto seria un NullPointerException.
     */
    private List<String> extraerRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(Object::toString)
                .filter(ROLES_DE_LA_APLICACION::contains)
                .sorted()
                .toList();
    }
}
