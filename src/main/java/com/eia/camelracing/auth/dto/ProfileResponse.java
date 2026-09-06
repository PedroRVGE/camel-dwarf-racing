package com.eia.camelracing.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Quien sos, segun el token que trajiste.
 *
 * Lo usa el frontend apenas se loguea, para dos cosas: mostrar el nombre en la
 * barra superior y, sobre todo, saber que botones dibujar. El enunciado pide que
 * "las opciones de menu esten ocultas o deshabilitadas cuando el rol no tiene
 * permiso", y para eso el frontend necesita saber los roles.
 *
 * OJO CON UNA COSA: que el frontend esconda un boton NO es seguridad. Cualquiera
 * puede volver a mostrarlo desde la consola del navegador, o llamar al endpoint
 * directo con curl. La seguridad de verdad esta en SecurityConfig, del lado del
 * servidor, y esto es solo para que la interfaz no le ofrezca al usuario cosas
 * que van a terminar en un 403.
 *
 * @param id        identificador del usuario en Keycloak (el claim "sub")
 * @param username  nombre de usuario con el que se logueo
 * @param firstName nombre
 * @param lastName  apellido
 * @param email     correo
 * @param roles     los roles de la aplicacion, en minuscula: admin, organizer, viewer
 */
@Schema(description = "Datos del usuario autenticado, leidos de su token")
public record ProfileResponse(

        @Schema(description = "Identificador del usuario en Keycloak",
                example = "8f14e45f-ceea-467a-9c1e-3f2b1a4d5e6f")
        String id,

        @Schema(description = "Nombre de usuario", example = "organizer")
        String username,

        @Schema(description = "Nombre", example = "Oscar")
        String firstName,

        @Schema(description = "Apellido", example = "Organizador")
        String lastName,

        @Schema(description = "Correo electronico", example = "organizer@camelracing.test")
        String email,

        @Schema(description = "Roles de la aplicacion que trae el token",
                example = "[\"organizer\", \"viewer\"]")
        List<String> roles
) {
}
