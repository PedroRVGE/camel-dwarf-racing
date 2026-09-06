package com.eia.camelracing.auth.service;

import com.eia.camelracing.auth.dto.ProfileResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

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
 * No lleva @Transactional ni repositorio: no hay nada que buscar en la base. Todo
 * lo que responde este servicio ya viene adentro del token, firmado por Keycloak.
 * Esa es justamente la gracia de un JWT: la aplicacion sabe quien sos sin
 * consultarle a nadie.
 */
@Service
public class AuthService {

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
    public ProfileResponse getProfile(Jwt jwt) {
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
