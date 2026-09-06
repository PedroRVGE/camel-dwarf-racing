package com.eia.camelracing.team.entity;

import com.eia.camelracing.competitor.entity.Competitor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un equipo: varios competidores que corren juntos, como "The Five Exceptions".
 *
 * RELACION: Team 1 ----- N Competitor
 * Un equipo tiene varios competidores; cada competidor pertenece a lo sumo a un
 * equipo (o a ninguno: el enunciado dice "optional team").
 */
@Entity
@Table(
        name = "teams",
        uniqueConstraints = {
                // El enunciado pide que el nombre del equipo sea unico. La
                // constraint va en la base y no solo en el servicio: el chequeo del
                // servicio se puede saltear por una carrera entre dos requests
                // simultaneos, que consultan a la vez, los dos ven que el nombre
                // esta libre y los dos insertan. La base es el unico lugar donde
                // "unico" significa unico de verdad.
                @UniqueConstraint(name = "uk_teams_name", columnNames = "name")
        },
        indexes = {
                // Los listados filtran por estado (mostrar solo los equipos
                // activos), y sin indice eso obliga a recorrer la tabla entera.
                @Index(name = "idx_teams_status", columnList = "status")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Team {

    /**
     * equals/hashCode solo por id.
     *
     * OJO: el id es null hasta que se guarda. Nunca metas un Team sin guardar
     * dentro de una coleccion basada en hash, porque su hashCode cambia al
     * persistirlo y despues no se lo encuentra ni buscandolo con el mismo objeto.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    /** Entrenador o responsable del equipo. */
    @Column(nullable = false, length = 150)
    private String coach;

    /**
     * length = 20 y no el default de 255: los valores del enum son cortos y fijos.
     * Ademas la migracion le pone un CHECK con los tres valores posibles, asi que
     * la base rechaza cualquier otra cosa aunque llegue por fuera de la aplicacion.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TeamStatus status = TeamStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Los integrantes del equipo.
     *
     * - mappedBy = "team": el DUENO de la relacion es Competitor, porque es el
     *   lado que tiene la foreign key (columna team_id en la tabla competitors).
     *   Esta lista es solo el reflejo: si agregas un Competitor aca y no le seteas
     *   el equipo, la columna team_id queda vacia. Por eso TeamService mueve gente
     *   con competitor.setTeam(team) y no tocando esta lista.
     *
     * - SIN cascade y SIN orphanRemoval, al reves de lo que suele verse en los
     *   ejemplos de @OneToMany. Es deliberado y es la decision mas importante de
     *   esta clase: un competidor NO es una parte de su equipo, es alguien que
     *   existe por su cuenta. Con cascade = ALL, dar de baja un equipo borraria a
     *   sus cinco enanos junto con todo su historial de carreras; con
     *   orphanRemoval, sacar a alguien del equipo lo eliminaria del sistema en vez
     *   de dejarlo libre.
     *
     * - LAZY (el default en @OneToMany): los integrantes se traen solo si alguien
     *   los pide. Como application.yml tiene open-in-view: false, hay que leerlos
     *   dentro de un metodo @Transactional o con JOIN FETCH / @EntityGraph.
     */
    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    // Sin este exclude, toString() entraria en bucle infinito:
    // Team.toString -> Competitor.toString -> Team.toString -> ...
    @ToString.Exclude
    @Builder.Default
    private List<Competitor> members = new ArrayList<>();

    /**
     * Estadisticas del equipo.
     *
     * Estan guardadas y no se calculan al vuelo contando resultados. Es una copia
     * de un dato que se puede derivar, y eso siempre se puede desincronizar, asi
     * que hace falta un motivo: la pantalla de clasificacion ordena por estos
     * numeros, y calcularlos en cada consulta obligaria a recorrer todos los
     * resultados de todas las carreras cada vez que alguien abre la tabla.
     *
     * Quien los mantiene al dia es el modulo de resultados, en un solo lugar.
     */
    @Column(nullable = false)
    @Builder.Default
    private int victories = 0;

    @Column(nullable = false)
    @Builder.Default
    private int defeats = 0;
}
