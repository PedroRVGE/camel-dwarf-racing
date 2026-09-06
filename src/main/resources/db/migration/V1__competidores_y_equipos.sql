-- =============================================================================
--  V1: equipos y competidores
-- =============================================================================
--  Primera migracion del esquema. Flyway la aplica una sola vez y deja registro
--  en la tabla flyway_schema_history; despues de aplicada NO se toca, porque
--  Flyway guarda un checksum del archivo y cualquier cambio hace fallar el
--  arranque. Para corregir algo se escribe una migracion nueva.
--
--  Hibernate corre con ddl-auto: validate, asi que al arrancar compara estas
--  tablas contra las entidades. Si un tipo o un nombre de columna no coinciden,
--  la aplicacion no levanta y el error aparece aca y no en el primer request.
--
--  El orden importa: teams va primero porque competitors la referencia.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  EQUIPOS
-- -----------------------------------------------------------------------------
CREATE TABLE teams (
    id          UUID          NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500)  NOT NULL,
    coach       VARCHAR(150)  NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    victories   INTEGER       NOT NULL DEFAULT 0,
    defeats     INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT pk_teams PRIMARY KEY (id),

    -- El enunciado pide que el nombre del equipo sea unico.
    --
    -- La constraint va en la BASE ademas del chequeo que hace TeamService. No es
    -- redundancia: el chequeo del servicio consulta y despues inserta, y entre esas
    -- dos operaciones puede colarse otro request. Los dos consultan, los dos ven el
    -- nombre libre, los dos insertan. La base es el unico lugar donde "unico"
    -- significa unico pase lo que pase.
    CONSTRAINT uk_teams_name UNIQUE (name),

    -- Los enums se guardan como texto (@Enumerated(EnumType.STRING)), y este CHECK
    -- es lo que impide que la columna termine con cualquier cosa adentro.
    --
    -- Hace falta porque la aplicacion no es el unico camino hacia la base: una
    -- correccion a mano con psql, un script de migracion de datos o un backup mal
    -- restaurado pueden meter un valor que el enum no conoce. Cuando eso pasa, la
    -- aplicacion revienta al LEER esa fila, con un error que no dice cual es.
    CONSTRAINT ck_teams_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

-- Los listados filtran por estado ("mostrame solo los equipos activos"). Sin
-- indice, ese filtro obliga a leer la tabla entera fila por fila.
CREATE INDEX idx_teams_status ON teams (status);


-- -----------------------------------------------------------------------------
--  COMPETIDORES
-- -----------------------------------------------------------------------------
CREATE TABLE competitors (
    id                UUID          NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    nickname          VARCHAR(100)  NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    date_of_birth     DATE          NOT NULL,

    -- NUMERIC y no DOUBLE PRECISION: los decimales binarios no representan exacto
    -- valores como 0.1, y eso se acumula al sumar. (5,2) entra hasta 999,99, que
    -- sobra para un camello de unos 600 kg.
    weight_kg         NUMERIC(5, 2) NOT NULL,
    height_cm         NUMERIC(5, 2) NOT NULL,

    country_of_origin VARCHAR(100)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    registered_at     TIMESTAMP(6)  NOT NULL,

    -- Sin NOT NULL: el equipo es opcional, porque el enunciado admite
    -- competidores individuales ("optional team").
    team_id           UUID,

    victories         INTEGER       NOT NULL DEFAULT 0,
    defeats           INTEGER       NOT NULL DEFAULT 0,
    completed_races   INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT pk_competitors PRIMARY KEY (id),

    -- "Nickname must be unique", regla del enunciado. Mismo razonamiento que con
    -- el nombre del equipo: el chequeo del servicio da el buen mensaje de error,
    -- esta constraint garantiza que la regla no se pueda violar.
    CONSTRAINT uk_competitors_nickname UNIQUE (nickname),

    CONSTRAINT ck_competitors_type
        CHECK (type IN ('DWARF', 'CAMEL', 'MEDIUM', 'OTHER')),
    CONSTRAINT ck_competitors_status
        CHECK (status IN ('ACTIVE', 'INJURED', 'SUSPENDED', 'RETIRED')),

    -- El peso y la altura tienen que ser positivos.
    --
    -- Ya lo validan las anotaciones @Positive del DTO, y esta igual por el mismo
    -- motivo que los CHECK de los enums: las anotaciones protegen la puerta de
    -- entrada de la API, el CHECK protege la tabla. Si manana se agrega un script
    -- de importacion masiva que no pase por la API, esta regla sigue valiendo.
    CONSTRAINT ck_competitors_weight CHECK (weight_kg > 0),
    CONSTRAINT ck_competitors_height CHECK (height_cm > 0),

    -- ON DELETE es deliberadamente el comportamiento por defecto (NO ACTION), o
    -- sea que la base RECHAZA borrar un equipo que todavia tenga integrantes.
    --
    -- No es un problema, porque en esta aplicacion los equipos no se borran: se
    -- dan de baja, y al hacerlo TeamService libera antes a los integrantes. Esta
    -- foreign key es la red que garantiza que nadie pueda dejar competidores
    -- apuntando a un equipo que ya no existe, ni siquiera desde afuera de la
    -- aplicacion.
    --
    -- ON DELETE CASCADE seria un error grave aca: borrar un equipo se llevaria
    -- puestos a sus cinco enanos con todo su historial de carreras.
    CONSTRAINT fk_competitors_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

-- Los tres filtros del listado de competidores. El de team_id ademas acelera el
-- "traeme los integrantes de este equipo", que se usa en cada consulta de un
-- equipo puntual.
CREATE INDEX idx_competitors_status ON competitors (status);
CREATE INDEX idx_competitors_type ON competitors (type);
CREATE INDEX idx_competitors_team ON competitors (team_id);
