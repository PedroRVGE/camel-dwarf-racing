package com.eia.camelracing.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Quien esta haciendo esta operacion.
 *
 * POR QUE HACE FALTA
 * El enunciado pide guardar el usuario que hizo la inscripcion, el que cargo el
 * resultado y el que aparece en cada registro de auditoria. Ese dato no viaja en
 * el cuerpo del request —dejar que el cliente diga quien es seria absurdo,
 * cualquiera pondria el nombre de otro— sino que sale del token ya validado.
 *
 * COMO LO OBTIENE
 * Spring Security guarda la autenticacion del request actual en un
 * SecurityContextHolder, que por debajo es un ThreadLocal: cada request tiene el
 * suyo. Esta clase lo lee y saca los dos datos que interesan.
 *
 * POR QUE ES UN COMPONENTE Y NO UN METODO ESTATICO
 * Podria ser una clase de utilidad con metodos static, como los mappers. Se hace
 * bean a proposito: asi los servicios lo reciben por constructor y en un test
 * unitario se lo puede reemplazar por un doble que devuelva "admin" sin tener que
 * armar un SecurityContext a mano ni acordarse de limpiarlo despues, que es una
 * fuente clasica de tests que se contaminan entre si.
 *
 * SOBRE LOS DOS IDENTIFICADORES
 * Se exponen los dos porque sirven para cosas distintas:
 *   - username es lo que se le muestra a una persona ("aprobada por organizer").
 *   - id es el "sub" del token, el identificador de Keycloak, que NO cambia nunca.
 *     El username si se puede cambiar desde la consola de Keycloak, asi que un
 *     historial guardado solo con el nombre puede terminar atribuyendo acciones a
 *     alguien que ya no se llama asi.
 */
@Component
public class CurrentUser {

    /**
     * El nombre de usuario del token, o "sistema" si no hay nadie autenticado.
     *
     * El caso sin autenticacion no deberia darse en los endpoints protegidos, pero
     * puede pasar si manana algo corre fuera de un request: una tarea programada,
     * la carga de datos iniciales, un test. Devolver un valor por defecto en vez de
     * null evita que una operacion de negocio falle con NullPointerException por un
     * dato que es solo informativo.
     */
    public String username() {
        Jwt jwt = jwtActual();
        if (jwt == null) return "sistema";
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    /**
     * El identificador estable del usuario en Keycloak (el claim "sub").
     *
     * Puede ser null si no hay nadie autenticado. Ver la nota de arriba sobre por
     * que se guarda ademas del username.
     */
    public String id() {
        Jwt jwt = jwtActual();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * El token del request actual, o null si no hay ninguno.
     *
     * El instanceof con patron cubre los dos casos raros de una sola vez: que no
     * haya autenticacion (null) y que la haya pero no sea un JWT, que es lo que
     * pasa en los tests con @WithMockUser.
     */
    private Jwt jwtActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
