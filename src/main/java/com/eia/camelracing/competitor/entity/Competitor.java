package com.eia.camelracing.competitor.entity;

import com.eia.camelracing.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un competidor: un enano, un camello o algo de tamano intermedio.
 *
 * RELACION: Competitor N ----- 1 Team
 * Este es el lado DUENO: aca vive la foreign key (columna team_id). El equipo es
 * opcional, porque el enunciado permite competidores individuales.
 *
 * QUE UN COMPETIDOR TENGA UN SOLO CAMPO team NO ES UNA SIMPLIFICACION
 * Es como se cumple una de las reglas del enunciado: "un competidor no puede
 * pertenecer a mas de un equipo activo al mismo tiempo". Con una sola columna
 * team_id, pertenecer a dos equipos es imposible por construccion, no por un
 * chequeo que alguien puede olvidarse de escribir. La misma columna resuelve
 * gratis la otra regla, "un competidor no puede ser agregado dos veces al mismo
 * equipo": una columna guarda un valor, no dos.
 */
@Entity
@Table(
        name = "competitors",
        uniqueConstraints = {
                // "Nickname must be unique" es regla del enunciado. Va en la base
                // ademas de en el servicio: el chequeo previo del servicio se puede
                // saltear si dos requests simultaneos consultan a la vez y los dos
                // ven el apodo libre.
                @UniqueConstraint(name = "uk_competitors_nickname", columnNames = "nickname")
        },
        indexes = {
                // Los tres filtros del listado. Sin indices, filtrar por estado en
                // una tabla grande obliga a leerla entera.
                @Index(name = "idx_competitors_status", columnList = "status"),
                @Index(name = "idx_competitors_type", columnList = "type"),
                @Index(name = "idx_competitors_team", columnList = "team_id")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Competitor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    /** El apodo con el que se lo anuncia: "Null Pointer", "Tiny Docker". Unico. */
    @Column(nullable = false, length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompetitorType type;

    /**
     * LocalDate y no LocalDateTime: una fecha de nacimiento no tiene hora ni zona
     * horaria. Guardar un instante para esto genera el problema clasico de que la
     * fecha se corre un dia segun desde donde se la lea.
     *
     * El enunciado pide "fecha de nacimiento o edad aproximada". Se guarda la
     * fecha y la edad se calcula: al reves, la edad guardada envejece mal, queda
     * desactualizada al ano siguiente y nadie se acuerda de corregirla.
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * Peso en kilos y altura en centimetros, los dos en BigDecimal.
     *
     * BigDecimal y no double porque los decimales binarios no representan exacto
     * valores como 0.1, y sumas de pesos terminan con errores de redondeo. Con
     * precision 5 y scale 2 entra hasta 999,99: sobra para un camello de ~600 kg.
     *
     * Las unidades van en el nombre del campo. Un campo llamado "weight" a secas
     * es una invitacion a que alguien cargue libras.
     */
    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "country_of_origin", nullable = false, length = 100)
    private String countryOfOrigin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompetitorStatus status = CompetitorStatus.ACTIVE;

    /**
     * Cuando se dio de alta en el sistema. Lo pone el servidor, no llega en el
     * request: si lo mandara el cliente, cualquiera podria antedatar un alta.
     */
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /**
     * El equipo al que pertenece, o null si compite solo.
     *
     * optional = true (el default) porque el enunciado dice "optional team": hay
     * competidores individuales, como el camello Byte del escenario de
     * demostracion.
     *
     * fetch = LAZY explicito porque el default de @ManyToOne es EAGER, y eso
     * significa traer el equipo entero en TODA consulta de competidores aunque no
     * se use.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_competitors_team"))
    @ToString.Exclude
    private Team team;

    /**
     * Estadisticas del competidor. Mismo criterio que en Team: estan guardadas
     * porque la clasificacion ordena por ellas y recalcularlas en cada consulta
     * obligaria a recorrer todos los resultados de todas las carreras.
     *
     * Las mantiene al dia el modulo de resultados, en un solo lugar.
     */
    @Column(nullable = false)
    @Builder.Default
    private int victories = 0;

    @Column(nullable = false)
    @Builder.Default
    private int defeats = 0;

    @Column(name = "completed_races", nullable = false)
    @Builder.Default
    private int completedRaces = 0;
}
