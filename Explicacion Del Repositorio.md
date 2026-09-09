# Explicación del repositorio

Guía de lectura del proyecto. Explica qué hay en cada carpeta, qué hace cada clase,
qué significan las anotaciones que aparecen por todas partes, y en qué se parece y
en qué se diferencia de los proyectos de clase de Dylan Ladrón.

Está escrita para leerse en orden. Los primeros capítulos son conceptos, los del
medio recorren el código archivo por archivo, y los últimos son la comparación con
los proyectos de clase y las preguntas que es probable que aparezcan al presentar.

---

## Índice

1. [Qué hace el sistema](#1-qué-hace-el-sistema)
2. [El camino de una petición](#2-el-camino-de-una-petición)
3. [Vocabulario de Spring](#3-vocabulario-de-spring)
4. [Las tecnologías y por qué está cada una](#4-las-tecnologías-y-por-qué-está-cada-una)
5. [La organización del código: por dominio, no por capa](#5-la-organización-del-código-por-dominio-no-por-capa)
6. [Los ocho módulos, uno por uno](#6-los-ocho-módulos-uno-por-uno)
7. [La carpeta common](#7-la-carpeta-common)
8. [La base de datos](#8-la-base-de-datos)
9. [La seguridad, de punta a punta](#9-la-seguridad-de-punta-a-punta)
10. [El frontend](#10-el-frontend)
11. [Docker y el arranque](#11-docker-y-el-arranque)
12. [Las pruebas](#12-las-pruebas)
13. [Comparación con los proyectos de Dylan Ladrón](#13-comparación-con-los-proyectos-de-dylan-ladrón)
14. [Preguntas previsibles y su respuesta](#14-preguntas-previsibles-y-su-respuesta)

---

## 1. Qué hace el sistema

Es el sistema de gestión de una liga de carreras donde compiten camellos contra
equipos de enanos. Antes eso vivía en una planilla de Excel y se perdió; el trabajo
consiste en reemplazarla por una aplicación de verdad.

El sistema maneja cinco cosas y una consulta:

- **Competidores**: cada camello o enano, con sus datos físicos, su estado y sus
  estadísticas acumuladas.
- **Equipos**: agrupaciones de competidores, con un capitán y un límite de
  integrantes.
- **Carreras**: el evento, con fecha, lugar, distancia, cupo y un estado que avanza
  de borrador a terminada.
- **Inscripciones**: la anotación de un competidor individual o de un equipo a una
  carrera, con carril asignado y un estado de aprobación.
- **Resultados**: cómo terminó cada participante, con tiempo, puesto y penalización.
- **Tabla de posiciones**: la consulta que suma puntos a partir de los resultados y
  ordena la liga.

Aparte hay una bitácora de auditoría que registra todo lo que pasa, y autenticación
con tres roles: administrador, organizador y espectador.

El proyecto son dos aplicaciones que se hablan por HTTP. El **backend** es una API
REST en Java con Spring Boot que guarda todo en PostgreSQL. El **frontend** es una
página en React que consume esa API. Aparte corre **Keycloak**, que es quien maneja
usuarios y contraseñas. Los cuatro (backend, base, Keycloak y frontend) corren en
contenedores de Docker y se levantan con un solo comando.

---

## 2. El camino de una petición

Esta es la idea más importante de todo el proyecto. Si se entiende este recorrido,
el resto del código se lee solo, porque los ocho módulos repiten exactamente la
misma estructura.

Supongamos que alguien aprieta "Guardar" en el formulario de un competidor nuevo.
Esto es lo que pasa, en orden:

**1. El navegador manda la petición.** Sale un `POST` a
`http://localhost:8080/api/competitors` con un cuerpo en JSON y una cabecera
`Authorization: Bearer eyJhbGciOi...`. Ese texto largo es el token.

**2. Los filtros de seguridad la interceptan** antes de que llegue a cualquier
código nuestro. Spring Security verifica la firma del token contra la clave pública
de Keycloak, mira que no esté expirado, saca los roles y decide si esa combinación
de método HTTP, ruta y rol tiene permiso. Si no lo tiene, la petición nunca llega al
controlador: se corta ahí con 401 o 403. Esto está en `SecurityConfig`.

**3. El controlador la recibe.** `CompetitorController` tiene un método anotado con
`@PostMapping`, y Spring lo llama pasándole el JSON ya convertido en un objeto de
Java. Antes de entrar al método, la anotación `@Valid` dispara las validaciones de
formato del DTO: que el nombre no esté vacío, que el peso sea positivo, que la fecha
de nacimiento esté en el pasado. Si algo falla, tampoco se ejecuta el método: salta
una excepción que se convierte en un 400 con el detalle campo por campo.

**4. El controlador llama al servicio.** No hace nada más. No decide, no valida
reglas, no habla con la base. Recibe, delega y devuelve.

**5. El servicio aplica las reglas de negocio.** `CompetitorService` verifica que el
apodo no esté repetido, que si viene un equipo ese equipo exista y tenga lugar, y
recién entonces guarda. Acá está toda la lógica del sistema, y es la capa que hay
que leer para entender qué hace la aplicación.

**6. El repositorio habla con la base.** El servicio no escribe SQL: le pide al
repositorio, que es una interfaz. Spring Data JPA genera la implementación en tiempo
de arranque y traduce a SQL.

**7. El mapper traduce.** Lo que se guarda en la base es una **entidad**
(`Competitor`), que es un objeto atado a la tabla. Lo que sale por la API es un
**DTO** (`CompetitorResponse`), que es un objeto pensado para el contrato de la API.
`CompetitorMapper` convierte de uno a otro. Los dos nunca se confunden.

**8. El controlador devuelve** un `ResponseEntity` con el código HTTP correcto (201
para una creación) y el DTO como cuerpo. Spring lo serializa a JSON y lo manda de
vuelta.

**9. Si algo salió mal en el camino**, la excepción no llega al navegador como un
volcado de pila. `GlobalExceptionHandler` la intercepta y la convierte en un JSON con
la misma forma que todos los demás errores del sistema.

Dibujado, el recorrido es este:

```
navegador
   |
   v
[ filtros de Spring Security ]   valida el token y el rol    -> 401 / 403
   |
   v
[ Controller ]                   valida el formato (@Valid)  -> 400
   |
   v
[ Service ]                      aplica las reglas           -> 409 / 404
   |
   v
[ Repository ]                   traduce a SQL
   |
   v
[ PostgreSQL ]                   constraints como ultima red -> 409
   |
   v
[ Mapper ] entidad -> DTO
   |
   v
navegador (JSON)
```

Notar que hay validación en cuatro lugares distintos y no es redundancia inútil:
cada capa protege contra algo que la anterior no puede ver. El DTO no puede saber si
un apodo ya existe; el servicio no puede impedir que alguien entre a la base con
`psql` y meta datos inconsistentes a mano.

---

## 3. Vocabulario de Spring

Las palabras que aparecen todo el tiempo en el código y conviene tener claras.

### Bean

Un bean es, simplemente, **un objeto que Spring crea y administra por vos**. Nada
más que eso.

En Java normal, si una clase necesita otra, la crea con `new`:

```java
public class CompetitorService {
    private ICompetitorRepository repo = new CompetitorRepositoryImpl();
}
```

Eso tiene dos problemas: hay que saber cómo construir el repositorio (y sus
dependencias, y las de esas), y queda atado a una implementación concreta, así que en
una prueba no se puede reemplazar por una falsa.

Con Spring, la clase solo **declara** lo que necesita, y Spring se lo entrega ya
construido:

```java
@Service
@RequiredArgsConstructor
public class CompetitorService {
    private final ICompetitorRepository competitorRepository;
    private final TeamService teamService;
    private final AuditService auditService;
}
```

`CompetitorService` es un bean. `ICompetitorRepository` es un bean. `TeamService` es
un bean. Al arrancar, Spring recorre el proyecto, encuentra las clases anotadas, las
instancia una sola vez cada una, y las va conectando entre sí. A ese arreglo se le
dice **inyección de dependencias**, y al lugar donde viven todos los beans, el
**contenedor** o *application context*.

Detalles que suelen preguntarse:

- Por defecto un bean es **singleton**: existe una sola instancia para toda la
  aplicación, compartida por todos los pedidos. Por eso los servicios no guardan
  estado en atributos: dos usuarios simultáneos usan el mismo objeto.
- No hay `@Autowired` en este proyecto. La inyección va **por constructor**, y
  `@RequiredArgsConstructor` de Lombok escribe ese constructor a partir de los campos
  `final`. Es la forma recomendada: si falta una dependencia, la aplicación no
  arranca en vez de fallar cuando alguien use el endpoint.

### Las anotaciones que declaran beans

Todas hacen lo mismo —marcar la clase como bean— pero dicen para qué sirve, y en
algunos casos agregan comportamiento:

| Anotación | Qué marca |
|---|---|
| `@Component` | Un bean genérico, sin papel definido. Acá lo usa `CurrentUser`. |
| `@Service` | Lógica de negocio. Es un `@Component` con nombre más claro. |
| `@Repository` | Acceso a datos. Además traduce excepciones de la base a las de Spring. |
| `@RestController` | Recibe peticiones HTTP y devuelve JSON. |
| `@Configuration` | Una clase que no es un servicio, sino una fábrica de otros beans. |
| `@Bean` | Va sobre un **método** dentro de una `@Configuration`: lo que devuelva ese método pasa a ser un bean. |

La distinción entre `@Component` y `@Bean` es la que más se confunde.
`@Component` se pone sobre **una clase propia** y Spring la instancia sola.
`@Bean` se pone sobre **un método** y sirve cuando el objeto no es de una clase
nuestra o hay que configurarlo antes de usarlo. En `SecurityConfig` se ve el segundo
caso: `SecurityFilterChain`, `CorsConfigurationSource` y `JwtAuthenticationConverter`
son clases de Spring que no podemos anotar, así que las armamos en un método y lo
marcamos con `@Bean`.

### @Transactional

Marca un método como una **transacción** de base de datos: o pasa todo, o no pasa
nada. Si a mitad de camino salta una excepción, la base deshace todo lo que ese
método había escrito.

Es imprescindible en operaciones que escriben en más de un lugar. Cargar un resultado
guarda el resultado, actualiza las estadísticas del competidor y escribe en la
bitácora; sin transacción, un error entre el segundo y el tercer paso dejaría a un
competidor con una victoria que no figura en ninguna carrera.

`@Transactional(readOnly = true)` en las consultas le avisa a la base que no va a
haber escrituras, lo que le permite ahorrar trabajo.

Cómo funciona por debajo, porque explica una limitación: Spring envuelve el bean en
un **proxy**, un objeto que se hace pasar por el original, abre la transacción, llama
al método real y cierra. La consecuencia es que si un método de la clase llama a otro
de la misma clase directamente, el proxy no se entera y la anotación no tiene efecto.
En este proyecto la transacción siempre empieza en el método público que llama el
controlador.

### Entidad, DTO y mapper

Tres nombres para tres cosas distintas que representan lo mismo.

Una **entidad** es una clase anotada con `@Entity` que corresponde a una tabla. Cada
atributo es una columna, y Hibernate se encarga de convertir entre filas y objetos. A
eso se le dice **ORM**, *object-relational mapping*.

Un **DTO** (*data transfer object*) es la forma que tiene el dato al entrar y salir de
la API. Acá son `record` de Java, que son clases inmutables de una sola línea:

```java
public record CompetitorStatusRequest(@NotNull CompetitorStatus status) {}
```

Hay dos por operación: un `Request` con lo que se acepta recibir y un `Response` con
lo que se decide mostrar.

Un **mapper** es la clase que traduce entre entidad y DTO. Acá son clases con
constructor privado y métodos `static`, sin librerías:

```java
public class CompetitorMapper {
    private CompetitorMapper() {}
    public static Competitor toEntity(CompetitorRequest request, Team team) { ... }
    public static CompetitorResponse toResponse(Competitor competitor) { ... }
}
```

El constructor privado impide instanciarla: no tiene estado, así que no hay razón
para tener un objeto de ella.

**Por qué no se devuelve la entidad directamente**, que es la pregunta obvia. Cuatro
razones concretas:

1. El contrato de la API quedaría atado al esquema de la base. Renombrar una columna
   rompería a todos los clientes.
2. Se filtraría todo. Si mañana la tabla tiene una columna interna, aparece sola en
   el JSON de todos los endpoints.
3. Las relaciones perezosas explotan al serializar. `Competitor` tiene una relación
   `LAZY` con `Team`; si Jackson intenta convertirla a JSON fuera de la transacción,
   salta una excepción de sesión cerrada. Con `open-in-view: false`, que es como está
   configurado el proyecto, eso pasa siempre.
4. El DTO puede tener cosas que la tabla no. `CompetitorResponse` incluye `age`, que
   no es una columna: el mapper la calcula a partir de la fecha de nacimiento.

### Lombok

Una librería que escribe código repetitivo en tiempo de compilación. Las que aparecen
acá:

- `@Getter` y `@Setter`: los métodos de acceso de cada atributo.
- `@NoArgsConstructor`: constructor vacío, que Hibernate necesita para instanciar
  entidades.
- `@AllArgsConstructor`: constructor con todos los atributos.
- `@Builder`: permite construir el objeto encadenando llamadas
  (`Competitor.builder().name("Byte").build()`), que se lee mucho mejor que un
  constructor de doce parámetros posicionales.
- `@RequiredArgsConstructor`: constructor solo con los campos `final`. Es el que
  habilita la inyección por constructor sin escribirla.
- `@Slf4j`: agrega un atributo `log` para escribir en el log.
- `@ToString` y `@EqualsAndHashCode`: representación en texto e igualdad.

En las entidades, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` con solo el `id`
incluido es deliberado: dos objetos que representan la misma fila son el mismo
competidor aunque uno tenga datos viejos, y comparar por todos los campos rompe el
comportamiento de los `Set` cuando Hibernate actualiza una entidad.

### JPA, Hibernate y Spring Data

Tres capas apiladas que suelen mezclarse:

- **JPA** es la especificación, el conjunto de interfaces y anotaciones (`@Entity`,
  `@Column`, `@ManyToOne`). No hace nada por sí sola.
- **Hibernate** es la implementación de JPA que trae Spring Boot. Es la que
  efectivamente genera el SQL.
- **Spring Data JPA** es una capa arriba que permite escribir repositorios como
  interfaces vacías y genera la implementación.

Eso último es lo que explica por qué en el proyecto no hay ninguna clase
`CompetitorRepositoryImpl`. Al extender `JpaRepository<Competitor, UUID>`, la interfaz
ya viene con `save`, `findById`, `findAll`, `deleteById` y compañía. Además, Spring
Data deduce consultas del nombre del método: `existsByNicknameIgnoreCase` se traduce
solo a un `SELECT ... WHERE LOWER(nickname) = LOWER(?)`. Y cuando la consulta es
demasiado complicada para expresarla en el nombre, se escribe explícita con `@Query`.

### JWT y resource server

Un **JWT** (*JSON Web Token*) es un texto con tres partes separadas por puntos:
cabecera, contenido y firma, cada una en base64. El contenido dice quién es el usuario
y qué roles tiene; la firma la hizo Keycloak con su clave privada.

Lo importante es que **no hace falta consultar a Keycloak para validar un token**. La
API baja una sola vez la clave pública de Keycloak y con eso verifica la firma de cada
token que llega. Si la firma es válida, el contenido es confiable, porque nadie sin la
clave privada puede producir una firma que cierre. Eso también significa que el token
no se puede modificar: cambiarle un rol invalida la firma.

En el vocabulario de OAuth2, una aplicación que **guarda datos y exige un token pero
no emite tokens** es un **resource server**. Es exactamente el papel de esta API, y es
lo que configura `SecurityConfig`.

### CORS y CSRF

Se confunden por el nombre y no tienen nada que ver.

**CORS** (*cross-origin resource sharing*) es una regla del navegador: una página
servida desde `localhost:3000` no puede llamar por defecto a `localhost:8080`, porque
un puerto distinto es un origen distinto. El servidor tiene que autorizarlo
explícitamente con cabeceras de respuesta. Eso es lo que hace
`corsConfigurationSource()`, y es la razón por la que el frontend funciona.

**CSRF** (*cross-site request forgery*) es un ataque: otra página hace que tu
navegador mande un pedido a este sitio aprovechando que la cookie de sesión viaja
sola. Acá está desactivado, y con razón: no hay cookie de sesión. La autorización va
en una cabecera que hay que poner a mano, y una página ajena no puede hacerlo. Sin
cookie no hay ataque que prevenir, y dejarlo activo rompería todos los `POST`.

### Flyway

Controla la versión del esquema de la base. Cada cambio es un archivo SQL numerado
(`V1__...`, `V2__...`, `V3__...`) que Flyway ejecuta en orden la primera vez y después
nunca más, anotando en una tabla propia (`flyway_schema_history`) qué ya corrió.

La alternativa es dejar que Hibernate cree las tablas solo con
`ddl-auto: update`. No se hizo, y el motivo es concreto: Hibernate agrega columnas
pero no las renombra ni las borra, no puede escribir un `CHECK` compuesto, y sobre
todo, el esquema termina siendo lo que Hibernate haya decidido, sin quedar registrado
en ninguna parte. Con Flyway, el esquema es un archivo que se lee.

En este proyecto Hibernate está en `ddl-auto: validate`: no toca nada, solo compara
las entidades con las tablas al arrancar y **falla el arranque** si no coinciden. Es
una red de seguridad: si alguien agrega un atributo a una entidad y se olvida de la
migración, la aplicación no levanta en vez de fallar más tarde con un error de columna
inexistente.

Los archivos `R__` son **repeatable**: no llevan número de versión y Flyway los vuelve
a ejecutar cada vez que cambia su contenido. Ahí van los datos de ejemplo.

---

## 4. Las tecnologías y por qué está cada una

| Tecnología | Versión | Para qué |
|---|---|---|
| Java | 25 | El lenguaje del backend. |
| Spring Boot | 4.1.0 | El armazón: servidor web, inyección de dependencias, configuración. |
| Gradle | 9.5.1 | Compila, resuelve dependencias y corre las pruebas. |
| Spring Web MVC | (de Boot) | Los controladores REST. |
| Spring Data JPA + Hibernate | (de Boot) | El mapeo objeto-relacional. |
| Bean Validation | (de Boot) | Las anotaciones `@NotBlank`, `@Positive`, `@Future`. |
| Spring Security + OAuth2 Resource Server | (de Boot) | Validación de tokens y reglas de acceso. |
| Spring Boot Actuator | (de Boot) | El endpoint `/actuator/health` y las métricas. |
| springdoc-openapi | 3.1.0 | Genera Swagger leyendo los controladores y los DTOs. |
| Flyway | 12.4.0 | Versiona el esquema de la base. |
| Lombok | (de Boot) | Elimina el código repetitivo. |
| PostgreSQL | 15 | La base de datos. |
| Keycloak | 26.4 | Usuarios, contraseñas, roles y emisión de tokens. |
| React | 19.2.0 | La interfaz. |
| Vite | 7.1.12 | Compila el frontend y da recarga automática al desarrollar. |
| react-router-dom | 7.9.4 | Las rutas de la interfaz. |
| keycloak-js | 26.2.0 | El cliente de Keycloak en el navegador. |
| nginx | 1.29 | Sirve el frontend ya compilado. |
| Docker y Docker Compose | — | Levanta los cuatro servicios juntos. |
| JUnit 5 | (de Boot) | El motor de pruebas. |
| Mockito | (de Boot) | Objetos falsos para las pruebas unitarias. |
| Testcontainers | 1.21.4 | Levanta Postgres y Keycloak reales durante las pruebas. |

Un detalle de versiones que vale saber por si aparece un error raro: **springdoc 2.x
no funciona con Spring Boot 4**. Boot 4 reorganizó sus módulos internos y solo la
línea 3.x de springdoc los conoce. La versión va escrita a mano en el `build.gradle`
porque el plugin `dependency-management` de Spring no administra las dependencias de
springdoc.

---

## 5. La organización del código: por dominio, no por capa

El árbol del backend es este:

```
src/main/java/com/eia/camelracing/
├── CamelRacingApplication.java     el punto de entrada
├── auth/                           el perfil del usuario autenticado
├── competitor/                     competidores
├── team/                           equipos
├── race/                           carreras
├── registration/                   inscripciones
├── result/                         resultados
├── standings/                      tabla de posiciones
├── audit/                          bitácora
└── common/                         configuración, seguridad y errores
```

Y adentro de cada módulo, siempre las mismas subcarpetas:

```
competitor/
├── controller/     CompetitorController
├── service/        CompetitorService
├── repository/     ICompetitorRepository
├── entity/         Competitor, CompetitorType, CompetitorStatus
├── dto/            CompetitorRequest, CompetitorResponse, CompetitorStatusRequest
└── mapper/         CompetitorMapper
```

Esto se llama **package by feature** o *paquete por dominio*, y es lo contrario de
tener en la raíz una carpeta `controller` con los ocho controladores, otra `service`
con los ocho servicios y así. La diferencia práctica es que para trabajar en
competidores se abre una carpeta y está todo ahí, en vez de saltar entre seis
carpetas lejanas cuyo contenido no tiene nada que ver entre sí.

`CamelRacingApplication` es la clase con `main`. Su única anotación,
`@SpringBootApplication`, hace tres cosas: marca la clase como configuración, activa
la autoconfiguración de Spring Boot (lo que arma el servidor web, el pool de
conexiones y el resto a partir de las dependencias que hay en el classpath) y
enciende el escaneo de componentes **a partir de su propio paquete y hacia abajo**.
Eso último explica por qué la clase está en `com.eia.camelracing` y no en un
subpaquete: si estuviera más adentro, Spring no encontraría los beans que quedaran
por fuera.

### El patrón que repiten los ocho módulos

Vale la pena verlo una vez con nombres concretos, porque después se repite idéntico.

**El controlador** recibe HTTP y no hace nada más:

```java
@RestController
@RequestMapping("/api/competitors")
@AllArgsConstructor
@Slf4j
@Tag(name = "Competidores", description = "...")
public class CompetitorController {

    private final CompetitorService competitorService;

    @PostMapping
    @Operation(summary = "Crea un competidor")
    @ApiResponses({ ... })
    public ResponseEntity<CompetitorResponse> createCompetitor(
            @Valid @RequestBody CompetitorRequest request) {
        CompetitorResponse creado = competitorService.createCompetitor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
```

Las anotaciones, una por una:

- `@RestController`: es un controlador y lo que devuelve se serializa a JSON, en vez
  de interpretarse como el nombre de una plantilla HTML.
- `@RequestMapping("/api/competitors")`: el prefijo común de todas sus rutas.
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`:
  qué método HTTP atiende cada uno.
- `@PathVariable`: un trozo de la ruta (`/api/competitors/{id}`).
- `@RequestParam`: un parámetro de la query (`?type=DWARF`).
- `@RequestBody`: el cuerpo JSON, convertido a un DTO.
- `@Valid`: dispara las validaciones declaradas en ese DTO.
- `@Tag`, `@Operation`, `@ApiResponses`: no cambian nada del comportamiento; son lo
  que lee springdoc para armar Swagger.
- `ResponseEntity<T>`: permite elegir el código de estado y las cabeceras, en vez de
  devolver siempre 200.

**El servicio** tiene toda la lógica:

```java
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final ICompetitorRepository competitorRepository;
    private final TeamService teamService;
    private final AuditService auditService;

    @Transactional
    public CompetitorResponse createCompetitor(CompetitorRequest request) {
        validarApodoLibre(request.nickname(), null);
        Team team = ...;                      // valida que exista y tenga lugar
        Competitor competitor = CompetitorMapper.toEntity(request, team);
        competitor = competitorRepository.save(competitor);
        auditService.registrar(...);
        return CompetitorMapper.toResponse(competitor);
    }
}
```

**El repositorio** es una interfaz:

```java
public interface ICompetitorRepository extends JpaRepository<Competitor, UUID> {

    @Override
    @EntityGraph(attributePaths = "team")
    Optional<Competitor> findById(UUID id);

    boolean existsByNicknameIgnoreCase(String nickname);
    List<Competitor> findByTeamId(UUID teamId);
}
```

La `I` del principio es una convención del curso para que se vea de un vistazo que es
una interfaz.

`@EntityGraph(attributePaths = "team")` merece explicación porque resuelve un problema
clásico. `Competitor` tiene su equipo en `FetchType.LAZY`, es decir que no se carga
hasta que alguien lo pida. Al listar cincuenta competidores y mostrar el nombre del
equipo de cada uno, eso son cincuenta consultas extra, una por competidor: es el
problema **N+1**. `@EntityGraph` le dice a Hibernate que traiga el equipo en la misma
consulta con un `JOIN`, y quedan cincuenta filas en un solo viaje.

---

## 6. Los ocho módulos, uno por uno

### competitor

Los competidores: camellos, enanos, medianos y otros.

`Competitor` es la entidad, con datos personales, físicos, un estado, la relación
opcional al equipo y tres contadores denormalizados (`victories`, `defeats`,
`completedRaces`). Están guardados en la fila en lugar de calcularse cada vez porque
la ficha de un competidor los muestra siempre y recalcularlos exigiría recorrer todos
sus resultados en cada consulta.

Dos enumerados acompañan a la entidad. `CompetitorType` tiene `DWARF`, `CAMEL`,
`MEDIUM` y `OTHER`. `CompetitorStatus` tiene `ACTIVE`, `INJURED`, `SUSPENDED` y
`RETIRED`, y expone un método `puedeCompetir()` que devuelve verdadero solo para
`ACTIVE`. Poner esa decisión dentro del enumerado, en vez de repartir
`if (status == ACTIVE || status == ...)` por los servicios, hace que agregar un estado
nuevo sea cambiar un solo archivo.

`CompetitorService` maneja: listado con búsqueda por texto, filtros y paginación;
ficha individual; alta; edición; cambio de estado; y baja. Sus reglas propias son que
el apodo es único en toda la liga (verificado con
`existsByNicknameIgnoreCase`, y respaldado por un `UNIQUE` en la tabla) y que la baja
es lógica: `deleteCompetitor` no borra la fila, la pasa a `RETIRED`.

`ICompetitorRepository` tiene una consulta `buscar` escrita con `@Query` y un bloque
de texto, donde cada filtro está condicionado con `(:parametro IS NULL OR ...)`. Ese
truco permite tener un solo método para todas las combinaciones de filtros en vez de
un método por combinación.

`CompetitorController` expone siete endpoints bajo `/api/competitors`: listar, crear,
ver por id, editar, cambiar estado, retirar. Todo lo que escribe pide rol de
administrador.

### team

Los equipos, que agrupan competidores.

`Team` tiene nombre único, descripción, capitán, estado y contadores propios. La
relación con los competidores está del lado del competidor (`team_id` en la tabla
`competitors`), así que un competidor pertenece a un equipo o a ninguno.

`TeamService` es el más entretenido del proyecto porque tiene las reglas más
enredadas. El límite de integrantes no es una constante sino
`@Value("${camelracing.teams.max-members}")`, que se resuelve al arrancar desde la
variable de entorno `TEAM_MAX_MEMBERS`, porque el enunciado pide que sea configurable.
`addMember` y `removeMember` mueven competidores entre equipos, `prepararIngreso` es
el método que reutiliza `CompetitorService` cuando el alta de un competidor ya viene
con equipo, y `liberarIntegrantes` se llama al desactivar un equipo para dejar sueltos
a sus integrantes en vez de que queden apuntando a un equipo inactivo.

`TeamController` expone nueve endpoints, incluidos los dos de integrantes:
`POST /api/teams/{id}/members/{competidor}` y su `DELETE`.

### race

Las carreras, que son el centro del sistema.

`Race` tiene nombre, descripción, fecha de largada, lugares de salida y llegada,
distancia, cupo, tipo, fecha de cierre de inscripciones y estado.

`RaceStatus` es la pieza más importante del dominio, porque es una **máquina de
estados**. Los seis valores son `DRAFT`, `OPEN_FOR_REGISTRATION`,
`CLOSED_FOR_REGISTRATION`, `IN_PROGRESS`, `COMPLETED` y `CANCELLED`, y el enumerado
declara él mismo qué transiciones son válidas:

```
DRAFT -> OPEN_FOR_REGISTRATION -> CLOSED_FOR_REGISTRATION -> IN_PROGRESS -> COMPLETED
  |             |                          |
  +-------------+--------------------------+---> CANCELLED
```

`transicionesPosibles()` devuelve el conjunto de destinos válidos y `puedePasarA()`
responde si un salto concreto está permitido. `COMPLETED` y `CANCELLED` son finales:
de ahí no se sale. `admiteInscripciones()` y `esFinal()` son atajos que usan los otros
servicios.

`RaceType` tiene `INDIVIDUAL`, `TEAM` y `MIXED`, con `admiteIndividuales()` y
`admiteEquipos()`. Una carrera `INDIVIDUAL` no acepta equipos, una `TEAM` no acepta
individuales, y una `MIXED` acepta los dos. Es la regla que hace posible el escenario
del enunciado: un camello solo contra un equipo de cinco enanos.

`RaceService` valida las transiciones y agrega dos reglas que el enumerado no puede
saber porque dependen de los datos: no se puede largar con menos de dos participantes
aprobados (`MINIMO_PARA_LARGAR = 2`) y no se puede dar por terminada mientras falte
cargar algún resultado. El mensaje de ese segundo error dice cuántos faltan, en lugar
de un "operación inválida" que obligue a ir a leer el código.

### registration

Las inscripciones, que unen una carrera con un participante.

`RaceRegistration` es la entidad y tiene una particularidad: apunta **o** a un
competidor **o** a un equipo, nunca a los dos y nunca a ninguno. En la tabla eso son
dos claves foráneas anulables más un `CHECK` que obliga a que exactamente una esté
llena.

`RegistrationStatus` tiene `PENDING`, `APPROVED`, `REJECTED` y `CANCELLED`, con
`ocupaLugar()` para saber cuáles cuentan contra el cupo (pendientes y aprobadas sí,
rechazadas y canceladas no).

`RegistrationService` es el que concentra más validaciones del proyecto, y todas están
en métodos privados con nombre descriptivo:

- que la carrera esté aceptando inscripciones,
- que quede cupo,
- que el carril esté libre,
- que el tipo de inscripción coincida con el tipo de carrera,
- que el competidor esté en condiciones de competir,
- que no esté ya inscripto en esa misma carrera,
- que ningún integrante del equipo esté anotado además como individual, y al revés,
- que el equipo tenga al menos un integrante y que todos estén habilitados.

Las dos cruzadas del final son las que suelen pasarse por alto:
`competidorYaCorreEnUnEquipo` y `algunIntegranteYaCorreSolo`. Sin ellas, un enano
podría correr contra su propio equipo.

`approve` asigna carril automáticamente con `siguienteCarrilLibre` si no viene uno, y
`reject` exige un motivo, que queda guardado.

### result

Los resultados de cada participante en cada carrera.

`RaceResult` guarda estado, tiempo, penalización, puesto y notas del juez, y está
unido a la inscripción (no directamente al competidor), lo cual es correcto: el
resultado es de una participación concreta.

`ResultStatus` tiene `FINISHED`, `DISQUALIFIED`, `DID_NOT_FINISH` y `DID_NOT_START`,
y en él vive el sistema de puntaje:

```java
public int puntos(Integer posicionFinal) { ... }   // 10, 7, 5, 3, 1, y 0 del sexto en adelante
public boolean cuentaComoDerrota() { ... }         // DID_NOT_START no cuenta
public boolean admitePosicionYTiempo() { ... }     // solo FINISHED
```

`ResultService` valida coherencia: que la carrera esté en curso, que la inscripción
sea de esa carrera y esté aprobada, que no tenga ya un resultado, que no haya dos
primeros puestos, y que los tiempos sean coherentes con los puestos (el segundo no
puede haber tardado menos que el primero). Después de guardar, `recalcularEstadisticas`
actualiza los contadores del competidor o del equipo.

### standings

La tabla de posiciones. Es el único módulo **sin entidad propia**, porque no guarda
nada: es una consulta de agregación sobre los resultados.

`IStandingsRepository` no extiende `JpaRepository` sino `Repository`, que es la
interfaz base sin ningún método. Eso es deliberado: este repositorio solo tiene que
leer, y heredar `save` y `delete` sería ofrecer operaciones que no tienen sentido.

Las dos consultas están escritas en JPQL con `SELECT new ...StandingRow(...)`, que es
una **proyección por constructor**: en vez de traer entidades y sumar en Java, la base
devuelve directamente objetos con las seis columnas calculadas. El orden es por puntos,
después por victorias, después alfabético, para que empates no queden en orden
aleatorio.

`StandingsService` numera las posiciones después de paginar, así que el primero de la
segunda página es el 21 y no el 1.

### audit

La bitácora. Registra quién hizo qué, cuándo, sobre qué entidad, con el valor anterior
y el nuevo.

`AuditAction` es un enumerado con veintiuna acciones concretas (`COMPETITOR_CREATED`,
`RACE_STATUS_CHANGED`, `RESULT_RECORDED`, `LOGIN`...). Usar un enumerado en lugar de un
texto libre impide que la misma acción quede escrita de tres formas distintas y hace
posible filtrar por ella.

`AuditService` tiene un solo método de escritura, `registrar`, que llaman todos los
demás servicios. Recorta los textos largos antes de guardarlos, porque el valor
anterior de una entidad grande no cabe en la columna.

`AuditController` expone un solo endpoint, `GET /api/audit`, con filtros, y es el único
del proyecto reservado exclusivamente a administradores.

### auth

El módulo más chico: un solo endpoint, `GET /api/auth/profile`, que devuelve quién es
el usuario de la sesión y qué roles tiene.

**No hay `/login`, `/register` ni `/refresh`**, y no es un olvido: de eso se encarga
Keycloak. La aplicación no guarda contraseñas y no emite tokens. `AuthService` recibe
el `Jwt` ya validado, extrae el usuario y filtra los roles quedándose con los tres de
la aplicación (`admin`, `organizer`, `viewer`) para no devolver los internos de
Keycloak. Aprovecha para registrar un `LOGIN` en la bitácora, que es el único momento
en que la API se entera de que alguien entró.

---

## 7. La carpeta common

Lo que no pertenece a ningún dominio.

### common/config/SecurityConfig.java

Quién puede entrar a qué. Es la clase que hay que leer para responder cualquier
pregunta sobre permisos.

Define cuatro beans:

- `securityFilterChain`: la cadena de filtros. Configura CORS, apaga CSRF, pone las
  sesiones en `STATELESS`, declara las reglas de acceso en orden, engancha el
  respondedor de errores y activa la validación de JWT.
- `corsConfigurationSource`: qué orígenes, métodos y cabeceras se aceptan. Los
  orígenes llegan por configuración, no están escritos en el código.
- `jwtAuthenticationConverter`: envuelve el traductor de roles.
- `KeycloakRolesConverter`: el traductor propiamente dicho.

Dos cosas de esta clase se preguntan seguido.

**El orden de las reglas importa, y mucho.** Spring evalúa de arriba hacia abajo y
**gana la primera que coincide**. Por eso van de lo más específico a lo más general:
si `.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(...)` estuviera antes que
la regla de `/api/audit/**`, cualquier usuario podría leer la bitácora. Y por eso al
final está `.anyRequest().authenticated()`: un endpoint nuevo al que alguien se olvide
de darle regla queda protegido en vez de quedar abierto.

**Por qué existe `KeycloakRolesConverter`.** Keycloak y Spring Security usan la
palabra "rol" para lo mismo pero lo guardan distinto. Keycloak los manda anidados y en
minúscula dentro del token:

```json
{ "realm_access": { "roles": ["admin", "organizer"] } }
```

Spring los busca planos y con prefijo, porque `hasRole("ADMIN")` internamente compara
contra `"ROLE_ADMIN"`. Sin este traductor, Spring no encuentra ningún rol donde busca,
todas las reglas fallan y **todo devuelve 403 aunque el token esté perfecto**. Es el
error más típico al conectar Keycloak con Spring y no deja ninguna pista en los logs.

### common/config/OpenApiConfig.java

Configura Swagger: el título, la descripción, y el esquema de seguridad, que es lo que
hace aparecer el botón "Authorize". Ahí también está documentado con qué usuario
entrar para probar.

### common/exception/GlobalExceptionHandler.java

Convierte excepciones en respuestas HTTP con formato uniforme. Es una clase anotada
con `@RestControllerAdvice`, que quiere decir que sus métodos se aplican a todos los
controladores del proyecto.

Cada método lleva `@ExceptionHandler(AlgunaExcepcion.class)` y traduce esa excepción a
un código de estado:

| Excepción | Estado | Cuándo pasa |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Falló una validación del `@RequestBody`. Devuelve el detalle campo por campo. |
| `HandlerMethodValidationException` | 400 | Falló una validación de un parámetro. |
| `MethodArgumentTypeMismatchException` | 400 | Un parámetro no se pudo convertir, por ejemplo un id que no es un UUID. |
| `HttpMessageNotReadableException` | 400 | El JSON está mal formado. |
| `NoSuchElementException` | 404 | No se encontró la entidad. |
| `NoResourceFoundException` | 404 | La ruta no existe. |
| `HttpRequestMethodNotSupportedException` | 405 | Método HTTP incorrecto para esa ruta. |
| `BusinessRuleException` | 409 | Se violó una regla de negocio. |
| `DataIntegrityViolationException` | 409 | Se violó una restricción de la base. |
| `Exception` | 500 | Cualquier otra cosa. Se registra en el log completo y al cliente le llega un mensaje genérico. |

Ese último renglón es una decisión de seguridad: un volcado de pila en la respuesta le
cuenta al atacante qué versión de qué librería se está usando y cómo están armadas las
consultas.

### common/exception/BusinessRuleException.java

Una excepción propia, `RuntimeException` con un mensaje. Existe para poder distinguir
"esto está mal pedido" (400) de "esto está bien pedido pero no se puede hacer" (409).
Cuando `RaceService` dice que faltan resultados, lanza esta.

### common/exception/ErrorResponse.java

El `record` con la forma de todos los errores: `timestamp`, `status`, `error`,
`message`, `path` y, solo en los de validación, `validationErrors`. Que todos los
errores del sistema tengan la misma forma es lo que permite que el frontend tenga un
solo lugar donde manejarlos.

### common/exception/SecurityErrorResponder.java

Resuelve un problema puntual. Los errores de seguridad ocurren en los filtros, es
decir **antes** de llegar a cualquier controlador, así que `GlobalExceptionHandler` no
los ve: Spring devolvería su propio 401 o 403 con un cuerpo distinto al del resto de
la API. Esta clase implementa `AuthenticationEntryPoint` y `AccessDeniedHandler`, y
escribe el mismo `ErrorResponse` a mano. Es la razón por la que un 401 y un 400 se ven
iguales desde afuera.

### common/dto/PageResponse.java

La envoltura de todos los listados: `content`, `page`, `size`, `totalElements`,
`totalPages`, `first`, `last`. Envuelve el `Page` de Spring en vez de devolverlo
directamente, porque el JSON que produce `Page` incluye la configuración interna de
paginación de Spring y su forma cambió entre versiones. El método estático `of` acepta
una función de mapeo, así que convertir entidades a DTOs y armar la página es una sola
línea en cada servicio.

### common/security/CurrentUser.java

Un `@Component` con dos métodos, `username()` e `id()`, que sacan del contexto de
seguridad quién está pidiendo. Los servicios lo inyectan cuando necesitan saberlo,
sobre todo para la bitácora. Tenerlo en una clase evita repetir el rescate de
`SecurityContextHolder` en siete servicios, y hace que en las pruebas se pueda
reemplazar por un doble.

---

## 8. La base de datos

Seis tablas. El diagrama entidad-relación completo, con el detalle de por qué cada
cosa está donde está, es `docs/modelo-de-datos.md`.

```
teams  1 ----- 0..n  competitors
                          |
                          | (una de las dos)
races  1 ----- 0..n  race_registrations  1 ----- 0..1  race_results
                          |
teams  1 ------------------+

audit_logs   (sin relaciones)
```

| Tabla | Qué guarda |
|---|---|
| `teams` | Equipos. Nombre único. |
| `competitors` | Competidores. Apodo único, clave foránea opcional al equipo. |
| `races` | Carreras. |
| `race_registrations` | Inscripciones. Apunta a la carrera y a un competidor **o** un equipo. |
| `race_results` | Resultados. Uno por inscripción como máximo. |
| `audit_logs` | Bitácora. Sin relaciones. |

**No hay tabla de usuarios.** Los usuarios viven en Keycloak. `audit_logs` guarda el
nombre de usuario como texto porque es lo único que la aplicación conoce.

### Las restricciones que hacen el trabajo

La base no es un depósito pasivo: buena parte de las reglas están garantizadas ahí,
donde no se pueden esquivar.

**Claves únicas.** `uk_teams_name` y `uk_competitors_nickname` impiden nombres y
apodos repetidos. `uk_registrations_race_lane` impide dos participantes en el mismo
carril de la misma carrera. `uk_results_registration` impide dos resultados para la
misma inscripción. `uk_results_race_position` impide dos primeros puestos.

**El `CHECK` de uno de dos.** `ck_registrations_un_participante` exige que
exactamente una de las dos claves foráneas de participante esté llena. Es lo que
convierte "o competidor o equipo, nunca los dos" en algo imposible de violar.

**El `CHECK` de coherencia de resultados.** `ck_results_coherencia_estado` exige que
solo un resultado `FINISHED` tenga puesto y tiempo, y que los demás estados no tengan
ninguno de los dos. De paso resuelve gratis un requisito del enunciado: "un
descalificado no puede ganar" queda garantizado estructuralmente, porque un
descalificado no puede tener puesto en absoluto.

**Índices.** Están sobre las columnas por las que efectivamente se filtra y ordena:
estado y tipo de competidores y carreras, fecha de largada, las claves foráneas de
inscripciones, y en la bitácora la fecha, el usuario y la acción.

### Las migraciones

```
db/migration/V1__competidores_y_equipos.sql
db/migration/V2__carreras_e_inscripciones.sql
db/migration/V3__resultados_y_auditoria.sql
db/seed/R__datos_de_ejemplo.sql
```

Las tres `V` crean el esquema en ese orden, porque cada una depende de la anterior:
no se puede crear `race_registrations` antes que `races`. La `R` carga datos de
ejemplo y solo se activa en los perfiles de desarrollo y Docker, nunca en producción.
Al ser *repeatable*, se vuelve a ejecutar si su contenido cambia, lo que permite
ajustar los datos de prueba sin inventar una versión nueva.

Los datos de ejemplo cubren el mínimo que pide el enunciado: cinco enanos, dos
camellos, dos medianos, dos equipos, una carrera en cada uno de los seis estados y dos
terminadas con resultados cargados.

---

## 9. La seguridad, de punta a punta

El recorrido completo de una sesión, que es lo que conviene poder contar de memoria.

**1.** El usuario abre `http://localhost:3000` y aprieta "Entrar".

**2.** El navegador se va a la pantalla de Keycloak. La contraseña se escribe **en
Keycloak**; ni el frontend ni el backend la ven nunca.

**3.** Keycloak valida, y devuelve el navegador a la aplicación con un token JWT
firmado que dice quién es el usuario y qué roles tiene.

**4.** El frontend guarda ese token **en memoria**, no en `localStorage`. La razón es
concreta: cualquier script que llegue a ejecutarse en la página puede leer
`localStorage` entero y llevarse una credencial válida. En memoria muere al cerrar la
pestaña, y al recargar la sesión se recupera preguntándole a Keycloak si su cookie
sigue viva.

**5.** Cada llamada a la API va con `Authorization: Bearer <token>`.

**6.** La API verifica la firma con la clave pública de Keycloak, que bajó una sola
vez al arrancar. No consulta a Keycloak en cada pedido.

**7.** `KeycloakRolesConverter` traduce `realm_access.roles` a las autoridades que
Spring entiende.

**8.** Las reglas de `SecurityConfig` deciden. Si el token falta o está vencido, 401.
Si es válido pero el rol no alcanza, 403.

Los tres roles y qué pueden hacer:

| | admin | organizer | viewer |
|---|---|---|---|
| Consultar todo | sí | sí | sí |
| Crear y editar competidores y equipos | sí | no | no |
| Crear y operar carreras | sí | sí | no |
| Aprobar inscripciones y cargar resultados | sí | sí | no |
| Ver la bitácora | sí | no | no |

Los roles se acumulan **en el usuario, no en el código**: en Keycloak, `admin` tiene
los tres asignados. Por eso `SecurityConfig` no necesita ninguna jerarquía en Java.

Un punto que conviene tener claro: el frontend tiene un archivo `permisos.js` que
copia esta tabla, pero **eso no es seguridad**. Sirve para no mostrar botones que van a
fallar. Quien quiera saltarse esa comprobación solo tiene que abrir la consola del
navegador; lo que no puede saltarse es `SecurityConfig`, porque corre en el servidor.

---

## 10. El frontend

Una aplicación de una sola página en React. La estructura está pensada para que se
corresponda con el backend: hay un archivo de API por cada módulo del backend, y las
páginas están agrupadas por dominio igual que los paquetes de Java.

```
frontend/src/
├── main.jsx           el arranque
├── App.jsx            el mapa de rutas
├── config.js          lee la configuración que inyecta el contenedor
├── estilos.css        una sola hoja de estilos
├── auth/
│   ├── keycloak.js        inicializa keycloak-js
│   ├── AuthProvider.jsx   el contexto de sesión
│   ├── RutaProtegida.jsx  el guardián de cada ruta
│   └── permisos.js        qué rol puede ver qué (solo para la interfaz)
├── api/
│   ├── cliente.js         el unico lugar del proyecto que llama a fetch
│   └── competidores.js, equipos.js, carreras.js, inscripciones.js,
│       resultados.js, posiciones.js, auditoria.js
├── comun/             componentes reutilizables
└── paginas/           una carpeta por dominio
```

### Los cinco archivos que importan

**`api/cliente.js`** es el único lugar del proyecto que llama a `fetch`. Todo lo demás
pasa por acá, y eso concentra en un archivo el token, el manejo de errores, los
mensajes por código de estado y el armado de la query. Define `ErrorDeApi`, una
excepción con `status` y `erroresPorCampo`, así que un formulario puede mostrar el
error debajo del campo que corresponde sin que cada página vuelva a interpretar el
JSON del backend.

**`auth/AuthProvider.jsx`** es un contexto de React que expone
`{listo, autenticado, roles, usuario, puede, entrar, salir}`. Le instala al cliente de
la API una función que devuelve el token vigente, renovándolo si le quedan menos de
treinta segundos. Así ninguna pantalla se ocupa del token.

**`auth/keycloak.js`** inicializa keycloak-js. Guarda la promesa de `init()` en una
variable del módulo, y eso no es adorno: React en modo estricto monta cada componente
dos veces durante el desarrollo para detectar efectos mal escritos, y `init()` de
Keycloak solo puede llamarse una vez. Sin ese cuidado, la segunda llamada falla y la
sesión no arranca.

**`auth/RutaProtegida.jsx`** envuelve cada ruta y tiene tres respuestas: si la sesión
todavía no se resolvió muestra un cargando; si no hay sesión redirige a `/entrar`
recordando a dónde iba; si hay sesión pero el rol no alcanza manda a
`/acceso-denegado`.

**`App.jsx`** es el mapa de rutas. `/entrar` queda fuera del layout —hay que poder
verla sin sesión— y todo lo demás va adentro de `RutaProtegida`, cada ruta con la
acción que exige.

### Las páginas

| Carpeta | Pantallas |
|---|---|
| `paginas/` | Entrar, Panel, Perfil, Posiciones, Bitácora, AccesoDenegado, NoEncontrado |
| `paginas/competidores/` | Lista, Detalle, Formulario |
| `paginas/equipos/` | Lista, Detalle, Formulario |
| `paginas/carreras/` | Lista, Detalle, Formulario, GestionDeInscripciones, CargaDeResultados |

`paginas/carreras/transiciones.js` copia la máquina de estados de `RaceStatus` para
que la pantalla ofrezca solo los botones de transiciones válidas. Igual que
`permisos.js`, es una copia por comodidad: la verdad está en el backend.

### La configuración en tiempo de ejecución

Este es el detalle más particular del frontend y suele sorprender. Vite reemplaza las
variables de entorno **al compilar**, no al ejecutar, así que la dirección de la API
quedaría grabada dentro del JavaScript y la misma imagen no serviría para dos
entornos.

La solución es que el contenedor escriba la configuración al arrancar.
`frontend/docker/config.sh` se ejecuta antes de que nginx levante y genera un archivo
`/config.js` con las direcciones que correspondan:

```js
window.__CONFIG__ = { apiUrl: "http://localhost:8080", keycloakUrl: "...", ... };
```

`index.html` carga ese archivo **antes** del bundle, y `src/config.js` lee de ahí.
Así la misma imagen compilada sirve para cualquier entorno cambiando variables de
entorno.

`frontend/nginx.conf` tiene la otra pieza necesaria:
`try_files $uri $uri/ /index.html`. Sin eso, recargar el navegador en
`/carreras/algo` da 404, porque esa ruta no existe como archivo: existe solo dentro
del router de React. La directiva le dice a nginx que si no encuentra el archivo
devuelva `index.html` y deje que React resuelva la ruta.

---

## 11. Docker y el arranque

`compose.yml` define cuatro servicios:

| Servicio | Imagen | Puerto | Qué hace |
|---|---|---|---|
| `db` | `postgres:15-alpine` | 5432 | La base. Sus datos están en un volumen con nombre. |
| `keycloak` | `quay.io/keycloak/keycloak:26.4` | 8180 | Importa el realm al arrancar y queda listo. |
| `app` | se construye desde `Dockerfile` | 8080 | La API. |
| `web` | se construye desde `frontend/Dockerfile` | 3000 | nginx con el frontend compilado. |

Los cuatro tienen **healthcheck**, y las dependencias esperan a que el anterior esté
sano, no solo arrancado. Eso importa: sin ello, la API arranca antes que Keycloak,
intenta bajar la clave pública, no la encuentra y se cae. Con `service_healthy`,
compose espera.

Los dos `Dockerfile` son de **dos etapas**, y la idea es la misma en los dos: la
imagen que compila no es la que se publica.

En el backend, la primera etapa tiene Gradle y el JDK completo y produce el `.jar`; la
segunda tiene solo un JRE y el `.jar` copiado. En el frontend, la primera etapa tiene
Node y produce archivos estáticos; la segunda tiene nginx y esos archivos, sin Node,
sin `npm`, sin `node_modules` y sin el código fuente. La imagen final del frontend
pesa unos 50 MB contra más de 1 GB. Pero el punto no es solo el tamaño: lo que no está
en la imagen no se puede explotar ni hay que parchearlo.

Los perfiles de Spring separan las configuraciones. `application.yml` tiene lo común,
`application-dev.yml` sirve para correr contra un Postgres propio fuera de Docker, y
`application-docker.yml` es el que usa el contenedor. Se elige con
`SPRING_PROFILES_ACTIVE`, que compose fija en `docker`.

Dos detalles que costaron encontrar y quedaron documentados en el propio código, por
si aparecen otra vez:

El healthcheck del frontend usa `http://127.0.0.1/` y no `http://localhost/`. Adentro
del contenedor, `localhost` resuelve primero a IPv6 y este nginx escucha solo en IPv4,
así que el chequeo fallaba y el contenedor quedaba marcado como enfermo mientras desde
afuera el sitio se veía perfecto.

`.gitattributes` fuerza `*.sh text eol=lf`. Un guion de shell con finales de línea de
Windows falla adentro de un contenedor de Linux de una forma desconcertante: el
retorno de carro queda pegado al final de la primera línea, el núcleo busca `/bin/sh`
más un carácter invisible, no lo encuentra, y el error dice `not found` nombrando **al
guion**. Parece que falta el archivo cuando en realidad está y lo que no existe es el
intérprete.

---

## 12. Las pruebas

Setenta y una pruebas en tres capas. La tabla que dice exactamente dónde está cada una
de las quince que nombra el enunciado está en `src/test/README.md`.

```bash
./gradlew test                    # las 71
./gradlew test -Pcapa=unit        # 44, en segundos, sin Docker
./gradlew test -Pcapa=integracion # 24, necesitan Docker
./gradlew test -Pcapa=e2e         # 3, necesitan Docker
```

Las capas se eligen con `@Tag` de JUnit 5, y las etiquetas están centralizadas en
`support/Capas.java` para que no se escriban a mano. El `build.gradle` conecta el
parámetro `-Pcapa=` con `includeTags`.

**Unitarias** (`...Test`). Prueban una clase sola, con sus dependencias reemplazadas
por dobles de Mockito. `CompetitorServiceTest`, `RaceServiceTest`,
`RegistrationServiceTest` y `ResultServiceTest` verifican las reglas de negocio;
`ResultStatusTest` verifica el puntaje; los `...ValidacionTest` verifican que las
anotaciones de los DTOs efectivamente rechazan lo que tienen que rechazar. No tocan
base ni red, así que corren en segundos.

**De integración** (`...IT`). Prueban las consultas contra un **PostgreSQL real** que
Testcontainers levanta y apaga solo. `support/PostgresTestBase.java` implementa el
patrón de contenedor único: el contenedor se crea una vez para toda la corrida en vez
de uno por clase.

**De punta a punta** (`...E2ETest`). Levantan la aplicación entera más Postgres más
Keycloak, piden un token de verdad y recorren el ciclo completo de una carrera con
peticiones HTTP.

**Por qué no se usa H2**, que sería más rápido. Tres razones concretas, no una
preferencia: la consulta de la tabla de posiciones agrupa de una forma que H2 acepta y
Postgres no, la de la bitácora esquiva un problema de tipos que solo existe en
Postgres, y las migraciones usan sintaxis que H2 no entiende. Probar contra un motor
distinto del de producción es probar otra cosa.

Aparte de las pruebas de Java, `docs/coleccion-postman.json` tiene 53 peticiones
ordenadas según el ciclo de vida de una carrera. Se importa, se aprieta Run y pasa
entera contra una base recién creada. Incluye una carpeta con los errores que el
sistema maneja, cada uno con su comprobación.

---

## 13. Comparación con los proyectos de Dylan Ladrón

Los cuatro proyectos de clase son el mismo sistema (un restaurante: chefs, clientes,
platos y compras) creciendo en cuatro pasos: `main` es el esqueleto, `conexiones`
agrega las relaciones entre entidades, `login` agrega Keycloak, y `pruebas` agrega las
pruebas automatizadas.

Este proyecto **sigue esa arquitectura**. No es una casualidad ni una coincidencia: es
la misma estructura llevada a un dominio más grande.

### Qué es idéntico

| Aspecto | En los proyectos de clase | Acá |
|---|---|---|
| Paquete por dominio | `chef/`, `client/`, `dish/`, `purchase/` | `competitor/`, `team/`, `race/`, `registration/`, `result/`, `standings/`, `audit/`, `auth/` |
| Subcarpetas por módulo | `controller`, `service`, `repository`, `entity`, `dto`, `mapper` | las mismas |
| Repositorios con `I` | `IChefRepository` | `ICompetitorRepository` |
| DTOs como `record` | `ChefRequest`, `ChefResponse` | `CompetitorRequest`, `CompetitorResponse` |
| Mapper con constructor privado y métodos `static` | `ChefMapper` | `CompetitorMapper` |
| Controlador con `@AllArgsConstructor` y `@Slf4j` | `ChefController` | `CompetitorController` |
| Servicio con `@RequiredArgsConstructor` | `ChefService` | `CompetitorService` |
| Entidad con Lombok y `@Builder` | `Chef` | `Competitor` |
| Manejo global de errores | `GlobalExceptionHandler` + `ErrorResponse` | los mismos, ampliados |
| `open-in-view: false` | sí, con el mismo motivo | igual |
| Perfiles de Spring | `dev`, `docker` | `dev`, `docker` |
| Java 25 y Spring Boot 4.1.0 | sí | igual |
| springdoc 3.1.0 fijado a mano | sí, por Boot 4 | igual |
| Traductor de roles de Keycloak | `KeycloakRolesConverter` en `login` | el mismo, palabra por palabra |
| Resource server sin controlador de login | sí | igual |
| CSRF apagado y sesiones `STATELESS` | sí | igual |
| Testcontainers con contenedor único | `PostgresTestBase` en `pruebas` | `PostgresTestBase` |
| Capas de prueba con `@Tag` | `support/Capas.java` | el mismo patrón |
| Dockerfile de dos etapas | sí | igual |

La deuda es explícita: el `KeycloakRolesConverter`, la separación en capas de prueba,
el patrón de contenedor único y la decisión de no tener controlador de login vienen
directamente de los proyectos de clase.

### Qué se agregó

Estas son las diferencias reales, y en cada caso hay un motivo:

**Ocho módulos en lugar de cuatro, y con relaciones más difíciles.** En el restaurante,
una compra apunta a un cliente y a un plato: dos claves foráneas obligatorias y
simples. Acá una inscripción apunta a una carrera y a **uno de dos** participantes
posibles, lo que exige dos claves anulables más un `CHECK`; y un resultado se cuelga de
la inscripción, no del competidor.

**Flyway.** Los proyectos de clase dejan que Hibernate genere el esquema. Acá el
esquema son tres archivos SQL versionados y Hibernate está en `validate`, que no toca
nada y falla el arranque si una entidad no coincide con su tabla. Esto habilitó poner
en la base restricciones que Hibernate no sabe generar: los `CHECK` compuestos, el de
uno-de-dos participantes y el de coherencia de resultados.

**PostgreSQL únicamente.** Los proyectos de clase incluyen H2 y su consola para
desarrollar rápido. Acá no está: el `build.gradle` no trae H2 ni en las pruebas, por
las tres razones de la sección anterior.

**Máquinas de estado dentro de los enumerados.** `RaceStatus` declara sus propias
transiciones válidas y `ResultStatus` calcula el puntaje. En el restaurante no hay
nada equivalente porque no hay ningún ciclo de vida que modelar.

**Reglas por rol, no por método HTTP.** El `SecurityConfig` de `login` tiene una regla
por verbo: todos los GET para los dos roles, todos los POST/PUT/PATCH/DELETE para
admin. Acá el enunciado pide tres roles con permisos distintos por recurso, así que las
reglas cruzan verbo con ruta: escribir competidores y equipos es de `admin`, mientras
operar carreras, inscripciones y resultados es de `admin` y `organizer`, y la bitácora
es solo de `admin`.

**CORS configurado.** En los proyectos de clase no hace falta, porque quien consume la
API es Swagger, servido por la misma aplicación y por lo tanto del mismo origen. Acá
hay un frontend en otro puerto, y sin CORS el navegador bloquea todas las llamadas
antes de que salgan.

**`SecurityErrorResponder`.** Los errores de seguridad ocurren en los filtros, antes
del `GlobalExceptionHandler`, así que en los proyectos de clase un 401 tiene un cuerpo
distinto al del resto de la API. Como acá hay un frontend que maneja errores en un solo
lugar, hizo falta unificarlos.

**`PageResponse` y paginación en todos los listados.** Los proyectos de clase devuelven
`List<ChefResponse>`. Acá todos los listados vienen paginados con la misma envoltura,
que es lo que pide el enunciado y lo que hace falta cuando la lista puede crecer.

**Bitácora de auditoría.** No existe en el restaurante. Acá los siete servicios llaman
a `AuditService` en cada operación que cambia algo.

**Baja lógica.** En el restaurante, borrar borra. Acá nada se elimina físicamente: un
competidor pasa a `RETIRED`, un equipo a `INACTIVE`, una carrera a `CANCELLED`. El
motivo es de integridad histórica: borrar un competidor obligaría a borrar sus
resultados, y con eso cambiarían los puestos de carreras que ya se corrieron.

**Actuator y healthchecks.** `/actuator/health` es lo que consulta Docker para decidir
si el contenedor está sano, y es lo que permite que compose arranque los servicios en
el orden correcto.

**Un frontend propio.** Los proyectos de clase se prueban desde Swagger y traen una
página de ayuda estática. Acá hay una aplicación en React servida por nginx, con su
propio contenedor, su login con PKCE contra Keycloak y su configuración generada al
arrancar.

**`CurrentUser` como componente.** En los proyectos de clase, cuando hace falta saber
quién pide, se rescata del contexto en el lugar. Acá está encapsulado en un
`@Component` porque lo usan siete servicios y porque en las pruebas conviene poder
reemplazarlo.

### Resumen de la comparación

Si hay que decirlo en dos frases: la **arquitectura** es la de los proyectos de clase,
sin desviaciones —paquete por dominio, las mismas seis subcarpetas, las mismas
convenciones de nombres, el mismo manejo de errores, el mismo enfoque de seguridad—.
Lo que se agregó son las piezas que el enunciado pide y que en un proyecto de cuatro
entidades sin ciclo de vida no hacían falta: control de versiones del esquema,
máquinas de estado, auditoría, baja lógica, paginación, permisos por rol y una
interfaz propia.

---

## 14. Preguntas previsibles y su respuesta

**¿Qué es un bean?**
Un objeto que Spring crea y administra. Las clases se anotan (`@Service`,
`@RestController`, `@Component`, `@Configuration`) y Spring las instancia al arrancar y
las conecta entre sí. Acá la inyección es siempre por constructor, con campos `final` y
`@RequiredArgsConstructor` de Lombok.

**¿Por qué no se devuelve la entidad directamente?**
Porque ataría el contrato de la API al esquema de la base, filtraría columnas internas,
rompería al serializar relaciones perezosas con `open-in-view: false`, y no permitiría
agregar campos calculados como la edad.

**¿Dónde está la lógica de negocio?**
En los servicios. Los controladores solo reciben, delegan y devuelven; los repositorios
solo consultan.

**¿Por qué hay validación en cuatro lugares?**
Porque cada capa ve algo distinto. El DTO valida el formato y no puede saber si un
apodo ya existe. El servicio valida las reglas y no puede impedir que alguien escriba
en la base por fuera de la aplicación. La base tiene la última palabra con sus
restricciones.

**¿Por qué no hay `/api/auth/login`?**
Porque la aplicación no guarda contraseñas ni emite tokens: eso lo hace Keycloak. La
API es un resource server, que valida tokens firmados por otro. El enunciado permite
explícitamente resolver la seguridad con Keycloak.

**¿Por qué no hay tabla de usuarios ni de roles?**
Porque viven en Keycloak. El enunciado pide entidades "equivalentes a" `User` y `Role`,
y Keycloak cumple ese papel. Un usuario nuevo se crea en Keycloak, no con un `INSERT`.

**¿Y `TeamMember`?**
No hace falta una tabla intermedia porque un competidor pertenece a un solo equipo: es
una relación uno a muchos, y se resuelve con la columna `team_id` en `competitors`. Una
tabla intermedia solo tendría sentido si un competidor pudiera estar en varios equipos.

**¿Qué pasa si alguien modifica el token?**
Deja de validar. La firma la hizo Keycloak con su clave privada; cualquier cambio en el
contenido la invalida, y la API rechaza el token con 401.

**¿Por qué CSRF está apagado?**
Porque el ataque que previene se apoya en que el navegador manda la cookie de sesión
sola. Acá no hay cookie de sesión: la autorización va en una cabecera que hay que poner
a mano, y una página ajena no puede hacerlo. Dejarlo activo rompería todos los `POST`
sin agregar seguridad.

**¿Qué diferencia hay entre 401 y 403?**
401 es "no sé quién sos": falta el token, o está vencido, o la firma no cierra. 403 es
"sé quién sos y no te alcanza": el token es válido pero el rol no tiene permiso.

**¿Por qué Flyway y no `ddl-auto: update`?**
Porque el esquema queda escrito y versionado en archivos que se pueden leer y revisar,
en vez de ser lo que Hibernate haya decidido. Además Hibernate no genera `CHECK`
compuestos, y son los que garantizan las reglas más importantes del modelo. Hibernate
queda en `validate`, que no toca nada y falla el arranque si una entidad no coincide con
su tabla.

**¿Por qué Testcontainers y no H2?**
Porque tres cosas del proyecto se comportan distinto: la consulta de la tabla de
posiciones agrupa de una forma que H2 acepta y Postgres no, la de la bitácora esquiva un
problema de tipos propio de Postgres, y las migraciones usan sintaxis que H2 no
entiende. Probar contra otro motor sería probar otra cosa.

**¿Qué es el problema N+1 y dónde se resuelve?**
Es hacer una consulta para la lista y después una más por cada elemento para traer una
relación perezosa. Se resuelve con `@EntityGraph(attributePaths = "team")` en los
repositorios, que trae todo con un `JOIN` en un solo viaje.

**¿Por qué las estadísticas están guardadas en la fila?**
Porque la ficha de un competidor las muestra siempre y recalcularlas exigiría recorrer
todos sus resultados en cada consulta. El costo de la decisión está asumido y
documentado: hay que mantenerlas al día al cargar un resultado, y con varias instancias
de la API en paralelo podrían pisarse.

**¿Por qué nada se borra?**
Porque borrar un competidor obligaría a borrar sus resultados, y eso cambiaría los
puestos de carreras que ya terminaron. Las bajas son de estado: `RETIRED`, `INACTIVE`,
`CANCELLED`.

**¿Cómo se garantiza que un descalificado no pueda ganar?**
Con la restricción `ck_results_coherencia_estado`, que solo permite puesto y tiempo en
un resultado `FINISHED`. Un descalificado no puede tener puesto, así que tampoco el
primero. Está garantizado en la base, no solamente en el código.

**¿Por qué el token se guarda en memoria y no en `localStorage`?**
Porque cualquier script que llegue a ejecutarse en la página puede leer `localStorage`
entero y llevarse una credencial válida. En memoria muere al cerrar la pestaña, y al
recargar la sesión se recupera preguntándole a Keycloak si su cookie sigue viva.

**Si el frontend controla los permisos, ¿no se puede saltar?**
El frontend no controla nada: solo evita mostrar botones que van a fallar.
`permisos.js` es una copia de la tabla de roles para la interfaz. La decisión real la
toma `SecurityConfig` en el servidor, y eso no se puede saltar desde el navegador.

**¿Por qué el frontend necesita un `config.js` generado al arrancar?**
Porque Vite reemplaza las variables de entorno al compilar, no al ejecutar. Sin ese
archivo, la dirección de la API quedaría grabada dentro del JavaScript y la misma imagen
no serviría para dos entornos. El contenedor lo escribe antes de que nginx levante.

**¿Por qué el máximo de integrantes es una variable de entorno?**
Porque el enunciado pide que el límite sea configurable. Está en
`application.yml` como `${TEAM_MAX_MEMBERS:5}` y llega desde el `.env` a través de
`compose.yml`.

**¿Qué pasa si se cae Keycloak?**
Los tokens ya emitidos siguen validando, porque la API tiene la clave pública en
memoria y no consulta a Keycloak en cada pedido. Lo que no se puede es iniciar sesión
ni renovar un token vencido.

**¿Cuántos endpoints hay?**
Treinta y cuatro, repartidos en los ocho módulos. La lista completa está en el `README`
y en `http://localhost:8080/swagger-ui.html`.
