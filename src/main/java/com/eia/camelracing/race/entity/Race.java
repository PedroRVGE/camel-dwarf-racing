package com.eia.camelracing.race.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una carrera.
 *
 * POR QUE ESTA ENTIDAD NO TIENE UNA LISTA DE INSCRIPCIONES
 * Lo natural seria poner un @OneToMany<RaceRegistration> aca, y esta a proposito
 * que no este. Las diapositivas de la materia lo dicen en una linea: "no modele
 * relaciones solo porque existen en la vida real; modele las que necesita
 * consultar, validar o persistir".
 *
 * Con la coleccion mapeada, todo listado paginado de carreras pasa a ser un campo
 * minado: cualquier @EntityGraph que la traiga multiplica las filas del JOIN y
 * rompe el LIMIT, y sin el aparece el N+1. A cambio no se gana nada, porque las
 * inscripciones SIEMPRE se consultan al reves —"las de esta carrera"— y para eso
 * alcanza con RaceRegistration.race, que es el lado que tiene la foreign key.
 *
 * Los conteos que hacen falta (cuantas aprobadas, cuantas pendientes) salen de
 * consultas COUNT, que traen un numero en vez de una lista entera.
 *
 * Tampoco hace falta cascade: las carreras no se borran nunca. Cancelar una es un
 * cambio de estado, asi que jamas queda una inscripcion apuntando a una carrera
 * inexistente.
 */
@Entity
@Table(
        name = "races",
        indexes = {
                // Los tres filtros del listado. El de scheduled_at ademas sirve para
                // el orden por defecto y para el panel de "proximas carreras", que es
                // la primera pantalla que ve cualquiera.
                @Index(name = "idx_races_status", columnList = "status"),
                @Index(name = "idx_races_type", columnList = "type"),
                @Index(name = "idx_races_scheduled_at", columnList = "scheduled_at")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    /** Cuando larga. Fecha Y hora: a diferencia de un cumpleanos, el momento importa. */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "start_location", nullable = false, length = 150)
    private String startLocation;

    @Column(name = "finish_location", nullable = false, length = 150)
    private String finishLocation;

    /**
     * Distancia en metros, y en metros enteros.
     *
     * El enunciado la pide asi ("distance in meters"), y ademas evita el problema
     * de los decimales: la carrera original era de un kilometro, o sea 1000, y
     * nadie necesita medio metro. La unidad va en el nombre del campo para que a
     * nadie se le ocurra cargar kilometros.
     */
    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    /** Cupo maximo de participantes, contando competidores individuales y equipos. */
    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RaceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RaceStatus status = RaceStatus.DRAFT;

    /**
     * Quien organiza la carrera: el nombre de usuario que la creo.
     *
     * Es un String y no una relacion a una entidad User, porque en este proyecto
     * los usuarios NO viven en esta base: viven en Keycloak. La aplicacion nunca
     * guarda usuarios ni contrasenas, solo recibe tokens firmados. Lo que si puede
     * hacer es dejar registrado quien hizo cada cosa, y para eso guarda el
     * username que venia en el token.
     *
     * La contrapartida honesta es que si un administrador le cambia el nombre de
     * usuario a alguien en Keycloak, este campo sigue diciendo el anterior. Es el
     * precio de no duplicar los usuarios en dos lados, que traeria un problema
     * peor: dos fuentes de verdad que se desincronizan.
     */
    @Column(nullable = false, length = 100)
    private String organizer;

    /**
     * Hasta cuando se puede anotar gente.
     *
     * Tiene que ser ANTERIOR a scheduledAt, y esa regla se valida en RaceService:
     * una inscripcion que cierra despues de largada la carrera no tiene sentido.
     */
    @Column(name = "registration_deadline", nullable = false)
    private LocalDateTime registrationDeadline;

    /**
     * Marcas de tiempo de creacion y ultima modificacion, que el enunciado pide
     * explicitamente.
     *
     * Las mantienen los dos metodos de abajo con @PrePersist y @PreUpdate, que
     * Hibernate llama solo antes de insertar y antes de actualizar. Se hace asi y
     * no seteandolas a mano en el servicio porque a mano se olvida: alcanza con que
     * un metodo nuevo modifique la carrera sin acordarse de tocar updatedAt para
     * que el campo empiece a mentir.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Antes del INSERT: las dos marcas arrancan iguales. */
    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (createdAt == null) createdAt = ahora;
        updatedAt = ahora;
    }

    /** Antes de cada UPDATE: se refresca solo la de modificacion. */
    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
