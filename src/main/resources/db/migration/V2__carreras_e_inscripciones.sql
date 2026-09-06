-- =============================================================================
--  V2: carreras e inscripciones
-- =============================================================================
--  Segunda migracion. Igual que la V1, una vez aplicada NO se toca: Flyway guarda
--  su checksum y cualquier cambio posterior hace fallar el arranque. Para
--  corregir algo se escribe una V3.
--
--  Hibernate arranca con ddl-auto: validate, asi que estas tablas tienen que
--  coincidir exactamente con las entidades Race y RaceRegistration. Un
--  VARCHAR(20) donde la entidad declara length = 30 rompe el arranque, y esta
--  bien que lo rompa: es mejor enterarse al levantar que cuando un estado largo
--  no entre en la columna.
--
--  El orden importa: races va primero porque race_registrations la referencia.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  CARRERAS
-- -----------------------------------------------------------------------------
CREATE TABLE races (
    id                    UUID          NOT NULL,
    name                  VARCHAR(150)  NOT NULL,
    description           VARCHAR(500)  NOT NULL,
    scheduled_at          TIMESTAMP(6)  NOT NULL,
    start_location        VARCHAR(150)  NOT NULL,
    finish_location       VARCHAR(150)  NOT NULL,

    -- Metros enteros, sin decimales. La unidad va en el nombre de la columna para
    -- que a nadie se le ocurra cargar kilometros aca adentro.
    distance_meters       INTEGER       NOT NULL,

    max_participants      INTEGER       NOT NULL,
    type                  VARCHAR(20)   NOT NULL,

    -- 30 y no 20: el estado mas largo es CLOSED_FOR_REGISTRATION, que tiene 23
    -- caracteres. Es el tipo de detalle que ddl-auto: validate no perdona.
    status                VARCHAR(30)   NOT NULL,

    -- El organizador es un texto, no una foreign key: los usuarios viven en
    -- Keycloak, no en esta base. La explicacion completa esta en la entidad Race.
    organizer             VARCHAR(100)  NOT NULL,

    registration_deadline TIMESTAMP(6)  NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL,
    updated_at            TIMESTAMP(6)  NOT NULL,

    CONSTRAINT pk_races PRIMARY KEY (id),

    -- Los dos enums, con el mismo criterio que en la V1: la columna guarda texto
    -- (@Enumerated(EnumType.STRING)) y el CHECK es lo unico que impide que termine
    -- con cualquier cosa adentro si alguien escribe directo en la base.
    CONSTRAINT ck_races_type
        CHECK (type IN ('INDIVIDUAL', 'TEAM', 'MIXED')),
    CONSTRAINT ck_races_status
        CHECK (status IN ('DRAFT', 'OPEN_FOR_REGISTRATION', 'CLOSED_FOR_REGISTRATION',
                          'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),

    CONSTRAINT ck_races_distance CHECK (distance_meters > 0),

    -- Minimo 2, y no 1, por la misma razon que en el DTO: el enunciado exige al
    -- menos dos participantes para largar, asi que una carrera con cupo 1 seria
    -- una carrera que nunca podria empezar.
    CONSTRAINT ck_races_max_participants CHECK (max_participants >= 2),

    -- La regla de las dos fechas, tambien en la base.
    --
    -- Es un CHECK que compara DOS COLUMNAS de la misma fila, que es algo que un
    -- CHECK puede hacer perfectamente. Ya lo valida @AssertTrue en RaceRequest,
    -- con un mensaje entendible; esto es la garantia de que la regla no se pueda
    -- violar desde afuera de la API.
    --
    -- Lo que NO se puede poner aca es "la fecha tiene que estar en el futuro": un
    -- CHECK tiene que ser inmutable, y una condicion contra la hora actual deja de
    -- cumplirse sola con el paso del tiempo. Toda carrera pasada volveria invalida
    -- la tabla entera y hasta un backup restaurado fallaria. Esa regla vive en el
    -- @Future del DTO, que es donde corresponde: se controla al escribir, no para
    -- siempre.
    CONSTRAINT ck_races_deadline_antes_de_largada
        CHECK (registration_deadline < scheduled_at)
);

-- Los dos filtros del listado, mas la fecha, que ademas es el orden por defecto y
-- lo que usa la pantalla de "proximas carreras".
CREATE INDEX idx_races_status ON races (status);
CREATE INDEX idx_races_type ON races (type);
CREATE INDEX idx_races_scheduled_at ON races (scheduled_at);


-- -----------------------------------------------------------------------------
--  INSCRIPCIONES
-- -----------------------------------------------------------------------------
CREATE TABLE race_registrations (
    id               UUID          NOT NULL,
    race_id          UUID          NOT NULL,

    -- Las dos son opcionales POR SEPARADO, porque una inscripcion es de un
    -- competidor o de un equipo. Que sea de exactamente uno de los dos lo garantiza
    -- el CHECK de mas abajo.
    competitor_id    UUID,
    team_id          UUID,

    status           VARCHAR(20)   NOT NULL,

    -- Sin NOT NULL a proposito: una inscripcion recien pedida todavia no tiene
    -- carril, y ese "todavia no" es distinto de cualquier numero.
    lane             INTEGER,

    registered_at    TIMESTAMP(6)  NOT NULL,
    registered_by    VARCHAR(100)  NOT NULL,
    validation_notes VARCHAR(500),

    CONSTRAINT pk_race_registrations PRIMARY KEY (id),

    -- "Starting positions cannot be duplicated", regla del enunciado.
    --
    -- Postgres permite varios NULL dentro de una constraint unica, y aca eso es
    -- justo lo que hace falta: todas las inscripciones sin carril asignado conviven
    -- sin chocar, y la unicidad empieza a valer recien cuando el carril existe.
    CONSTRAINT uk_registrations_race_lane UNIQUE (race_id, lane),

    CONSTRAINT ck_registrations_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),

    -- Los carriles se numeran desde 1. El servicio asigna max + 1 empezando por el
    -- uno, y el DTO ya rechaza los no positivos.
    CONSTRAINT ck_registrations_lane CHECK (lane IS NULL OR lane > 0),

    -- EL CHECK MAS IMPORTANTE DE LA TABLA
    -- Exactamente uno de los dos participantes tiene que estar presente: o compite
    -- un competidor individual, o compite un equipo. Ni los dos ni ninguno.
    --
    -- La regla ya la valida @AssertTrue en RegistrationRequest, que es lo que da el
    -- mensaje 400 entendible. Esta constraint existe porque sin ella una fila con
    -- los dos ids en null seria una inscripcion sin participante, y el codigo que
    -- la lea no tendria forma de decidir que es. Es la clase de dato imposible que
    -- conviene que la base directamente no acepte.
    CONSTRAINT ck_registrations_un_participante
        CHECK ((competitor_id IS NOT NULL AND team_id IS NULL)
            OR (competitor_id IS NULL AND team_id IS NOT NULL)),

    -- Las tres foreign keys, todas con el ON DELETE por defecto (NO ACTION), o sea
    -- que la base rechaza borrar una carrera, un competidor o un equipo que tenga
    -- inscripciones.
    --
    -- No molesta, porque en esta aplicacion nada se borra: cancelar, retirar y dar
    -- de baja son cambios de estado. Lo que si evita es que un DELETE hecho a mano
    -- deje inscripciones colgando de algo que ya no existe.
    CONSTRAINT fk_registrations_race
        FOREIGN KEY (race_id) REFERENCES races (id),
    CONSTRAINT fk_registrations_competitor
        FOREIGN KEY (competitor_id) REFERENCES competitors (id),
    CONSTRAINT fk_registrations_team
        FOREIGN KEY (team_id) REFERENCES teams (id)
);

-- El indice de race_id es el que mas trabaja: casi toda consulta de inscripciones
-- empieza por "las de esta carrera". Los de competitor_id y team_id sirven para el
-- historial de un participante, y el de status para separar pendientes de
-- aprobadas dentro de una misma carrera.
CREATE INDEX idx_registrations_race ON race_registrations (race_id);
CREATE INDEX idx_registrations_competitor ON race_registrations (competitor_id);
CREATE INDEX idx_registrations_team ON race_registrations (team_id);
CREATE INDEX idx_registrations_status ON race_registrations (status);
