package com.eia.camelracing.audit.repository;

import com.eia.camelracing.audit.entity.AuditAction;
import com.eia.camelracing.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface IAuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * La consulta de la bitacora, con los seis filtros combinables.
     *
     * SON MUCHOS FILTROS Y HACEN FALTA TODOS
     * Una bitacora sin filtros es una pared de texto. Las preguntas reales que se
     * le hacen son concretas: "que hizo este usuario", "quien cancelo carreras",
     * "todo lo que le paso a esta inscripcion", "que se toco el lunes a la noche".
     * Cada una de esas preguntas es uno de estos parametros, y se pueden combinar
     * entre si.
     *
     * El patron ":parametro IS NULL OR condicion" es el mismo del resto del
     * proyecto: cuando el filtro no viene, la condicion se cumple sola y no filtra
     * nada. Asi una unica consulta cubre las 64 combinaciones posibles, en vez de
     * armar el WHERE concatenando texto (que es como se escriben las inyecciones
     * SQL).
     *
     * El username se compara en minusculas de los dos lados para que buscar "Admin"
     * encuentre lo que hizo "admin". No se usa LIKE: el usuario se busca completo,
     * no por fragmento, y una comparacion exacta puede usar el indice.
     *
     * LAS DOS FECHAS NO USAN EL PATRON ":parametro IS NULL", Y NO ES UN CAPRICHO
     * Con fechas, esa forma NO funciona contra Postgres: falla con "could not
     * determine data type of parameter $9". El motivo es que Hibernate emite cada
     * aparicion del parametro como un placeholder distinto, asi que el de la
     * condicion "IS NULL" queda solo, sin nada de donde deducir su tipo. Con un
     * String o un UUID el driver igual manda el tipo y Postgres se arregla; con un
     * LocalDateTime en null manda "sin especificar" y el motor se planta.
     *
     * La solucion es COALESCE: cuando :desde viene en null, la expresion se resuelve
     * como la propia columna, con lo cual la comparacion queda "occurredAt >=
     * occurredAt", que es siempre verdadera y no filtra nada. Y como el parametro
     * aparece una sola vez, dentro de un COALESCE junto a una columna de fecha,
     * Postgres deduce el tipo sin ayuda.
     *
     * Es el mismo tipo de trampa que aparecio en ICompetitorRepository con el
     * CONCAT y los bytea: las consultas con filtros opcionales son el lugar donde
     * JPQL y el motor de base de datos se ponen de acuerdo con menos naturalidad.
     *
     * No hace falta countQuery explicito: la consulta selecciona la entidad
     * directamente, sin constructor expression, asi que Spring Data sabe derivar el
     * COUNT solo.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:username IS NULL OR LOWER(a.username) = :username)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR LOWER(a.entityType) = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND a.occurredAt >= COALESCE(:desde, a.occurredAt)
              AND a.occurredAt <= COALESCE(:hasta, a.occurredAt)
            """)
    Page<AuditLog> buscar(@Param("username") String username,
                          @Param("action") AuditAction action,
                          @Param("entityType") String entityType,
                          @Param("entityId") UUID entityId,
                          @Param("desde") LocalDateTime desde,
                          @Param("hasta") LocalDateTime hasta,
                          Pageable pageable);
}
