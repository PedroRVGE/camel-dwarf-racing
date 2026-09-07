package com.eia.camelracing.result.entity;

import com.eia.camelracing.race.entity.Race;
import com.eia.camelracing.registration.entity.RaceRegistration;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * El resultado oficial de un participante en una carrera.
 *
 * POR QUE APUNTA A LA INSCRIPCION Y NO AL COMPETIDOR
 * Lo intuitivo seria (carrera, competidor). Apunta a la INSCRIPCION por tres
 * motivos, y el tercero es el que decide:
 *
 *   1. Un participante puede ser un competidor O un equipo. Con dos columnas
 *      opcionales habria que repetir aca toda la logica de "exactamente uno de los
 *      dos" que ya vive en RaceRegistration.
 *   2. La inscripcion ya sabe en que carrera es y con que carril largo. Duplicar
 *      esos datos seria pedirles que se mantengan sincronizados solos.
 *   3. La regla del enunciado "only approved participants may receive results" se
 *      vuelve verificable de una: se mira el estado de la inscripcion. Si el
 *      resultado apuntara al competidor, habria que salir a buscar si ademas
 *      estaba inscripto y aprobado, y nada impediria cargarle un resultado a
 *      alguien que nunca se anoto.
 *
 * La carrera igual se guarda aparte (race_id) aunque se pueda deducir de la
 * inscripcion. Es una desnormalizacion deliberada: casi toda consulta de
 * resultados empieza por "los de esta carrera", y sin la columna cada una de esas
 * consultas tendria que pasar por race_registrations. Ademas es lo que permite la
 * constraint unica (race_id, final_position), que es la que garantiza que no haya
 * dos ganadores. El servicio la copia de la inscripcion, nunca la recibe de afuera.
 */
@Entity
@Table(
        name = "race_results",
        uniqueConstraints = {
                // Un resultado por inscripcion. Cargarle dos veces el resultado al
                // mismo participante daria dos filas contradictorias sobre el mismo
                // hecho, y las estadisticas lo contarian dos veces.
                @UniqueConstraint(name = "uk_results_registration",
                        columnNames = {"registration_id"}),

                // "Only one official winner is allowed" y "final positions cannot be
                // duplicated": no puede haber dos primeros ni tres segundos en la
                // misma carrera.
                //
                // Postgres admite varios NULL dentro de una constraint unica, y aca eso
                // es exactamente lo que hace falta: todos los que no terminaron tienen
                // la posicion en null y conviven sin chocar.
                @UniqueConstraint(name = "uk_results_race_position",
                        columnNames = {"race_id", "final_position"})
        },
        indexes = {
                @Index(name = "idx_results_race", columnList = "race_id"),
                @Index(name = "idx_results_status", columnList = "status")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_results_race"))
    @ToString.Exclude
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_results_registration"))
    @ToString.Exclude
    private RaceRegistration registration;

    /**
     * El carril desde el que largo, copiado de la inscripcion al cargar el
     * resultado.
     *
     * Es una FOTO, no un espejo. Se podria leer siempre de registration.lane y
     * ahorrarse la columna, pero entonces una modificacion posterior de la
     * inscripcion cambiaria la grilla de una carrera ya corrida. La posicion de
     * largada de una carrera que ya paso es un hecho historico: se guarda como
     * quedo.
     */
    @Column(name = "starting_position")
    private Integer startingPosition;

    /**
     * En que puesto termino. Null si no termino.
     *
     * Integer y no int, y esa distincion es la que sostiene media docena de reglas:
     * el que abandono, el descalificado y el que no largo NO tienen posicion. Con
     * un int primitivo tendrian todos un 0, y la constraint unica de posiciones
     * empezaria a rechazar la segunda descalificacion de la carrera.
     */
    @Column(name = "final_position")
    private Integer finalPosition;

    /**
     * El tiempo que tardo, en segundos. Null si no termino.
     *
     * SE GUARDA EL TIEMPO TRANSCURRIDO, NO LA HORA DE LLEGADA
     * Es la decision que hace imposible, de raiz, uno de los absurdos que el
     * enunciado pide evitar: "a camel finishing before the race began". Con una
     * hora de llegada habria que compararla contra la largada en cada validacion, y
     * alcanzaria con que una sola se olvide para que quede registrado un camello
     * que llego antes de salir. Con una duracion, ese caso no se puede ni escribir:
     * lo unico que hay que exigir es que sea positiva.
     *
     * Segundos enteros y no decimales: es la unidad en la que se cuenta una carrera
     * de un kilometro entre enanos y camellos, y evita el problema de acumular
     * errores al sumar tiempos con coma.
     */
    @Column(name = "completion_time_seconds")
    private Integer completionTimeSeconds;

    /**
     * Segundos de penalizacion. Cero si no hubo.
     *
     * Se suman al tiempo, no lo reemplazan: el tiempo puro sigue estando, y asi se
     * puede explicar por que alguien que cruzo primero termino segundo.
     */
    @Column(name = "penalty_time_seconds", nullable = false)
    @Builder.Default
    private int penaltyTimeSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus status;

    /** Las notas del juez: por que se descalifico, en que kilometro abandono. */
    @Column(length = 500)
    private String notes;

    /**
     * Quien cargo el resultado y cuando.
     *
     * El enunciado los pide explicitamente ("user who recorded the result and
     * recording timestamp"), y con razon: un resultado es una afirmacion oficial
     * sobre lo que paso, y tiene que saberse quien la firmo.
     *
     * Notar que NO hay un "quien lo modifico". No hace falta: cada correccion queda
     * en la bitacora (AuditLog) con el usuario, la fecha y el antes y el despues.
     * Guardar solo al ultimo que lo toco seria menos informacion, no mas.
     */
    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (recordedAt == null) recordedAt = ahora;
        updatedAt = ahora;
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    // Metodos de conveniencia
    // ------------------------------------------------------------------

    /**
     * El tiempo que vale: el de carrera mas las penalizaciones.
     *
     * Es lo que hay que comparar para saber quien fue mas rapido, y es lo que usa
     * la validacion de coherencia entre puestos y tiempos.
     */
    @Transient
    public Integer tiempoTotalSegundos() {
        return completionTimeSeconds == null ? null : completionTimeSeconds + penaltyTimeSeconds;
    }

    /** Si este resultado es el del ganador. */
    @Transient
    public boolean esVictoria() {
        return status == ResultStatus.FINISHED
                && finalPosition != null
                && finalPosition == 1;
    }

    /** Los puntos que suma para la tabla de posiciones. La escala vive en ResultStatus. */
    @Transient
    public int puntos() {
        return status.puntos(finalPosition);
    }
}
