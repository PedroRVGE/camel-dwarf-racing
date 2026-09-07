package com.eia.camelracing.support;

import com.eia.camelracing.competitor.dto.CompetitorRequest;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.competitor.entity.CompetitorType;
import com.eia.camelracing.race.dto.RaceRequest;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.race.entity.RaceStatus;
import com.eia.camelracing.race.entity.RaceType;
import com.eia.camelracing.registration.entity.RaceRegistration;
import com.eia.camelracing.registration.entity.RegistrationStatus;
import com.eia.camelracing.result.dto.ResultRequest;
import com.eia.camelracing.result.entity.RaceResult;
import com.eia.camelracing.result.entity.ResultStatus;
import com.eia.camelracing.team.entity.Team;
import com.eia.camelracing.team.entity.TeamStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Las fabricas de datos de prueba.
 *
 * POR QUE EXISTE ESTA CLASE
 * Un competidor valido necesita nombre, apodo, categoria, fecha de nacimiento,
 * peso, altura, origen y estado. Escribir esos ocho campos en cada test hace dos
 * cosas malas: alarga los tests hasta que no se lee que estan probando, y
 * convierte cada campo nuevo de la entidad en veinte archivos que tocar.
 *
 * Aca cada metodo devuelve un objeto VALIDO por defecto, y el test cambia solo lo
 * que le importa. Asi un test se lee como la regla que prueba:
 *
 *     Competitor suspendido = Datos.competidor("cascoduro", CompetitorStatus.SUSPENDED);
 *
 * y no como una pagina de setters entre los que hay que buscar cual es el que
 * importa.
 *
 * TODOS LOS OBJETOS VIENEN CON ID
 * Los tests unitarios lo necesitan: sin id no se puede decirle a un mock "cuando
 * te pregunten por ESTE competidor, devolve este". En las pruebas de integracion
 * el id que se asigna aca se pierde, porque Hibernate genera el suyo al persistir,
 * y eso no molesta: lo que se usa despues es el id de la entidad ya guardada.
 */
public final class Datos {

    private Datos() {
    }

    // ==================================================================
    // PARA GUARDAR EN LA BASE
    // ==================================================================

    /**
     * La misma entidad, pero con el id en null.
     *
     * POR QUE HACE FALTA
     * Las fabricas de abajo asignan un id, porque los tests unitarios lo necesitan
     * para decirle a un mock "cuando te pregunten por ESTE, devolve este otro". Pero
     * una entidad con id ya puesto, para Hibernate, es una entidad DESPRENDIDA: cree
     * que ya existe en la base y que alguien la trajo de otra sesion. Al intentar
     * persistirla corta con "detached entity passed to persist", que es un mensaje
     * bastante desorientador cuando lo que uno cree estar haciendo es crear una fila
     * nueva.
     *
     * Los ids los genera Hibernate al insertar (@GeneratedValue), asi que en las
     * pruebas de integracion hay que sacarlos antes de guardar. Se usa asi, con
     * import estatico, y se lee como lo que es:
     *
     *     em.persistAndFlush(sinId(competidor("martillo")));
     *
     * Hay una sobrecarga por tipo en vez de un metodo generico con reflexion: son
     * cinco lineas, no se equivocan nunca, y el compilador avisa si aparece una
     * entidad nueva que todavia no esta contemplada.
     */
    public static Competitor sinId(Competitor competidor) {
        competidor.setId(null);
        return competidor;
    }

    public static Team sinId(Team equipo) {
        equipo.setId(null);
        return equipo;
    }

    public static Race sinId(Race carrera) {
        carrera.setId(null);
        return carrera;
    }

    public static RaceRegistration sinId(RaceRegistration inscripcion) {
        inscripcion.setId(null);
        return inscripcion;
    }

    public static RaceResult sinId(RaceResult resultado) {
        resultado.setId(null);
        return resultado;
    }

    // ==================================================================
    // ENTIDADES
    // ==================================================================

    /** Un equipo activo, sin integrantes. */
    public static Team equipo(String nombre) {
        return Team.builder()
                .id(UUID.randomUUID())
                .name(nombre)
                .description("Equipo de prueba")
                .coach("Entrenadora de prueba")
                .status(TeamStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();
    }

    /** Un enano ACTIVE, sin equipo. El caso normal. */
    public static Competitor competidor(String apodo) {
        return competidor(apodo, CompetitorStatus.ACTIVE);
    }

    /** El mismo competidor, pero en el estado que se le pida. */
    public static Competitor competidor(String apodo, CompetitorStatus estado) {
        return Competitor.builder()
                .id(UUID.randomUUID())
                .name("Competidor " + apodo)
                .nickname(apodo)
                .type(CompetitorType.DWARF)
                .dateOfBirth(LocalDate.of(1975, 4, 12))
                .weightKg(new BigDecimal("80.00"))
                .heightCm(new BigDecimal("130.00"))
                .countryOfOrigin("Colombia")
                .status(estado)
                .registeredAt(LocalDateTime.now().minusDays(20))
                .build();
    }

    /**
     * Una carrera mixta en el estado que se pida.
     *
     * La largada va SIEMPRE 30 dias adelante y el cierre de inscripciones 25, aun
     * para una carrera COMPLETED, que en la realidad seria una fecha pasada. Es a
     * proposito: casi ninguna regla mira la fecha, y las dos que si la miran
     * (inscribirse fuera de plazo y programar en el pasado) se prueban con carreras
     * armadas a medida, donde la fecha esta a la vista del que lee el test. Poner
     * fechas pasadas por defecto haria que esas dos reglas se activaran de rebote
     * en tests que estan probando otra cosa.
     */
    public static Race carrera(RaceStatus estado) {
        LocalDateTime largada = LocalDateTime.now().plusDays(30);
        return Race.builder()
                .id(UUID.randomUUID())
                .name("Carrera de prueba")
                .description("Una carrera para probar")
                .scheduledAt(largada)
                .startLocation("Portico de la EIA")
                .finishLocation("Alto de Las Palmas")
                .distanceMeters(1000)
                .maxParticipants(8)
                .type(RaceType.MIXED)
                .status(estado)
                .organizer("organizer")
                .registrationDeadline(largada.minusDays(5))
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build();
    }

    /** Una inscripcion individual, en el estado que se pida y sin carril. */
    public static RaceRegistration inscripcion(Race carrera, Competitor competidor,
                                               RegistrationStatus estado) {
        return RaceRegistration.builder()
                .id(UUID.randomUUID())
                .race(carrera)
                .competitor(competidor)
                .status(estado)
                .registeredAt(LocalDateTime.now().minusDays(5))
                .registeredBy("organizer")
                .build();
    }

    /** Una inscripcion de equipo, en el estado que se pida. */
    public static RaceRegistration inscripcionDeEquipo(Race carrera, Team equipo,
                                                       RegistrationStatus estado) {
        return RaceRegistration.builder()
                .id(UUID.randomUUID())
                .race(carrera)
                .team(equipo)
                .status(estado)
                .registeredAt(LocalDateTime.now().minusDays(5))
                .registeredBy("organizer")
                .build();
    }

    /** Un resultado de los que puntuan: llego a la meta, con puesto y tiempo. */
    public static RaceResult resultado(RaceRegistration inscripcion, int puesto, int segundos) {
        return RaceResult.builder()
                .id(UUID.randomUUID())
                .race(inscripcion.getRace())
                .registration(inscripcion)
                .finalPosition(puesto)
                .completionTimeSeconds(segundos)
                .penaltyTimeSeconds(0)
                .status(ResultStatus.FINISHED)
                .recordedBy("organizer")
                .recordedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** Un resultado de los que no puntuan: sin puesto y sin tiempo, como exige la regla. */
    public static RaceResult resultadoSinLlegar(RaceRegistration inscripcion, ResultStatus estado) {
        return RaceResult.builder()
                .id(UUID.randomUUID())
                .race(inscripcion.getRace())
                .registration(inscripcion)
                .penaltyTimeSeconds(0)
                .status(estado)
                .recordedBy("organizer")
                .recordedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================================================================
    // DTOs DE ENTRADA
    // ==================================================================

    /** Un alta de competidor valida, sin equipo. */
    public static CompetitorRequest pedidoDeCompetidor(String apodo) {
        return new CompetitorRequest(
                "Competidor " + apodo,
                apodo,
                CompetitorType.DWARF,
                LocalDate.of(1975, 4, 12),
                new BigDecimal("80.00"),
                new BigDecimal("130.00"),
                "Colombia",
                null);
    }

    /** El mismo alta, pero con el peso que se le pase. Sirve para probar el peso invalido. */
    public static CompetitorRequest pedidoDeCompetidorConPeso(String apodo, String peso) {
        return new CompetitorRequest(
                "Competidor " + apodo,
                apodo,
                CompetitorType.DWARF,
                LocalDate.of(1975, 4, 12),
                new BigDecimal(peso),
                new BigDecimal("130.00"),
                "Colombia",
                null);
    }

    /** Una carrera valida: largada dentro de 30 dias, cierre 5 dias antes. */
    public static RaceRequest pedidoDeCarrera(String nombre) {
        LocalDateTime largada = LocalDateTime.now().plusDays(30);
        return pedidoDeCarrera(nombre, largada, largada.minusDays(5));
    }

    /** La misma carrera, con las dos fechas a eleccion. Sirve para probar las reglas de fecha. */
    public static RaceRequest pedidoDeCarrera(String nombre, LocalDateTime largada,
                                              LocalDateTime cierre) {
        return new RaceRequest(
                nombre,
                "Una carrera para probar",
                largada,
                "Portico de la EIA",
                "Alto de Las Palmas",
                1000,
                8,
                RaceType.MIXED,
                cierre);
    }

    /** Un resultado de llegada: FINISHED con puesto y tiempo, que es la unica forma valida. */
    public static ResultRequest pedidoDeResultado(UUID inscripcionId, int puesto, int segundos) {
        return new ResultRequest(inscripcionId, ResultStatus.FINISHED, segundos, 0, puesto, null);
    }
}
