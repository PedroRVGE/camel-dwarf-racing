-- =============================================================================
--  DATOS DE EJEMPLO  (punto 8 del enunciado: "Minimum initial data")
-- =============================================================================
--  Esta NO es una migracion de esquema. Aca no se crea ni se modifica ninguna
--  tabla: solo se cargan los competidores, equipos, carreras y resultados que el
--  enunciado pide como datos iniciales.
--
--  POR QUE VIVE EN db/seed Y NO EN db/migration
--  Son dos cosas distintas y se aplican en momentos distintos. El esquema va en
--  todos los perfiles, incluido el de tests. Los datos de ejemplo van solo en dev
--  y en docker (ver la propiedad spring.flyway.locations en application-dev.yml y
--  application-docker.yml). Si el seed se aplicara tambien en los tests, cada
--  test de listados arrancaria con nueve competidores y siete carreras que el no
--  puso, y todos los conteos darian distinto. Los tests crean sus propios datos.
--
--  POR QUE EMPIEZA CON "R__" Y NO CON "V4__"
--  Las dos carpetas las lee el MISMO Flyway, asi que comparten la numeracion. Si
--  este archivo fuera V4, la proxima migracion de esquema tendria que ser V5, y
--  quedaria aplicada despues del seed en dev pero antes en tests: el mismo numero
--  significaria cosas distintas segun el perfil. Peor todavia, si el seed fuera
--  V100 para dejar lugar, cualquier V4 posterior seria "fuera de orden" y Flyway
--  se negaria a aplicarla.
--
--  Un archivo "repeatable" (R__) esquiva las dos cosas: no tiene numero, y Flyway
--  lo corre SIEMPRE al final, despues de todas las versionadas. A cambio, se
--  vuelve a ejecutar cada vez que cambia su contenido, y por eso cada INSERT
--  termina en ON CONFLICT DO NOTHING: los ids estan escritos a mano y son fijos,
--  asi que la segunda pasada no inserta nada. Es la unica forma de que el seed sea
--  idempotente, que es exactamente lo que se le pide a un archivo que puede correr
--  mas de una vez.
--
--  LOS USUARIOS NO ESTAN ACA
--  El enunciado pide tambien "un administrador, un organizador y un viewer". Esos
--  tres no son filas de esta base: viven en Keycloak, y se cargan solos al
--  levantar el contenedor con --import-realm (ver keycloak/realm-camelracing.json).
--  Por eso las columnas organizer, registered_by y recorded_by guardan el nombre de
--  usuario como texto y no como foreign key.
--
--  LAS FECHAS SON RELATIVAS AL MOMENTO DE LA CARGA
--  Ninguna esta escrita a mano, todas se calculan con LOCALTIMESTAMP mas o menos un
--  intervalo. Si estuvieran fijas, la carrera "abierta a inscripcion" quedaria con
--  la fecha de largada en el pasado apenas pasaran unas semanas, y la demo se veria
--  rota sin que nada este mal. Asi, el juego de datos tiene sentido el dia que se
--  levante el proyecto, sea cuando sea.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  EQUIPOS
-- -----------------------------------------------------------------------------
--  Dos equipos, como pide el enunciado. Las victorias y las derrotas que se cargan
--  aca NO son un numero inventado: son exactamente las que saldrian de recorrer los
--  resultados del final de este archivo, que es lo que hace
--  ResultService.recalcularEstadisticas cada vez que se carga o se corrige uno.
--  Si no coincidieran, la primera correccion de un resultado los "arreglaria" solo
--  y el cambio pareceria un error.
-- -----------------------------------------------------------------------------
INSERT INTO teams (id, name, description, coach, status, created_at, victories, defeats) VALUES
    ('a0000000-0000-4000-a000-000000000001',
     'Barbas de Hierro',
     'El equipo clasico de la liga: cinco enanos entrenados para correr en formacion cerrada y turnarse el frente. Su apuesta no es la velocidad punta sino no aflojar nunca el ritmo.',
     'Kildra Mano de Piedra',
     'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '90 days',
     1, 0),

    ('a0000000-0000-4000-a000-000000000002',
     'Los Medianos de la Loma',
     'Equipo joven, formado por competidores de talla media que no encajaban ni entre los enanos ni entre los camellos. Corren pocas carreras al ano y las eligen con cuidado.',
     'Perla Vientoclaro',
     'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '75 days',
     0, 1)
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
--  COMPETIDORES
-- -----------------------------------------------------------------------------
--  Cinco enanos, dos camellos y dos medianos, que es el minimo exacto del
--  enunciado. Los cinco enanos forman el equipo completo: con max-members en 5
--  (ver camelracing.teams.max-members), "Barbas de Hierro" queda justo en el
--  limite, y eso hace que agregarle un sexto integrante desde la API devuelva 409
--  sin tener que preparar nada.
--
--  Los camellos van SIN equipo a proposito. El enunciado admite competidores
--  individuales, y ademas son los que hacen posible la carrera clasica de la
--  historia: los cinco enanos, juntos, contra un camello solo.
--
--  Uno de los enanos esta INJURED. No es un descuido: es el caso que hace visible
--  la regla "solo los competidores ACTIVE pueden inscribirse", y el que le da algo
--  que mostrar al filtro por estado del listado.
-- -----------------------------------------------------------------------------
INSERT INTO competitors (id, name, nickname, type, date_of_birth, weight_kg, height_cm,
                         country_of_origin, status, registered_at, team_id,
                         victories, defeats, completed_races) VALUES

    -- Los cinco enanos de Barbas de Hierro
    ('c0000000-0000-4000-a000-000000000001', 'Thorbal Piedrafirme', 'martillo',
     'DWARF', DATE '1968-03-12', 82.40, 134.00, 'Colombia', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '88 days', 'a0000000-0000-4000-a000-000000000001', 0, 1, 1),

    ('c0000000-0000-4000-a000-000000000002', 'Grimna Yunque', 'yunque',
     'DWARF', DATE '1972-11-30', 79.10, 129.50, 'Colombia', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '88 days', 'a0000000-0000-4000-a000-000000000001', 0, 1, 1),

    ('c0000000-0000-4000-a000-000000000003', 'Durik Barbanegra', 'barbanegra',
     'DWARF', DATE '1965-07-04', 88.00, 137.20, 'Peru', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '86 days', 'a0000000-0000-4000-a000-000000000001', 0, 1, 0),

    ('c0000000-0000-4000-a000-000000000004', 'Nala Cascoduro', 'cascoduro',
     'DWARF', DATE '1979-01-22', 74.60, 126.80, 'Chile', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '86 days', 'a0000000-0000-4000-a000-000000000001', 0, 1, 0),

    -- El lesionado: sigue siendo del equipo, pero no puede inscribirse a nada.
    ('c0000000-0000-4000-a000-000000000005', 'Borin Pieveloz', 'pieveloz',
     'DWARF', DATE '1983-09-15', 71.20, 124.00, 'Colombia', 'INJURED',
     LOCALTIMESTAMP - INTERVAL '80 days', 'a0000000-0000-4000-a000-000000000001', 0, 0, 0),

    -- Los dos camellos, individuales, sin equipo
    ('c0000000-0000-4000-a000-000000000006', 'Zafira del Oasis', 'zafira',
     'CAMEL', DATE '2017-05-08', 612.00, 218.50, 'Marruecos', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '84 days', NULL, 1, 0, 1),

    ('c0000000-0000-4000-a000-000000000007', 'Ramses de Dunas Largas', 'ramses',
     'CAMEL', DATE '2019-02-19', 588.75, 211.00, 'Egipto', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '84 days', NULL, 0, 1, 1),

    -- Los dos medianos, en el segundo equipo
    ('c0000000-0000-4000-a000-000000000008', 'Perrin Trotalomas', 'trotalomas',
     'MEDIUM', DATE '1994-06-27', 96.30, 168.40, 'Colombia', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '73 days', 'a0000000-0000-4000-a000-000000000002', 0, 1, 1),

    ('c0000000-0000-4000-a000-000000000009', 'Sela Cascabel', 'cascabel',
     'MEDIUM', DATE '1998-12-03', 91.80, 164.90, 'Ecuador', 'ACTIVE',
     LOCALTIMESTAMP - INTERVAL '73 days', 'a0000000-0000-4000-a000-000000000002', 0, 0, 0)
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
--  CARRERAS
-- -----------------------------------------------------------------------------
--  El enunciado pide tres carreras en estados distintos. Van siete, una por cada
--  uno de los seis estados posibles y dos COMPLETED, y el motivo es practico: la
--  pantalla de listado tiene un filtro por estado, y con tres carreras la mitad de
--  las opciones del filtro devolveria una lista vacia. Con estas siete, cualquier
--  combinacion que pruebe el evaluador muestra algo.
--
--  Las dos COMPLETED tampoco son un capricho: una es individual y la otra por
--  equipos, y hacen falta las dos para que las DOS tablas de posiciones
--  (/api/standings/competitors y /api/standings/teams) tengan contenido.
-- -----------------------------------------------------------------------------
INSERT INTO races (id, name, description, scheduled_at, start_location, finish_location,
                   distance_meters, max_participants, type, status, organizer,
                   registration_deadline, created_at, updated_at) VALUES

    -- 1) COMPLETED, individual: la carrera con resultados completos.
    ('e0000000-0000-4000-a000-000000000001',
     'I Gran Premio EIA de Velocidad',
     'La carrera fundacional de la liga. Individual y sin equipos: cada uno corre por su cuenta, que es la unica manera de comparar de verdad a un camello con un enano.',
     LOCALTIMESTAMP - INTERVAL '45 days',
     'Portico de la Universidad EIA', 'Alto de las Palmas',
     1000, 10, 'INDIVIDUAL', 'COMPLETED', 'organizer',
     LOCALTIMESTAMP - INTERVAL '50 days',
     LOCALTIMESTAMP - INTERVAL '70 days', LOCALTIMESTAMP - INTERVAL '45 days'),

    -- 2) COMPLETED, por equipos: la que le da contenido a la tabla de equipos.
    ('e0000000-0000-4000-a000-000000000002',
     'Copa de Equipos de la Cantera',
     'Primera edicion por equipos. El tiempo que cuenta es el del ultimo integrante en cruzar la meta, asi que no gana el mas rapido sino el que mejor se espera.',
     LOCALTIMESTAMP - INTERVAL '20 days',
     'Cantera vieja', 'Mirador del rio',
     2500, 6, 'TEAM', 'COMPLETED', 'organizer',
     LOCALTIMESTAMP - INTERVAL '25 days',
     LOCALTIMESTAMP - INTERVAL '55 days', LOCALTIMESTAMP - INTERVAL '20 days'),

    -- 3) IN_PROGRESS: largo hace dos horas y todavia no tiene ni un resultado
    --    cargado. Es el estado que deja probar POST /api/races/{id}/results sin
    --    tener que preparar nada antes, porque los resultados solo se admiten
    --    mientras la carrera esta en curso.
    ('e0000000-0000-4000-a000-000000000003',
     'Clasica del Valle de la Herradura',
     'Mixta: compiten equipos completos y competidores sueltos en la misma grilla. Es la carrera que mas discusiones genera y la que mas publico lleva.',
     LOCALTIMESTAMP - INTERVAL '2 hours',
     'Puente de la herradura', 'Plaza del mercado',
     1800, 8, 'MIXED', 'IN_PROGRESS', 'organizer',
     LOCALTIMESTAMP - INTERVAL '3 days',
     LOCALTIMESTAMP - INTERVAL '40 days', LOCALTIMESTAMP - INTERVAL '2 hours'),

    -- 4) OPEN_FOR_REGISTRATION: la unica que acepta inscripciones nuevas. Tiene
    --    pendientes, aprobadas y una rechazada, para que el circuito de aprobar y
    --    rechazar se pueda probar de entrada.
    ('e0000000-0000-4000-a000-000000000004',
     'Copa de Primavera EIA',
     'Carrera individual de media distancia, abierta a inscripcion. La organizacion revisa cada solicitud antes de asignar carril.',
     LOCALTIMESTAMP + INTERVAL '30 days',
     'Portico de la Universidad EIA', 'Laguna del sur',
     1500, 12, 'INDIVIDUAL', 'OPEN_FOR_REGISTRATION', 'organizer',
     LOCALTIMESTAMP + INTERVAL '25 days',
     LOCALTIMESTAMP - INTERVAL '10 days', LOCALTIMESTAMP - INTERVAL '10 days'),

    -- 5) CLOSED_FOR_REGISTRATION: la fecha limite ya paso, la grilla esta armada y
    --    la carrera todavia no largo. Es el estado previo a IN_PROGRESS.
    ('e0000000-0000-4000-a000-000000000005',
     'Desafio de las Dunas Largas',
     'La carrera larga del calendario, sobre terreno suelto. Aca el camello corre con ventaja y el equipo de enanos apuesta a que la distancia se le haga eterna.',
     LOCALTIMESTAMP + INTERVAL '10 days',
     'Entrada del arenal', 'Duna del vigia',
     4200, 8, 'MIXED', 'CLOSED_FOR_REGISTRATION', 'organizer',
     LOCALTIMESTAMP - INTERVAL '1 day',
     LOCALTIMESTAMP - INTERVAL '35 days', LOCALTIMESTAMP - INTERVAL '1 day'),

    -- 6) CANCELLED, con sus inscripciones tambien canceladas. Sirve para ver que
    --    una carrera cancelada conserva su historia en vez de desaparecer.
    ('e0000000-0000-4000-a000-000000000006',
     'Nocturna de la Cantera',
     'Se cancelo por falta de iluminacion en el tramo final. Queda en el sistema para dejar constancia de que existio y de por que no se corrio.',
     LOCALTIMESTAMP + INTERVAL '18 days',
     'Cantera vieja', 'Cantera vieja',
     1200, 6, 'INDIVIDUAL', 'CANCELLED', 'organizer',
     LOCALTIMESTAMP + INTERVAL '12 days',
     LOCALTIMESTAMP - INTERVAL '30 days', LOCALTIMESTAMP - INTERVAL '5 days'),

    -- 7) DRAFT: todavia se esta escribiendo y por eso no tiene ni una inscripcion.
    --    Una carrera en borrador no esta publicada, asi que nadie pudo anotarse.
    ('e0000000-0000-4000-a000-000000000007',
     'II Gran Premio EIA de Velocidad',
     'Borrador de la segunda edicion. Falta cerrar el recorrido y confirmar el cupo antes de abrir la inscripcion.',
     LOCALTIMESTAMP + INTERVAL '90 days',
     'Portico de la Universidad EIA', 'Alto de las Palmas',
     1000, 10, 'INDIVIDUAL', 'DRAFT', 'organizer',
     LOCALTIMESTAMP + INTERVAL '80 days',
     LOCALTIMESTAMP - INTERVAL '2 days', LOCALTIMESTAMP - INTERVAL '2 days')
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
--  INSCRIPCIONES
-- -----------------------------------------------------------------------------
--  Cada inscripcion es de un competidor O de un equipo, nunca de los dos: lo
--  garantiza la constraint ck_registrations_un_participante.
--
--  UNA COMBINACION QUE PARECE POSIBLE Y NO LO ES
--  En las carreras donde corre "Barbas de Hierro" como equipo NO aparece ningun
--  enano inscripto por su cuenta, y no es porque falte: la aplicacion lo rechaza
--  con un 409. Un enano que corre suelto y ademas dentro de su equipo estaria
--  compitiendo dos veces en la misma carrera. Por eso los enanos individuales solo
--  figuran en las carreras INDIVIDUAL, donde los equipos directamente no entran.
--
--  El carril va en null en todo lo que no esta aprobado. Es la diferencia entre
--  "todavia no tiene lugar en la grilla" y "tiene el lugar numero tal", y Postgres
--  admite varios null dentro de la constraint unica (race_id, lane) justamente para
--  que eso se pueda representar.
-- -----------------------------------------------------------------------------
INSERT INTO race_registrations (id, race_id, competitor_id, team_id, status, lane,
                                registered_at, registered_by, validation_notes) VALUES

    -- Carrera 1 (COMPLETED, individual): ocho corredores sueltos
    ('d0000000-0000-4000-a000-000000000101', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000006', NULL, 'APPROVED', 1,
     LOCALTIMESTAMP - INTERVAL '60 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000102', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000007', NULL, 'APPROVED', 2,
     LOCALTIMESTAMP - INTERVAL '60 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000103', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000001', NULL, 'APPROVED', 3,
     LOCALTIMESTAMP - INTERVAL '59 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000104', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000002', NULL, 'APPROVED', 4,
     LOCALTIMESTAMP - INTERVAL '59 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000105', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000008', NULL, 'APPROVED', 5,
     LOCALTIMESTAMP - INTERVAL '58 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000106', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000003', NULL, 'APPROVED', 6,
     LOCALTIMESTAMP - INTERVAL '58 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000107', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000004', NULL, 'APPROVED', 7,
     LOCALTIMESTAMP - INTERVAL '57 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000108', 'e0000000-0000-4000-a000-000000000001',
     'c0000000-0000-4000-a000-000000000009', NULL, 'APPROVED', 8,
     LOCALTIMESTAMP - INTERVAL '57 days', 'organizer', NULL),

    -- Carrera 2 (COMPLETED, por equipos): los dos equipos
    ('d0000000-0000-4000-a000-000000000201', 'e0000000-0000-4000-a000-000000000002',
     NULL, 'a0000000-0000-4000-a000-000000000001', 'APPROVED', 1,
     LOCALTIMESTAMP - INTERVAL '40 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000202', 'e0000000-0000-4000-a000-000000000002',
     NULL, 'a0000000-0000-4000-a000-000000000002', 'APPROVED', 2,
     LOCALTIMESTAMP - INTERVAL '39 days', 'organizer', NULL),

    -- Carrera 3 (IN_PROGRESS, mixta): dos equipos y los dos camellos, todos
    -- aprobados y con carril. Ningun enano suelto, por lo explicado arriba.
    ('d0000000-0000-4000-a000-000000000301', 'e0000000-0000-4000-a000-000000000003',
     NULL, 'a0000000-0000-4000-a000-000000000001', 'APPROVED', 1,
     LOCALTIMESTAMP - INTERVAL '20 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000302', 'e0000000-0000-4000-a000-000000000003',
     NULL, 'a0000000-0000-4000-a000-000000000002', 'APPROVED', 2,
     LOCALTIMESTAMP - INTERVAL '20 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000303', 'e0000000-0000-4000-a000-000000000003',
     'c0000000-0000-4000-a000-000000000006', NULL, 'APPROVED', 3,
     LOCALTIMESTAMP - INTERVAL '19 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000304', 'e0000000-0000-4000-a000-000000000003',
     'c0000000-0000-4000-a000-000000000007', NULL, 'APPROVED', 4,
     LOCALTIMESTAMP - INTERVAL '19 days', 'organizer', NULL),

    -- Carrera 4 (OPEN): dos aprobadas con carril, dos pendientes sin carril y una
    -- rechazada con el motivo escrito. Es el estado interesante para la pantalla de
    -- inscripciones.
    ('d0000000-0000-4000-a000-000000000401', 'e0000000-0000-4000-a000-000000000004',
     'c0000000-0000-4000-a000-000000000006', NULL, 'APPROVED', 1,
     LOCALTIMESTAMP - INTERVAL '8 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000402', 'e0000000-0000-4000-a000-000000000004',
     'c0000000-0000-4000-a000-000000000001', NULL, 'APPROVED', 2,
     LOCALTIMESTAMP - INTERVAL '7 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000403', 'e0000000-0000-4000-a000-000000000004',
     'c0000000-0000-4000-a000-000000000002', NULL, 'PENDING', NULL,
     LOCALTIMESTAMP - INTERVAL '5 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000404', 'e0000000-0000-4000-a000-000000000004',
     'c0000000-0000-4000-a000-000000000008', NULL, 'PENDING', NULL,
     LOCALTIMESTAMP - INTERVAL '4 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000405', 'e0000000-0000-4000-a000-000000000004',
     'c0000000-0000-4000-a000-000000000003', NULL, 'REJECTED', NULL,
     LOCALTIMESTAMP - INTERVAL '6 days', 'organizer',
     'Se rechaza por no presentar el certificado veterinario al dia'),

    -- Carrera 5 (CLOSED): la grilla ya esta cerrada, los cuatro con carril
    ('d0000000-0000-4000-a000-000000000501', 'e0000000-0000-4000-a000-000000000005',
     NULL, 'a0000000-0000-4000-a000-000000000001', 'APPROVED', 1,
     LOCALTIMESTAMP - INTERVAL '25 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000502', 'e0000000-0000-4000-a000-000000000005',
     'c0000000-0000-4000-a000-000000000007', NULL, 'APPROVED', 2,
     LOCALTIMESTAMP - INTERVAL '24 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000503', 'e0000000-0000-4000-a000-000000000005',
     NULL, 'a0000000-0000-4000-a000-000000000002', 'APPROVED', 3,
     LOCALTIMESTAMP - INTERVAL '23 days', 'organizer', NULL),
    ('d0000000-0000-4000-a000-000000000504', 'e0000000-0000-4000-a000-000000000005',
     'c0000000-0000-4000-a000-000000000006', NULL, 'APPROVED', 4,
     LOCALTIMESTAMP - INTERVAL '22 days', 'organizer', NULL),

    -- Carrera 6 (CANCELLED): al cancelarse la carrera, las inscripciones quedaron
    -- canceladas y sin carril. La historia se conserva, la grilla no.
    ('d0000000-0000-4000-a000-000000000601', 'e0000000-0000-4000-a000-000000000006',
     'c0000000-0000-4000-a000-000000000006', NULL, 'CANCELLED', NULL,
     LOCALTIMESTAMP - INTERVAL '28 days', 'organizer',
     'Cancelada junto con la carrera'),
    ('d0000000-0000-4000-a000-000000000602', 'e0000000-0000-4000-a000-000000000006',
     'c0000000-0000-4000-a000-000000000007', NULL, 'CANCELLED', NULL,
     LOCALTIMESTAMP - INTERVAL '28 days', 'organizer',
     'Cancelada junto con la carrera')
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
--  RESULTADOS
-- -----------------------------------------------------------------------------
--  "At least one completed race with results", ultimo punto del minimo exigido.
--
--  El juego de datos de la carrera 1 cubre los CUATRO estados posibles de un
--  resultado y los CINCO puestos que puntuan, y eso es deliberado: con menos, la
--  tabla de posiciones mostraria siempre el mismo numero y no se veria que la
--  escala 10-7-5-3-1 esta funcionando.
--
--  LOS TIEMPOS NO SON AL AZAR
--  Tienen que crecer con el puesto, contando la penalizacion, porque la aplicacion
--  rechaza con un 409 un resultado donde el cuarto haya sido mas rapido que el
--  tercero. Grimna, cuarta, hizo 742 segundos y arrastra 30 de penalizacion: 772 en
--  total, todavia por debajo de los 806 del quinto. Cargar estos mismos datos por
--  la API daria exactamente el mismo resultado.
--
--  Los que no llegaron a la meta van sin puesto y sin tiempo, y eso no es un dato
--  faltante sino la unica forma valida de escribirlo: la constraint
--  ck_results_coherencia_estado rechaza cualquier otra combinacion.
-- -----------------------------------------------------------------------------
INSERT INTO race_results (id, race_id, registration_id, starting_position, final_position,
                          completion_time_seconds, penalty_time_seconds, status, notes,
                          recorded_by, recorded_at, updated_at) VALUES

    -- Carrera 1: el podio
    ('f0000000-0000-4000-a000-000000000101', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000101', 1, 1, 498, 0, 'FINISHED',
     'Tomo la punta en los primeros doscientos metros y no la solto',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),
    ('f0000000-0000-4000-a000-000000000102', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000102', 2, 2, 521, 0, 'FINISHED',
     NULL,
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),
    ('f0000000-0000-4000-a000-000000000103', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000103', 3, 3, 705, 0, 'FINISHED',
     'Mejor tiempo de la categoria DWARF en la historia de la liga',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),

    -- Cuarto puesto con penalizacion: 742 + 30 = 772 segundos reales
    ('f0000000-0000-4000-a000-000000000104', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000104', 4, 4, 742, 30, 'FINISHED',
     'Treinta segundos de penalizacion por largada adelantada',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),
    ('f0000000-0000-4000-a000-000000000105', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000105', 5, 5, 806, 0, 'FINISHED',
     NULL,
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),

    -- Los tres que no puntuan, cada uno por un motivo distinto
    ('f0000000-0000-4000-a000-000000000106', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000106', 6, NULL, NULL, 0, 'DID_NOT_FINISH',
     'Abandono a la altura del kilometro 800 por un tobillo',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),
    ('f0000000-0000-4000-a000-000000000107', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000107', 7, NULL, NULL, 0, 'DISQUALIFIED',
     'Descalificado por invadir el carril vecino en la curva final',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),
    ('f0000000-0000-4000-a000-000000000108', 'e0000000-0000-4000-a000-000000000001',
     'd0000000-0000-4000-a000-000000000108', 8, NULL, NULL, 0, 'DID_NOT_START',
     'No se presento a la largada',
     'organizer', LOCALTIMESTAMP - INTERVAL '45 days', LOCALTIMESTAMP - INTERVAL '45 days'),

    -- Carrera 2: los dos equipos
    ('f0000000-0000-4000-a000-000000000201', 'e0000000-0000-4000-a000-000000000002',
     'd0000000-0000-4000-a000-000000000201', 1, 1, 1240, 0, 'FINISHED',
     'Los cinco cruzaron la meta en formacion, con doce segundos entre el primero y el ultimo',
     'organizer', LOCALTIMESTAMP - INTERVAL '20 days', LOCALTIMESTAMP - INTERVAL '20 days'),
    ('f0000000-0000-4000-a000-000000000202', 'e0000000-0000-4000-a000-000000000002',
     'd0000000-0000-4000-a000-000000000202', 2, 2, 1395, 0, 'FINISHED',
     NULL,
     'organizer', LOCALTIMESTAMP - INTERVAL '20 days', LOCALTIMESTAMP - INTERVAL '20 days')
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
--  BITACORA
-- -----------------------------------------------------------------------------
--  Estas lineas no las exige el enunciado, y van igual por un motivo concreto: sin
--  ellas la pantalla de auditoria arranca vacia, y una pantalla vacia no permite
--  distinguir "no paso nada todavia" de "esto no funciona". Con estas once, el
--  filtro por usuario, por accion, por entidad y por rango de fechas se puede
--  probar apenas levanta el proyecto.
--
--  No estan todas las que corresponderian a los datos de arriba: seria una linea
--  por cada competidor, cada carrera, cada inscripcion y cada resultado, unas
--  cincuenta en total, y la bitacora dejaria de leerse. Van las representativas de
--  cada tipo de accion.
-- -----------------------------------------------------------------------------
INSERT INTO audit_logs (id, username, action, entity_type, entity_id, occurred_at,
                        description, previous_value, new_value) VALUES

    ('b0000000-0000-4000-a000-000000000001', 'admin', 'LOGIN', 'User', NULL,
     LOCALTIMESTAMP - INTERVAL '95 days',
     'El usuario ''admin'' empezo a usar el sistema', NULL, NULL),

    ('b0000000-0000-4000-a000-000000000002', 'organizer', 'TEAM_CREATED', 'Team',
     'a0000000-0000-4000-a000-000000000001', LOCALTIMESTAMP - INTERVAL '90 days',
     'Se creo el equipo ''Barbas de Hierro''', NULL,
     'name=Barbas de Hierro, coach=Kildra Mano de Piedra, status=ACTIVE'),

    ('b0000000-0000-4000-a000-000000000003', 'organizer', 'TEAM_CREATED', 'Team',
     'a0000000-0000-4000-a000-000000000002', LOCALTIMESTAMP - INTERVAL '75 days',
     'Se creo el equipo ''Los Medianos de la Loma''', NULL,
     'name=Los Medianos de la Loma, coach=Perla Vientoclaro, status=ACTIVE'),

    ('b0000000-0000-4000-a000-000000000004', 'organizer', 'COMPETITOR_CREATED', 'Competitor',
     'c0000000-0000-4000-a000-000000000006', LOCALTIMESTAMP - INTERVAL '84 days',
     'Se registro al competidor ''Zafira del Oasis'' (zafira)', NULL,
     'type=CAMEL, status=ACTIVE, team=null'),

    ('b0000000-0000-4000-a000-000000000005', 'organizer', 'TEAM_MEMBER_ADDED', 'Team',
     'a0000000-0000-4000-a000-000000000001', LOCALTIMESTAMP - INTERVAL '80 days',
     'Se agrego a ''Borin Pieveloz'' al equipo ''Barbas de Hierro''', NULL,
     'competitor=pieveloz'),

    -- Un cambio de estado, con el antes y el despues. Es el caso que muestra para
    -- que sirven las columnas previous_value y new_value.
    ('b0000000-0000-4000-a000-000000000006', 'organizer', 'COMPETITOR_STATUS_CHANGED', 'Competitor',
     'c0000000-0000-4000-a000-000000000005', LOCALTIMESTAMP - INTERVAL '52 days',
     'Se cambio el estado de ''Borin Pieveloz'' por una lesion en el entrenamiento',
     'ACTIVE', 'INJURED'),

    ('b0000000-0000-4000-a000-000000000007', 'organizer', 'RACE_CREATED', 'Race',
     'e0000000-0000-4000-a000-000000000001', LOCALTIMESTAMP - INTERVAL '70 days',
     'Se creo la carrera ''I Gran Premio EIA de Velocidad''', NULL,
     'type=INDIVIDUAL, status=DRAFT, distance=1000m, cupo=10'),

    ('b0000000-0000-4000-a000-000000000008', 'organizer', 'REGISTRATION_APPROVED', 'RaceRegistration',
     'd0000000-0000-4000-a000-000000000101', LOCALTIMESTAMP - INTERVAL '59 days',
     'Se aprobo la inscripcion de ''Zafira del Oasis'' en ''I Gran Premio EIA de Velocidad''',
     'PENDING', 'APPROVED, carril 1'),

    ('b0000000-0000-4000-a000-000000000009', 'organizer', 'RESULT_RECORDED', 'RaceResult',
     'f0000000-0000-4000-a000-000000000101', LOCALTIMESTAMP - INTERVAL '45 days',
     'Se cargo el resultado de ''Zafira del Oasis'' en la carrera ''I Gran Premio EIA de Velocidad''',
     NULL, 'FINISHED, puesto 1, 498s (+0s de penalizacion)'),

    ('b0000000-0000-4000-a000-00000000000a', 'organizer', 'RACE_STATUS_CHANGED', 'Race',
     'e0000000-0000-4000-a000-000000000001', LOCALTIMESTAMP - INTERVAL '45 days',
     'Se dio por terminada la carrera ''I Gran Premio EIA de Velocidad''',
     'IN_PROGRESS', 'COMPLETED'),

    ('b0000000-0000-4000-a000-00000000000b', 'organizer', 'RACE_CANCELLED', 'Race',
     'e0000000-0000-4000-a000-000000000006', LOCALTIMESTAMP - INTERVAL '5 days',
     'Se cancelo la carrera ''Nocturna de la Cantera'' por falta de iluminacion en el tramo final',
     'OPEN_FOR_REGISTRATION', 'CANCELLED')
ON CONFLICT DO NOTHING;
