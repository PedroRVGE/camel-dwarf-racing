package com.eia.camelracing.auth.controller;

import com.eia.camelracing.auth.dto.ProfileResponse;
import com.eia.camelracing.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Un solo endpoint: quien soy.
 *
 * POR QUE NO HAY /register, /login NI /refresh
 * El enunciado los sugiere, y esta bien que lo haga: son los que corresponden
 * cuando la aplicacion emite sus propios tokens. Este proyecto eligio la otra
 * opcion que el enunciado tambien permite —Keycloak como proveedor de identidad—
 * y ahi esos tres endpoints no tienen nada que hacer:
 *
 *   - /login    lo atiende Keycloak, en su propia pantalla. La contrasena no pasa
 *               nunca por esta aplicacion, que es todo el punto de usar OAuth2.
 *               Escribir un /api/auth/login que reciba la contrasena y se la
 *               reenvie a Keycloak seria volver a poner la aplicacion en el medio,
 *               justo lo que se evito.
 *   - /refresh  lo hace el frontend contra Keycloak, con el refresh token que
 *               recibio al loguearse. La aplicacion ni se entera.
 *   - /register lo hace un administrador desde la consola de Keycloak. Un endpoint
 *               publico de registro seria ademas un problema en si mismo: en una
 *               liga de carreras, los usuarios los da de alta la organizacion, no
 *               se auto-registra cualquiera.
 *
 * Queda /profile, que SI es tarea de la aplicacion: es la que sabe cuales de los
 * roles del token le importan al sistema.
 */
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
@Slf4j
@Tag(name = "Autenticacion",
        description = "Datos del usuario logueado. El login y la renovacion del token los hace Keycloak, no esta API")
public class AuthController {

    private final AuthService authService;

    /**
     * GET /api/auth/profile
     *
     * @AuthenticationPrincipal Jwt inyecta el token ya validado. No hace falta
     * leer la cabecera Authorization a mano ni verificar nada: si el request llego
     * hasta aca, la firma, el emisor y la expiracion ya se comprobaron en la
     * cadena de filtros.
     *
     * No devuelve 401 en ningun caso desde este metodo: si no hubiera token, el
     * request se habria frenado antes (ver SecurityConfig, /api/auth/**
     * authenticated) y la respuesta la habria armado SecurityErrorResponder. El
     * @ApiResponse de 401 esta igual porque es informacion util para quien lee la
     * documentacion.
     */
    @GetMapping("/profile")
    @Operation(summary = "Datos del usuario autenticado",
            description = """
                    Devuelve quien sos segun el token que mandaste, con los roles que tenes
                    en el sistema.

                    Lo usa la interfaz grafica para mostrar tu nombre y para decidir que
                    acciones ofrecerte. Tener en cuenta que esconder un boton NO es
                    seguridad: los permisos se aplican en el servidor, en cada endpoint.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos del usuario"),
            @ApiResponse(responseCode = "401", description = "No se mando token, o no es valido")
    })
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.getProfile(jwt));
    }
}
