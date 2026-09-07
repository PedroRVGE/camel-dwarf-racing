# Liga EIA de Carreras

Sistema de gestión de la liga de carreras de camellos contra enanos de la
Universidad EIA. Reemplaza a `final_races_v2_FINAL_NOW_THIS_ONE.xlsx`, que se
perdió porque alguien ordenó mal una columna.

Es una API REST en Java con una interfaz web que la consume, base de datos
persistente, autenticación con roles y todo corriendo en contenedores. Trabajo de
la materia Implementación de Software.

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
  "message": "Hay campos invalidos",
  "path": "/api/competitors",
  "validationErrors": { "weightKg": "El peso tiene que ser mayor que cero" }
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
