-- =============================================================================
--  V3: resultados y bitacora de auditoria
-- =============================================================================
--  Las dos tablas que cierran el modelo. Van juntas en una sola migracion porque
--  llegan con el mismo modulo, aunque no se relacionan entre si: audit_logs no
--  apunta a ninguna tabla, y esa es justamente su caracteristica principal (ver
--  la entidad AuditLog).
--
--  Como siempre: una vez aplicada no se toca, Flyway guarda su checksum. Y tiene
--  que coincidir exactamente con las entidades, porque Hibernate arranca con
--  ddl-auto: validate.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  RESULTADOS
-- -----------------------------------------------------------------------------
CREATE TABLE race_results (
    id                      UUID          NOT NULL,

    -- La carrera esta desnormalizada: se puede deducir de la inscripcion. Se
    -- guarda igual, y no es por comodidad: es lo que permite la constraint unica
    -- (race_id, final_position), que es la que hace imposible que existan dos
    -- ganadores. Una constraint no puede mirar una columna de otra tabla.
    race_id                 UUID          NOT NULL,

    -- El resultado es de la INSCRIPCION, no del competidor. La explicacion larga
    -- esta en la entidad RaceResult: es lo que permite que el participante sea un
    -- competidor o un equipo sin repetir aca esa logica, y lo que hace verificable
    -- la regla "solo los aprobados pueden tener resultado".
    registration_id         UUID          NOT NULL,

    -- Copia del carril de la inscripcion al momento de cargar el resultado. Una
    -- foto, no un espejo: la grilla de largada de una carrera ya corrida no cambia
    -- porque alguien toque la inscripcion despues.
    starting_position       INTEGER,

    -- Sin NOT NULL, y es la columna que sostiene media docena de reglas: el que
    -- abandono, el descalificado y el que no largo NO tienen puesto.
    final_position          INTEGER,

    -- Segundos transcurridos, no hora de llegada. Es lo que vuelve imposible de
    -- escribir el absurdo del enunciado ("a camel finishing before the race
    -- began"): una duracion positiva no puede ser anterior a nada.
    completion_time_seconds INTEGER,

    penalty_time_seconds    INTEGER       NOT NULL DEFAULT 0,
    status                  VARCHAR(20)   NOT NULL,
    notes                   VARCHAR(500),
    recorded_by             VARCHAR(100)  NOT NULL,
    recorded_at             TIMESTAMP(6)  NOT NULL,
    updated_at              TIMESTAMP(6)  NOT NULL,

    CONSTRAINT pk_race_results PRIMARY KEY (id),

    -- Un participante, un resultado. Dos filas para la misma inscripcion serian dos
    -- afirmaciones sobre el mismo hecho, y las estadisticas lo contarian dos veces.
    CONSTRAINT uk_results_registration UNIQUE (registration_id),

    -- "Only one official winner is allowed" y "final positions cannot be
    -- duplicated among normal finishers".
    --
    -- Postgres admite varios NULL en una constraint unica, y aca es exactamente lo
    -- que hace falta: todos los que no terminaron tienen final_position en null y
    -- conviven sin chocar. Sin esa particularidad habria que inventar un puesto
    -- ficticio para los abandonos.
    CONSTRAINT uk_results_race_position UNIQUE (race_id, final_position),

    CONSTRAINT ck_results_status
        CHECK (status IN ('FINISHED', 'DISQUALIFIED', 'DID_NOT_FINISH', 'DID_NOT_START')),

    -- "Completion time must be positive."
    CONSTRAINT ck_results_completion_time
        CHECK (completion_time_seconds IS NULL OR completion_time_seconds > 0),
    CONSTRAINT ck_results_penalty_time
        CHECK (penalty_time_seconds >= 0),
    CONSTRAINT ck_results_positions
        CHECK ((final_position IS NULL OR final_position > 0)
           AND (starting_position IS NULL OR starting_position > 0)),

    -- LA REGLA MAS IMPORTANTE DE LA TABLA, Y LA QUE MAS ABSURDOS EVITA
    -- El estado y los datos de llegada tienen que decir lo mismo: si termino, hay
    -- puesto y tiempo; si no termino, no hay ninguno de los dos.
    --
    -- De aca sale, sin escribirla aparte, la regla "a disqualified participant
    -- cannot win": un DISQUALIFIED no puede tener puesto, asi que tampoco puede
    -- tener el puesto 1.
    --
    -- Ya lo valida @AssertTrue en ResultRequest, que es lo que da el mensaje 400
    -- entendible. Esta constraint es la garantia de que la regla no se pueda violar
    -- desde afuera de la API, y ademas es lo que permite que la consulta de la tabla
    -- de posiciones se apoye en el invariante "solo FINISHED tiene posicion".
    CONSTRAINT ck_results_coherencia_estado CHECK (
        (status = 'FINISHED'
             AND final_position IS NOT NULL
             AND completion_time_seconds IS NOT NULL)
        OR (status <> 'FINISHED'
             AND final_position IS NULL
             AND completion_time_seconds IS NULL)
    ),

    CONSTRAINT fk_results_race
        FOREIGN KEY (race_id) REFERENCES races (id),
    CONSTRAINT fk_results_registration
        FOREIGN KEY (registration_id) REFERENCES race_registrations (id)
);

-- El de race_id es el que mas trabaja: toda consulta de resultados empieza por
-- "los de esta carrera". El de status lo usa la validacion de coherencia de
-- tiempos, que pide solo los que terminaron.
CREATE INDEX idx_results_race ON race_results (race_id);
CREATE INDEX idx_results_status ON race_results (status);


-- -----------------------------------------------------------------------------
--  BITACORA DE AUDITORIA
-- -----------------------------------------------------------------------------
--  Esta tabla no tiene NI UNA foreign key, y es deliberado.
--
--  Una bitacora tiene que poder decir "se cancelo la carrera X" aunque manana esa
--  carrera no exista mas. Con una foreign key, borrar la carrera obligaria a
--  borrar tambien su historia, que es justo lo contrario de para lo que sirve una
--  auditoria. Por eso entity_type y entity_id son datos sueltos, y por eso ademas
--  se guarda una descripcion en texto: sigue siendo legible aunque lo referenciado
--  ya no este.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id             UUID          NOT NULL,
    username       VARCHAR(100)  NOT NULL,
    action         VARCHAR(40)   NOT NULL,
    entity_type    VARCHAR(50)   NOT NULL,

    -- Sin NOT NULL: un LOGIN no es sobre ninguna entidad puntual.
    entity_id      UUID,

    occurred_at    TIMESTAMP(6)  NOT NULL,
    description    VARCHAR(500),
    previous_value VARCHAR(1000),
    new_value      VARCHAR(1000),

    CONSTRAINT pk_audit_logs PRIMARY KEY (id),

    -- NO hay CHECK sobre action, y es la unica excepcion del proyecto.
    --
    -- En el resto de las tablas el CHECK de los enums impide que entre un valor que
    -- el codigo no conoce. Aca seria contraproducente: la lista de acciones va a
    -- crecer con cada funcionalidad nueva, y un CHECK obligaria a una migracion
    -- solo para poder registrar que paso algo mas. Peor todavia, esa migracion se
    -- olvidaria justo el dia que se agrega la accion, y el efecto seria que la
    -- operacion de negocio falla por no poder auditarse.
    --
    -- El riesgo que se acepta a cambio es acotado: si apareciera un valor
    -- desconocido, la unica consecuencia seria un error al leer esa fila desde
    -- Java, y la bitacora se lee, no se opera con ella.
    CONSTRAINT ck_audit_entity_type CHECK (entity_type <> '')
);

-- La consulta natural de una bitacora es "que paso ultimamente", y el endpoint
-- ordena por fecha descendente: sin este indice, cada consulta ordena la tabla
-- entera.
CREATE INDEX idx_audit_occurred_at ON audit_logs (occurred_at);
CREATE INDEX idx_audit_username ON audit_logs (username);
CREATE INDEX idx_audit_action ON audit_logs (action);

-- Compuesto y en este orden. Resuelve "todo lo que le paso a esta carrera", que es
-- como se investiga un caso puntual, y de paso sirve para filtrar solo por tipo,
-- porque entity_type es la primera columna del indice.
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
