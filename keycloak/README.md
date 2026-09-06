# Configuración de Keycloak

`realm-camelracing.json` es lo que Keycloak importa solo al arrancar, gracias al
`--import-realm` de `compose.yml`. Sin este archivo habría que crear a mano el
realm, los clientes, los roles y los usuarios desde la consola web cada vez que
se borra el contenedor.

La explicación va acá afuera y no adentro del JSON porque **JSON no admite
comentarios**, y el importador de Keycloak además es estricto: cualquier campo
que no conozca hace fallar el arranque entero con un
`Unrecognized field ... not marked as ignorable`. No los ignora en silencio.

## Por qué Keycloak y no un login propio

El enunciado permite las dos cosas: "Auth0, Keycloak, Amazon Cognito, JWT with
Spring Security, or another approved identity provider". Se eligió Keycloak, y
eso tiene una consecuencia que conviene entender antes de buscar código que no
existe:

**La aplicación no guarda usuarios ni contraseñas, y no emite tokens.** Cumple el
rol de *resource server*: recibe tokens ya firmados, verifica la firma contra la
clave pública de Keycloak y decide según los roles que vengan adentro. No hay
tabla `users`, no hay `password_hash`, no hay `JWT_SECRET`. Por eso tampoco
existen `POST /api/auth/register`, `/login` ni `/refresh`: esos endpoints tienen
sentido cuando la aplicación es la que emite los tokens, y acá los emite
Keycloak. El frontend habla directamente con Keycloak para loguearse y para
renovar el token; la API solo expone `GET /api/auth/profile`, que devuelve quién
sos según el token que trajiste.

Lo que el enunciado pide como "secure password storage" y "BCrypt or another
secure password-hashing algorithm" lo cumple Keycloak, que guarda las
credenciales con PBKDF2 y nunca se las muestra a nadie.

## Qué crea el realm

**Realm `camelracing`.** Un realm es un conjunto aislado de usuarios, roles y
aplicaciones. El realm `master` que trae Keycloak de fábrica es para administrar
Keycloak mismo; mezclar los usuarios de la app ahí adentro es un error común.

**Tres roles de realm: `admin`, `organizer` y `viewer`**, que son los tres que
pide el enunciado. Viajan dentro del token, en `realm_access.roles`, y
`SecurityConfig` los traduce a `ROLE_ADMIN`, `ROLE_ORGANIZER` y `ROLE_VIEWER`
para proteger las rutas.

| Rol | Permisos mínimos según el enunciado |
|---|---|
| `admin` | Gestiona usuarios, competidores, equipos, carreras, inscripciones, resultados y auditoría |
| `organizer` | Gestiona carreras, inscripciones y resultados; consulta competidores y equipos |
| `viewer` | Solo lectura: información pública, calendario, resultados y clasificación |

**Los roles se acumulan en el usuario, no en el código.** El usuario `admin`
tiene los tres roles asignados y `organizer` tiene `organizer` + `viewer`. La
alternativa era declarar una jerarquía en Spring Security (`RoleHierarchy`), pero
entonces el token diría una cosa y la aplicación entendería otra, y al mirar un
token en jwt.io no se sabría qué puede hacer realmente quien lo trae. Así, lo que
dice el token es exactamente lo que vale.

**Dos clientes.** El "cliente" es la aplicación que pide tokens, no el usuario:

- `camelracing-swagger` — la pantalla de Swagger, para probar la API a mano.
- `camelracing-web` — la interfaz gráfica de React.

Están separados porque tienen direcciones de retorno distintas, y mezclarlos
obligaría a que un solo cliente aceptara volver a cualquiera de los dos puertos.
Tres opciones de ambos que vale la pena entender:

- `publicClient: true` — sin secreto. Es lo correcto para algo que corre en el
  navegador: un secreto metido en JavaScript lo lee cualquiera con F12, así que
  no sería secreto. Lo que da la seguridad es PKCE.
- `pkce.code.challenge.method: S256` — PKCE obliga a que quien canjea el código
  de autorización sea el mismo que lo pidió. Es lo que reemplaza al secreto en un
  cliente público. Está en el realm porque `application.yml` configura a Swagger
  para usarlo (`use-pkce-with-authorization-code-grant: true`); si una punta lo
  pide y la otra no, el login falla.
- `directAccessGrantsEnabled: true` — habilita pedir un token mandando usuario y
  contraseña directo por HTTP (`grant_type=password`). Está prendido **solo para
  poder probar**: los tests e2e lo usan para conseguir un token firmado sin
  levantar un navegador, y sirve para verificar la API con `curl`. En algo real
  se apaga: obliga a que quien pide el token conozca la contraseña del usuario,
  que es justamente lo que OAuth2 viene a evitar.

### El scope `basic` no se puede olvidar

`defaultClientScopes` lista los scopes que Keycloak le aplica al cliente sin que
nadie los pida. Al escribirlo a mano se **reemplaza** la lista por defecto, y ahí
hay una trampa que cuesta encontrar: si no se incluye `basic`, los tokens salen
**sin el claim `sub`**.

`sub` es el identificador del usuario en Keycloak, y es el único dato de
identidad que no cambia nunca: el `preferred_username` y el correo los puede
editar un administrador, el `sub` no. Es lo que guarda el módulo de auditoría
para decir quién hizo cada cosa, y lo que devuelve `GET /api/auth/profile` como
`id`.

El síntoma es engañoso porque todo lo demás funciona: el login anda, los roles
llegan, la firma valida, y el campo `id` simplemente viene en `null` sin ningún
error en ningún log. Se descubrió justamente así, viendo un `"id": null` en la
respuesta del perfil.

**Tres usuarios**, que son los que el punto 8 del enunciado pide como datos
iniciales mínimos:

| Usuario | Contraseña | Roles | Qué puede hacer |
|---|---|---|---|
| `admin` | `admin123` | `admin`, `organizer`, `viewer` | todo, incluida la auditoría |
| `organizer` | `organizer123` | `organizer`, `viewer` | carreras, inscripciones y resultados |
| `viewer` | `viewer123` | `viewer` | solo leer; cualquier escritura le da 403 |

Estas credenciales están en el repositorio **a propósito**, para que cualquiera
clone el proyecto y lo levante sin configurar nada. No son un secreto filtrado:
son datos de demostración de un realm de desarrollo que vive en memoria. En algo
real los usuarios no se versionan, y la contraseña del panel de administración
sale del `.env` (`KEYCLOAK_ADMIN_PASSWORD`), que sí está excluido por
`.gitignore`.

## Los redirect URIs

`redirectUris` lista a dónde puede volver el navegador después de loguearse.
Keycloak rechaza cualquier dirección que no esté en la lista, y con razón: sin
esa validación, alguien podría armar un link que se loguee contra tu Keycloak y
mande el código de autorización a un servidor suyo.

- `camelracing-swagger` acepta el 8080 y el 8081 porque el puerto de la app es
  configurable con `APP_PORT`.
- `camelracing-web` acepta el 3000 (el contenedor de nginx, `WEB_PORT`) y el 5173
  (el servidor de desarrollo de Vite, para cuando se trabaja en el frontend sin
  reconstruir la imagen).

Si levantás alguno en otro puerto, hay que agregarlo acá o el login corta con
`Invalid parameter: redirect_uri`.

`webOrigins` es lo mismo pero para CORS: sin el origen en esa lista, el navegador
bloquea las llamadas del frontend a Keycloak antes de que salgan.

## Consola de administración

`http://localhost:8180` — usuario y contraseña salen de `KEYCLOAK_ADMIN` y
`KEYCLOAK_ADMIN_PASSWORD` en el `.env`. Esas credenciales son de la consola de
Keycloak y no tienen nada que ver con los usuarios de la app.

Los cambios que hagas desde la consola **se pierden** al recrear el contenedor:
Keycloak corre con `start-dev`, que usa una base H2 en memoria. Para que un
cambio quede, hay que reflejarlo en este JSON.

## Cómo pedir un token a mano

Sirve para probar la API con `curl` sin pasar por el navegador:

```bash
curl -s -X POST \
  http://localhost:8180/realms/camelracing/protocol/openid-connect/token \
  -d client_id=camelracing-swagger \
  -d username=admin -d password=admin123 \
  -d grant_type=password | jq -r .access_token
```

Ese token se pega en `Authorization: Bearer <token>`. Pegándolo en
[jwt.io](https://jwt.io) se ve el `realm_access.roles` que la aplicación lee para
decidir los permisos.
