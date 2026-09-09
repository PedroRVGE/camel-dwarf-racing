# Liga EIA de Carreras

Sistema de gestión de la liga de carreras de camellos contra enanos de la
Universidad EIA. Reemplaza a `final_races_v2_FINAL_NOW_THIS_ONE.xlsx`, que se
perdió porque alguien ordenó mal una columna.

Es una API REST en Java con una interfaz web que la consume, base de datos
persistente, autenticación con roles y todo corriendo en contenedores. Trabajo de
la materia Implementación de Software.

Lo hicimos Sebastián Pérez, Samuel Ramírez y Pedro Rueda.

---

## Levantarlo

Hace falta Docker Desktop andando y nada más: ni Java, ni Node, ni Postgres
instalados en la máquina. Todo se compila adentro de los contenedores.

```bash
cp .env.example .env      # completá las contraseñas
docker compose up -d
```

La primera vez tarda unos minutos porque compila el backend, el frontend y baja
las imágenes. Después arranca en segundos.

Cuando termina, esto es lo que hay:

| Dónde | Qué es |
|---|---|
| http://localhost:3000 | La interfaz. Es por acá por donde se usa el sistema. |
| http://localhost:8080/swagger-ui.html | La documentación de la API, con botón para probarla. |
| http://localhost:8080/actuator/health | El healthcheck que consulta Docker. |
| http://localhost:8180 | La consola de Keycloak, donde viven los usuarios. |
| localhost:5432 | Postgres, publicado para poder mirar los datos con DBeaver o psql. |

Para ver cómo va:

```bash
docker compose ps          # los cuatro contenedores y su estado de salud
docker compose logs -f app # los logs de la API
```

Para apagarlo, `docker compose down`. Los datos sobreviven porque están en un
volumen. Para borrarlos también, `docker compose down -v`, que hay que pedir a
propósito.

### Si algún puerto está ocupado

Cambiá el número en el `.env` y listo, no hay que tocar ningún otro archivo:

```
DB_PORT=5433
APP_PORT=8090
KEYCLOAK_PORT=8190
WEB_PORT=3001
```

La única excepción es `API_BASE_URL`, que hay que dejar apuntando al mismo puerto
que `APP_PORT`. Es la dirección que usa el navegador para hablarle a la API, y por
eso no se puede derivar sola.

---

## Variables de entorno

Todas viven en el `.env`, que no se versiona. La plantilla es `.env.example`, que
trae los mismos nombres con los comentarios largos; acá está el resumen.

| Variable | Qué hace | Ejemplo |
|---|---|---|
| `DB_NAME` | Nombre de la base que Postgres crea la primera vez | `camelracing` |
| `DB_USER` | Dueño de esa base, y el usuario con el que se conecta la API | `camelracing` |
| `DB_PASSWORD` | Su contraseña. No tiene valor por defecto: si falta, la base no arranca | `cambiame` |
| `DB_PORT` | Puerto de Postgres en tu máquina | `5432` |
| `APP_PORT` | Puerto de la API en tu máquina | `8080` |
| `KEYCLOAK_PORT` | Puerto de Keycloak en tu máquina | `8180` |
| `WEB_PORT` | Puerto de la interfaz en tu máquina | `3000` |
| `KEYCLOAK_ADMIN` | Usuario de la consola de Keycloak, no de la aplicación | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | Su contraseña | `cambiame` |
| `API_BASE_URL` | Dirección de la API vista desde el navegador. Va con el mismo puerto que `APP_PORT` | `http://localhost:8080` |
| `TEAM_MAX_MEMBERS` | Máximo de integrantes que se le pueden poner a un equipo | `5` |

Los cuatro puertos son los del host. Adentro de los contenedores son fijos y no
dependen de esto, así que cambiarlos no toca nada más que la dirección por la que
entrás. Los dos `cambiame` son eso, un recordatorio: son las únicas dos
contraseñas que hay que inventar.

Hay otras variables que la aplicación lee y que no van en el `.env` porque compose
las arma sola a partir de las de arriba: `DB_URL`, `DB_USERNAME`,
`KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI`, `KEYCLOAK_PUBLIC_URL`,
`KEYCLOAK_REALM`, `CORS_ALLOWED_ORIGINS` y `SPRING_PROFILES_ACTIVE`. Escribirlas a
mano no agrega nada y habilita que queden contradiciendo a las otras.

Tres que el enunciado menciona no existen acá, y no es un olvido. `DB_HOST` es
siempre `db` adentro de la red de Docker, porque así se llama el servicio en
`compose.yml`; hacerlo configurable solo daría la posibilidad de romperlo.
`JWT_SECRET` y `JWT_EXPIRATION` tendrían sentido si la aplicación firmara los
tokens, y no los firma: los emite Keycloak con un par de claves RSA propio y la API
se limita a bajar la clave pública para verificar la firma. Dejar un `JWT_SECRET`
sin usar en el `.env` sería peor que no tenerlo, porque haría pensar que hay una
clave compartida donde no hay ninguna. La duración del token se configura en el
realm, en `keycloak/realm-camelracing.json`.

---

## Con qué usuario entrar

Los usuarios no están en la base de datos: los crea Keycloak al arrancar, a partir
de `keycloak/realm-camelracing.json`.

| Usuario | Contraseña | Qué puede hacer |
|---|---|---|
| `admin` | `admin123` | Todo, incluida la bitácora de auditoría |
| `organizer` | `organizer123` | Crear y operar carreras, aprobar inscripciones, cargar resultados |
| `viewer` | `viewer123` | Solo leer |

Son credenciales de demostración y están acá porque este repositorio es un trabajo
de clase. En un sistema real los usuarios no se versionan.

---

## El recorrido completo

Es el escenario que sugiere el enunciado, y sirve tanto para conocer el sistema
como para grabar la demostración. Todo se hace desde http://localhost:3000, sin
Postman.

1. Entrá como `admin`. El botón te manda a Keycloak, escribís ahí tus datos y
   volvés.
2. En Competidores, creá un camello llamado Byte.
3. Creá cinco enanos: Null Pointer, Stack Overflow, Little Lambda, Captain Cache y
   Tiny Docker.
4. En Equipos, creá The Five Exceptions y agregales los cinco enanos.
5. Salí y entrá como `organizer`. Vas a notar que el botón de crear competidores
   ya no está: no es que falle, directamente no aparece.
6. Creá una carrera mixta de un kilómetro y abrile las inscripciones.
7. Anotá a Byte y a The Five Exceptions, y aprobá las dos inscripciones.
8. Entrá como `viewer` en otra ventana e intentá abrir la edición de la carrera.
   Vas a caer en la pantalla de acceso denegado, que además te dice con qué rol
   entraste.
9. De vuelta como `organizer`, cerrá las inscripciones y largá la carrera.
10. Antes de cargar nada, intentá darla por terminada. La API la rechaza y dice
    cuántos resultados faltan.
11. Cargá los resultados: Byte primero, y The Five Exceptions abandonó porque Tiny
    Docker paró a mitad de camino alegando que en su máquina funcionaba.
12. Ahora sí, dala por terminada, y mirá la tabla de posiciones actualizada.
13. Volvé a entrar como `admin` y abrí la Bitácora: está registrado todo lo
    anterior, con quién lo hizo y cuándo.

---

## Cómo está hecho

Backend en Java 25 con Spring Boot 4.1, construido con Gradle. Postgres 15 para
los datos, con el esquema versionado en Flyway. Keycloak 26 para la
autenticación. La interfaz es React 19 con Vite, servida por nginx.

El backend está organizado **por dominio y no por capa**. No hay un paquete
`controller` con todos los controladores adentro: hay un paquete por entidad de
negocio, y dentro de cada uno están sus capas.

```
src/main/java/com/eia/camelracing/
├── auth/           el perfil del usuario autenticado
├── competitor/     competidores
├── team/           equipos
├── race/           carreras
├── registration/   inscripciones
├── result/         resultados
├── standings/      tabla de posiciones
├── audit/          bitácora
└── common/         configuración, seguridad y manejo de errores
```

Cada módulo tiene `controller`, `service`, `repository`, `entity`, `dto` y
`mapper`. La ventaja de esta organización es que para tocar las carreras se abre
una carpeta y está todo ahí, en vez de saltar entre cinco carpetas lejanas.

Tres cosas que vale la pena saber antes de leer el código:

Las entidades nunca salen de la aplicación. Todo lo que entra y sale son `record`
de Java en el paquete `dto`, y la traducción la hacen mappers escritos a mano, sin
librerías. Exponer una entidad de JPA sería atarle el contrato de la API al
esquema de la base.

Los controladores no tienen lógica. Reciben, validan el formato, llaman al
servicio y devuelven. Toda regla de negocio está en los servicios, que es donde se
la busca.

El esquema lo maneja Flyway y Hibernate arranca en modo `validate`. Si una entidad
deja de coincidir con su tabla, la aplicación no levanta en lugar de modificar la
base por su cuenta.

---

## La API

Treinta y cuatro endpoints repartidos en ocho módulos. La documentación completa,
con los esquemas de cada campo y un botón para probar cada uno, está en
http://localhost:8080/swagger-ui.html.

| Método y ruta | Qué hace | Quién puede |
|---|---|---|
| `GET /api/auth/profile` | El perfil del usuario de la sesión | cualquiera autenticado |
| `GET /api/competitors` | Listado con búsqueda, filtros, paginación y orden | cualquiera |
| `POST /api/competitors` | Alta | admin |
| `GET /api/competitors/{id}` | Ficha con estadísticas | cualquiera |
| `PUT /api/competitors/{id}` | Edición | admin |
| `PATCH /api/competitors/{id}/status` | Cambio de estado | admin |
| `DELETE /api/competitors/{id}` | Retiro (baja lógica) | admin |
| `GET /api/teams` | Listado de equipos | cualquiera |
| `POST /api/teams` | Alta | admin |
| `GET /api/teams/{id}` | Detalle con integrantes | cualquiera |
| `PUT /api/teams/{id}` | Edición | admin |
| `PATCH /api/teams/{id}/status` | Cambio de estado | admin |
| `DELETE /api/teams/{id}` | Desactivación (baja lógica) | admin |
| `POST /api/teams/{id}/members/{competidor}` | Agregar integrante | admin |
| `DELETE /api/teams/{id}/members/{competidor}` | Quitar integrante | admin |
| `GET /api/races` | La agenda, con filtros y orden | cualquiera |
| `POST /api/races` | Alta (nace en borrador) | admin, organizer |
| `GET /api/races/{id}` | Detalle con cupo e inscriptos | cualquiera |
| `PUT /api/races/{id}` | Edición | admin, organizer |
| `PATCH /api/races/{id}/status` | Avance de estado | admin, organizer |
| `DELETE /api/races/{id}` | Cancelación | admin, organizer |
| `POST /api/races/{id}/registrations` | Anotar un participante | admin, organizer |
| `GET /api/races/{id}/registrations` | Inscriptos de una carrera | cualquiera |
| `GET /api/registrations/{id}` | Detalle de una inscripción | cualquiera |
| `PATCH /api/registrations/{id}/approve` | Aprobar y asignar carril | admin, organizer |
| `PATCH /api/registrations/{id}/reject` | Rechazar con motivo | admin, organizer |
| `DELETE /api/registrations/{id}` | Dar de baja | admin, organizer |
| `POST /api/races/{id}/results` | Cargar un resultado | admin, organizer |
| `GET /api/races/{id}/results` | Resultados de una carrera | cualquiera |
| `GET /api/results/{id}` | Detalle de un resultado | cualquiera |
| `PUT /api/results/{id}` | Corregir un resultado | admin, organizer |
| `GET /api/standings/competitors` | Clasificación individual | cualquiera |
| `GET /api/standings/teams` | Clasificación por equipos | cualquiera |
| `GET /api/audit` | Bitácora, con filtros | admin |

Todos los listados aceptan `page`, `size` y `sort`, y devuelven la misma
envoltura: `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y
`last`.

Todos los errores tienen la misma forma, con `validationErrors` agregándose
solamente cuando el problema es de validación:

```json
{
  "timestamp": "2026-09-07T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Error de validacion en los datos enviados",
  "path": "/api/competitors",
  "validationErrors": { "weightKg": "El peso tiene que ser mayor que cero" }
}
```

### Ejemplos con curl

Todo empieza por un token: salvo Swagger y el healthcheck, no hay endpoint que
conteste sin uno. Se lo pide a Keycloak.

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/camelracing/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=camelracing-web \
  -d username=admin \
  -d password=admin123 \
  | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
```

Con `jq` instalado, `| jq -r .access_token` hace lo mismo y se lee mejor. Los
ejemplos son de Bash: en PowerShell las comillas simples de los `-d` llegan de otra
forma y hay que reescribirlos, así que desde Windows conviene usar Swagger o la
colección de Postman.

Pedir el token con usuario y contraseña está habilitado para poder probar desde la
consola. La interfaz no lo usa: manda el navegador a la pantalla de Keycloak, que
es lo que corresponde y lo único que sigue sirviendo si mañana se agrega un segundo
factor o un proveedor externo.

Listar competidores, filtrando por categoría y ordenando por apodo:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/competitors?type=DWARF&size=5&sort=nickname,asc"
```

Crear uno:

```bash
curl -s -X POST http://localhost:8080/api/competitors \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Byte",
        "nickname": "El Camello Cuantico",
        "type": "CAMEL",
        "dateOfBirth": "2019-04-12",
        "weightKg": 612.50,
        "heightCm": 195.00,
        "countryOfOrigin": "Envigado"
      }'
```

Devuelve 201 con el competidor y su `id`. La segunda vez devuelve 409, porque el
apodo ya está tomado.

La tabla de posiciones, que es la consulta que más se mira:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/standings/competitors?size=10"
```

```json
{
  "content": [
    { "position": 1, "participantName": "Byte", "points": 10,
      "racesFinished": 1, "victories": 1, "defeats": 0 }
  ],
  "page": 0, "size": 10, "totalElements": 9, "totalPages": 1,
  "first": true, "last": true
}
```

Los errores también sirven como ejemplo, porque son los que más se ven. Sin token:

```bash
curl -s http://localhost:8080/api/competitors
```

```json
{
  "timestamp": "2026-09-09T19:23:41.112893",
  "status": 401,
  "error": "Unauthorized",
  "message": "Hace falta un token valido para acceder a este recurso",
  "path": "/api/competitors"
}
```

Con un token de `viewer` intentando crear un competidor —el mismo pedido de arriba,
pero pidiendo el token con `username=viewer` y `password=viewer123`:

```json
{
  "timestamp": "2026-09-09T19:23:41.014175",
  "status": 403,
  "error": "Forbidden",
  "message": "Tu rol no tiene permiso para esta operacion",
  "path": "/api/competitors"
}
```

Con datos inválidos —apodo vacío, peso negativo y fecha de nacimiento en el
futuro— la respuesta dice qué campo tiene qué problema, en lugar de un "datos
incorrectos" que obligue a adivinar:

```json
{
  "timestamp": "2026-09-09T19:23:41.910620",
  "status": 400,
  "error": "Bad Request",
  "message": "Error de validacion en los datos enviados",
  "path": "/api/competitors",
  "validationErrors": {
    "name": "El nombre debe tener entre 2 y 150 caracteres",
    "nickname": "El apodo debe tener entre 2 y 100 caracteres",
    "weightKg": "El peso tiene que ser mayor que cero",
    "dateOfBirth": "La fecha de nacimiento tiene que estar en el pasado"
  }
}
```

Y el 409, que es el que aparece cuando el pedido está bien escrito pero va contra
una regla: largar una carrera con un solo inscripto, repetir un apodo, anotar a
alguien dos veces, o esto, que es darla por terminada faltando resultados.

```bash
curl -s -X PATCH "http://localhost:8080/api/races/$CARRERA/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "COMPLETED"}'
```

```json
{
  "timestamp": "2026-09-09T19:24:02.551830",
  "status": 409,
  "error": "Conflict",
  "message": "La carrera no se puede dar por terminada: faltan cargar 1 resultado(s). Los participantes que no corrieron van como DID_NOT_START",
  "path": "/api/races/.../status"
}
```

### Probar la API sin la interfaz

En `docs/coleccion-postman.json` hay una colección de Postman con los 53 pedidos.
Se importa, se aprieta Run y pasa entera: está ordenada según el ciclo de vida de
una carrera, así que crea el catálogo, arma una carrera, anota participantes,
carga resultados y consulta la clasificación, en ese orden.

Incluye una carpeta con los cinco errores que el sistema maneja (401, 403, 404,
400 y 409), cada uno con su comprobación.

---

## Las reglas

Las que decidieron el diseño y las que más se notan al usar el sistema.

Una carrera avanza de a un estado y no se saltea ninguno: borrador, inscripciones
abiertas, inscripciones cerradas, en curso, terminada. Desde cualquiera menos las
dos últimas se puede cancelar. Cada estado habilita cosas distintas, y por eso el
orden importa: solo se anota gente con las inscripciones abiertas y solo se cargan
resultados con la carrera en curso.

No se puede largar una carrera con menos de dos participantes aprobados, ni darla
por terminada mientras falte cargar el resultado de alguno. En ese último caso el
mensaje dice cuántos faltan, porque un "operación inválida" a secas obliga a ir a
leer el código.

El apodo de un competidor es único en toda la liga. Un competidor suspendido,
lesionado o retirado no se puede inscribir. Nadie se anota dos veces en la misma
carrera, ni después del cierre de inscripciones, ni cuando el cupo está lleno.

No puede haber dos primeros puestos en la misma carrera, y el tiempo tiene que ser
coherente con el puesto: el que llegó segundo no puede haber tardado menos que el
primero. Los puntos salen del puesto, diez al primero y después siete, cinco, tres
y uno; del sexto en adelante, cero.

El que no largó no suma derrota. No compitió, así que no perdió. El descalificado
y el que abandonó sí.

Nada se borra. Las bajas son lógicas: un competidor pasa a retirado, un equipo a
inactivo, una carrera a cancelada. Si se borrara un competidor habría que borrar
sus resultados, y con eso cambiarían los puestos de carreras que ya se corrieron.

Todo cambio queda registrado en la bitácora con el usuario, la fecha, el valor
anterior y el nuevo.

---

## La seguridad

La aplicación no guarda contraseñas y no emite tokens. Eso lo hace Keycloak, que
es un servidor de identidad hecho para el trabajo. La API recibe un token ya
firmado, baja la clave pública del emisor, verifica la firma y traduce los roles.
No hay tabla `users`, no hay columna de contraseña, no hay clave de firma en
ningún archivo de configuración.

La consecuencia práctica es que no existe `/api/auth/login`, y que ni el backend
ni el frontend ven jamás una contraseña. El botón "Entrar" de la interfaz manda el
navegador a la pantalla de Keycloak y lo trae de vuelta con un token.

Los tres roles del enunciado son roles del realm: `admin`, `organizer` y `viewer`.
Se acumulan en el usuario, no en el código: `admin` tiene los tres asignados, así
que no hace falta ninguna jerarquía en Java.

En el frontend, el token vive en memoria y no en `localStorage`. Cualquier script
que llegue a ejecutarse en la página puede leer `localStorage` entero y llevarse
una credencial válida; en memoria muere al cerrar la pestaña, y la sesión se
recupera al recargar preguntándole a Keycloak si su cookie sigue viva.

Los detalles están en `keycloak/README.md`.

---

## Las pruebas

```bash
./gradlew test                    # las 71, en tres capas
./gradlew test -Pcapa=unit        # solo las unitarias: 44, en segundos, sin Docker
./gradlew test -Pcapa=integracion # las de base de datos: 24, necesitan Docker
./gradlew test -Pcapa=e2e         # el sistema completo: 3, necesitan Docker
```

El informe queda en `build/reports/tests/test/index.html`.

Las de integración y las de punta a punta levantan sus propios contenedores con
Testcontainers y los apagan al terminar. No usan H2 ni siquiera como atajo: la
consulta de la tabla de posiciones agrupa de una forma que H2 acepta y Postgres
no, la de la bitácora esquiva un problema de tipos que solo existe en Postgres, y
las migraciones usan sintaxis que H2 no entiende. Probar contra un motor distinto
del de producción sería probar otra cosa.

El enunciado pide quince pruebas significativas y las nombra una por una. Están
las quince, y `src/test/README.md` tiene la tabla que dice dónde está cada una.

---

## Dónde está cada cosa

```
camel-dwarf-racing/
├── src/main/java/          el backend, un paquete por dominio
├── src/main/resources/
│   ├── db/migration/       el esquema, versionado con Flyway
│   ├── db/seed/            datos de ejemplo (solo en desarrollo y Docker)
│   └── application*.yml    configuración por perfiles
├── src/test/               las 71 pruebas, en tres capas
├── frontend/               la interfaz en React, con su Dockerfile y su nginx
├── keycloak/               el realm que se importa al arrancar
├── docs/                   modelo de datos y colección de Postman
├── compose.yml             los cuatro contenedores
└── Dockerfile              la imagen del backend
```

Cada carpeta importante tiene su propio README con el detalle:

- `docs/modelo-de-datos.md`: el diagrama entidad-relación y por qué el esquema es
  como es.
- `docs/coleccion-postman.json`: la colección para probar la API.
- `keycloak/README.md`: cómo funciona la autenticación y cómo se toca el realm.
- `frontend/README.md`: la estructura de la interfaz y sus decisiones.
- `src/test/README.md`: las tres capas de pruebas y qué cubre cada una.

---

## Trabajar en el código

Para el backend, con Java 25 instalado:

```bash
docker compose up -d db keycloak    # solo la infraestructura
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Para el frontend, con el resto del sistema ya levantado:

```bash
cd frontend
npm install
npm run dev
```

Queda en http://localhost:5173 con recarga automática, contra el mismo backend que
corre en Docker. Ese puerto ya está declarado en el realm de Keycloak y en la
configuración de CORS de la API, así que no hay que tocar nada.

---

## Limitaciones conocidas

Corre en una sola instancia y da por sentado que es la única. Las estadísticas de
cada competidor y de cada equipo están guardadas en su propia fila y no calculadas
al momento, y se actualizan dentro de la misma transacción que carga el resultado.
Con dos copias de la API atendiendo a la vez, dos resultados de la misma carrera
cargados en el mismo instante pueden pisarse el contador. No hay bloqueo optimista
para evitarlo, porque para la liga de una materia agrega una complejidad que
todavía no se paga.

La bitácora sirve para leer, no para reconstruir. Guarda quién, cuándo, sobre qué
entidad y los valores viejo y nuevo como texto, pero el id de la entidad afectada
no tiene clave foránea a ninguna tabla, porque según el caso apunta a seis tablas
distintas. Desde ahí no se puede deshacer un cambio.

Los usuarios están solo en Keycloak, y eso también tiene un costo. La API no puede
listar usuarios ni mostrar el perfil de alguien que no sea el que está conectado.
La bitácora guarda el nombre de usuario tal como venía en el token, así que si en
Keycloak se renombra a una persona, los registros viejos quedan con el nombre
anterior.

El frontend no tiene pruebas automatizadas. Las 71 pruebas cubren el backend
entero, incluidas las de punta a punta contra un Postgres y un Keycloak de verdad,
pero la interfaz se verificó a mano y con un render sin cabeza que confirma que
monta y no tira errores. Un cambio que rompa un componente no hace fallar ninguna
prueba, y debería.

No hay integración continua ni despliegue. El proyecto se levanta con Docker
Compose en la máquina de quien lo corre; no hay nada que ejecute las pruebas en
cada pull request, ni imágenes publicadas en ningún registro.

Nada avisa nada. No hay correos, ni notificaciones, ni actualizaciones en vivo: si
alguien carga un resultado, el resto lo ve cuando recarga la página.

La base y la API están en inglés, y el código y los mensajes en español. Es a
propósito, porque los nombres de tablas y campos se leen igual en cualquier parte y
los mensajes los lee el usuario, pero es una mezcla; en un equipo más grande habría
que dejarlo escrito en algún lado antes de que cada uno elija distinto.

## Mejoras futuras

Lo primero sería exportar a CSV la tabla de posiciones y los resultados de una
carrera. Este sistema reemplaza una planilla de Excel, y lo primero que va a pedir
quien la usaba es una forma de volver a sacar los datos a una planilla.

Después, integración continua: un workflow que en cada pull request compile y corra
las 71 pruebas. Testcontainers ya funciona sin configuración extra en los runners
de GitHub, así que es más trabajo escribir el archivo que resolver el problema.

Pruebas del frontend con Vitest y Testing Library, empezando por las que más valen:
que `RutaProtegida` mande al login sin sesión y a acceso denegado sin permiso, y que
el cliente de la API reparta bien los errores de validación entre los campos del
formulario.

Seguimiento en vivo de la carrera en curso con server-sent events, que para este
caso alcanza y es bastante más simple que websockets: el servidor manda y el
navegador escucha, que es justo lo que hace falta.

Y si la liga creciera, mover las estadísticas a una vista materializada que se
refresque al terminar cada carrera. Se dejarían de escribir contadores a mano, con
lo que desaparece la posibilidad de que queden desfasados, y la tabla de posiciones
seguiría respondiendo igual de rápido.

---

## Si algo no arranca

**La API se cae al levantar y los logs dicen algo de Keycloak.** La aplicación
necesita bajar la clave pública de Keycloak para poder validar tokens, y Keycloak
tarda cerca de un minuto en estar listo. El compose ya espera a que esté sano
antes de arrancar la API, así que esto solo pasa si Keycloak se cayó: revisá
`docker compose logs keycloak`.

**El navegador dice "Invalid parameter: redirect_uri".** El frontend está en un
puerto que el realm no tiene registrado. Los declarados son el 3000 y el 5173. Si
cambiaste `WEB_PORT`, hay que agregar el nuevo a `keycloak/realm-camelracing.json`
y volver a levantar Keycloak.

**La interfaz carga pero todos los pedidos fallan.** Casi siempre es `API_BASE_URL`
apuntando a un puerto distinto del de `APP_PORT`. Se ve rápido abriendo
http://localhost:3000/config.js, que muestra las direcciones que está usando.

**`./gradlew` no encuentra Java.** En Git Bash, sobre Windows:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
```

**Las pruebas de Docker fallan por tiempo de espera.** La primera corrida baja las
imágenes de Postgres y Keycloak, y eso puede tardar varios minutos con una
conexión lenta. Conviene bajarlas antes con `docker pull postgres:15-alpine` y
`docker pull quay.io/keycloak/keycloak:26.4`.

---

Universidad EIA · Implementación de Software
