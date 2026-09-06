package com.eia.camelracing.registration.entity;

import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * La inscripcion de un participante en una carrera.
 *
 * ES UNA ENTIDAD PROPIA Y NO UNA SIMPLE RELACION N A N
 * Podria parecer que alcanza con una tabla de pares (carrera, competidor). No
 * alcanza, y por el mismo motivo por el que existe cualquier entidad de union con
 * datos propios: la inscripcion TIENE datos suyos que no pertenecen ni a la
 * carrera ni al participante. Cuando se pidio, en que estado esta, quien la
 * aprobo, en que carril larga, por que se rechazo. Nada de eso entra en una tabla
 * de pares.
 *
 * EL PARTICIPANTE ES UNO DE DOS, Y EXACTAMENTE UNO
 * Una inscripcion es de un competidor individual O de un equipo, nunca de los dos
 * ni de ninguno. Las dos columnas son opcionales por separado y la regla se
 * garantiza en dos lugares: RegistrationService la verifica para dar un mensaje
 * claro, y la migracion tiene un CHECK que la hace cumplir aunque alguien escriba
 * directo en la base.
 *
 * La alternativa "elegante" seria una jerarquia de herencia con dos subclases.
 * Para dos casos y una sola diferencia, agrega tablas o columnas discriminadoras y
 * complica todas las consultas, sin ganar nada.
 */
@Entity
@Table(
        name = "race_registrations",
        uniqueConstraints = {
                // "Starting positions cannot be duplicated": dos participantes no pueden
                // largar en el mismo carril de la misma carrera. Postgres permite varios
                // NULL en una constraint unica, asi que las inscripciones sin carril
                // asignado todavia no chocan entre si, que es justo lo que se necesita.
                @UniqueConstraint(name = "uk_registrations_race_lane",
                        columnNames = {"race_id", "lane"})
        },
        indexes = {
                @Index(name = "idx_registrations_race", columnList = "race_id"),
                @Index(name = "idx_registrations_competitor", columnList = "competitor_id"),
                @Index(name = "idx_registrations_team", columnList = "team_id"),
                @Index(name = "idx_registrations_status", columnList = "status")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RaceRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * La carrera. Lado dueno de la relacion: aca esta la columna race_id.
     *
     * optional = false y LAZY explicito. El default de @ManyToOne es EAGER, que
     * traeria la carrera entera en toda consulta de inscripciones aunque no se use.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_registrations_race"))
    @ToString.Exclude
    private Race race;

    /** El competidor, si es una inscripcion individual. Null si es de un equipo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitor_id",
            foreignKey = @ForeignKey(name = "fk_registrations_competitor"))
    @ToString.Exclude
    private Competitor competitor;

    /** El equipo, si es una inscripcion de equipo. Null si es individual. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id",
            foreignKey = @ForeignKey(name = "fk_registrations_team"))
    @ToString.Exclude
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    /**
     * El carril o posicion de largada.
     *
     * Integer y no int: tiene que poder ser null. Una inscripcion recien pedida
     * todavia no tiene carril asignado, y con un int primitivo ese "todavia no"
     * seria un 0, que es indistinguible de un carril numero 0 legitimo.
     */
    @Column
    private Integer lane;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /**
     * Quien hizo la inscripcion. Sale del token, no del cuerpo del request.
     *
     * Mismo criterio que Race.organizer: es un String porque los usuarios viven en
     * Keycloak y no en esta base.
     */
    @Column(name = "registered_by", nullable = false, length = 100)
    private String registeredBy;

    /**
     * Las notas de validacion que pide el enunciado, y el motivo del rechazo.
     *
     * Es un solo campo para las dos cosas porque son la misma: el texto con el que
     * la organizacion explica su decision sobre esta inscripcion. Al rechazar es
     * obligatorio —el enunciado exige que "rejected registrations must include a
     * clear reason"— y en el resto de los casos puede estar vacio.
     */
    @Column(name = "validation_notes", length = 500)
    private String validationNotes;

    // ------------------------------------------------------------------
    // Metodos de conveniencia
    // ------------------------------------------------------------------

    /**
     * Si es una inscripcion individual.
     *
     * @Transient significa "esto NO es una columna": se deduce de los datos que ya
     * estan, en vez de guardar un campo "tipo" que podria contradecirlos. Un dato
     * guardado que se puede derivar es un dato que tarde o temprano queda
     * desincronizado.
     */
    @Transient
    public boolean esIndividual() {
        return competitor != null;
    }

    /** Si es una inscripcion de equipo. */
    @Transient
    public boolean esDeEquipo() {
        return team != null;
    }
}
