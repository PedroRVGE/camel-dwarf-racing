package com.eia.camelracing.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una linea de la bitacora: quien hizo que, sobre que, y cuando.
 *
 * ES LA UNICA TABLA DEL PROYECTO QUE SOLO SE ESCRIBE
 * No tiene update ni delete, ni siquiera de los "blandos" que usa el resto del
 * sistema. Una bitacora que se puede editar no sirve para nada: si el que hizo
 * algo indebido puede despues corregir el registro, el registro no prueba nada.
 * Por eso el servicio solo ofrece guardar y consultar, y no hay ningun endpoint
 * que la modifique.
 *
 * POR QUE NO HAY RELACIONES A LAS OTRAS ENTIDADES
 * Seria natural poner un @ManyToOne a Competitor, otro a Race, etc. Esta a
 * proposito que no esten, por dos razones:
 *
 *   1. Una foreign key obligaria a que la fila referenciada siga existiendo. La
 *      bitacora tiene que poder decir "se cancelo la carrera X" aunque manana esa
 *      carrera no este mas. Un registro historico no puede depender de que el
 *      presente lo acompanie.
 *
 *   2. Cada accion es sobre una entidad distinta. Con relaciones habria que tener
 *      una columna por tipo de entidad, casi todas en null en cada fila. Guardar
 *      el TIPO y el ID como datos sueltos es lo que permite que una sola tabla
 *      sirva para todo el sistema.
 *
 * La contrapartida honesta: la base no garantiza que entity_id apunte a algo real,
 * y para reconstruir el nombre de lo referenciado hay que ir a buscarlo. Por eso
 * ademas se guarda una descripcion en texto, que sigue siendo legible aunque la
 * entidad ya no exista.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                // El indice mas importante es el de la fecha: la consulta natural de
                // una bitacora es "que paso ultimamente", y el orden por defecto del
                // endpoint es por fecha descendente.
                @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_audit_username", columnList = "username"),
                @Index(name = "idx_audit_action", columnList = "action"),
                // Compuesto, y en este orden: sirve para "todo lo que le paso a esta
                // carrera", que es como se investiga un caso puntual. Un indice por
                // (tipo, id) tambien resuelve las consultas que filtran solo por tipo,
                // porque el tipo es la primera columna.
                @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
        }
)
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Quien lo hizo: el username que venia en el token.
     *
     * Es texto y no una relacion porque los usuarios viven en Keycloak, igual que
     * en Race.organizer. En una bitacora esa decision ademas juega a favor: aunque
     * manana el usuario se borre de Keycloak, la linea sigue diciendo quien fue.
     */
    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    /** Sobre que tipo de cosa fue: "Competitor", "Race", "RaceRegistration"... */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** El id de esa cosa. Puede ser null: un LOGIN no es sobre ninguna entidad. */
    @Column(name = "entity_id")
    private UUID entityId;

    /**
     * Cuando paso.
     *
     * La pone @PrePersist y no el que llama, para que sea la hora real del hecho y
     * no una fecha que alguien pudo elegir.
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** Una frase en castellano que se entienda sin ir a buscar nada mas. */
    @Column(length = 500)
    private String description;

    /**
     * Como estaba antes y como quedo despues.
     *
     * Son textos cortos, no un volcado completo de la entidad: alcanza con lo que
     * cambio ("status=ACTIVE" -> "status=SUSPENDED"). Guardar la entidad entera en
     * cada modificacion haria crecer la bitacora sin limite y, sobre todo, obligaria
     * a leer dos JSON largos para encontrar el unico campo distinto.
     *
     * Los dos son opcionales: en un alta no hay valor anterior, y en una baja no hay
     * valor nuevo.
     */
    @Column(name = "previous_value", length = 1000)
    private String previousValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    @PrePersist
    void alCrear() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
