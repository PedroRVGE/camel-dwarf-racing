package com.eia.camelracing.competitor.mapper;

import com.eia.camelracing.competitor.dto.CompetitorRequest;
import com.eia.camelracing.competitor.dto.CompetitorResponse;
import com.eia.camelracing.competitor.entity.Competitor;
import com.eia.camelracing.competitor.entity.CompetitorStatus;
import com.eia.camelracing.team.entity.Team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Traduce entre la entidad Competitor y sus DTOs.
 *
 * Todo estatico y sin estado: no guarda nada entre llamadas, asi que no hace
 * falta que Spring lo administre como bean. La ventaja practica es que se puede
 * probar sin levantar el contexto ni la base, y los tests de mapeo corren en
 * milisegundos.
 *
 * No se usa MapStruct ni ModelMapper. Escrito a mano son treinta lineas que se
 * leen de arriba abajo y donde el compilador avisa si falta un campo; con una
 * libreria de mapeo por reflexion, un campo que no coincide se copia como null
 * sin que nadie se entere hasta que aparece en la pantalla.
 */
public class CompetitorMapper {

    // Constructor privado: es una clase de utilidad, no se instancia.
    private CompetitorMapper() {}

    /**
     * Arma la entidad a partir del request.
     *
     * El equipo llega YA CARGADO desde la base y no se busca aca adentro: el
     * mapper no debe tocar repositorios, esa es tarea del servicio. Puede venir
     * null, que es el caso normal del competidor individual.
     *
     * Dos campos no salen del request y los pone el servidor:
     *   - status arranca siempre en ACTIVE. Un competidor recien creado esta
     *     disponible; para darlo de alta lesionado hay que hacerlo y despues
     *     cambiarle el estado, que deja rastro en la auditoria.
     *   - registeredAt es el momento actual, para que nadie pueda antedatar un alta.
     */
    public static Competitor toEntity(CompetitorRequest request, Team team) {
        if (request == null) return null;
        return Competitor.builder()
                .name(request.name())
                .nickname(request.nickname())
                .type(request.type())
                .dateOfBirth(request.dateOfBirth())
                .weightKg(request.weightKg())
                .heightCm(request.heightCm())
                .countryOfOrigin(request.countryOfOrigin())
                .status(CompetitorStatus.ACTIVE)
                .registeredAt(LocalDateTime.now())
                .team(team)
                .build();
    }

    /**
     * Copia los campos editables sobre un competidor que YA existe.
     *
     * Se modifica la entidad que trajo Hibernate en vez de construir una nueva con
     * el mismo id. Si se creara una nueva, todo lo que no esta en el request
     * —victorias, derrotas, carreras completadas, fecha de alta— se guardaria en
     * cero, y un PUT para corregir un apodo borraria el historial del competidor.
     *
     * El estado tampoco se toca aca: se cambia por PATCH /status, que es una
     * operacion distinta con su propio significado.
     */
    public static void updateEntity(Competitor competitor, CompetitorRequest request, Team team) {
        if (competitor == null || request == null) return;
        competitor.setName(request.name());
        competitor.setNickname(request.nickname());
        competitor.setType(request.type());
        competitor.setDateOfBirth(request.dateOfBirth());
        competitor.setWeightKg(request.weightKg());
        competitor.setHeightCm(request.heightCm());
        competitor.setCountryOfOrigin(request.countryOfOrigin());
        // Puede ser null, y eso significa "sacalo del equipo, que compita solo".
        competitor.setTeam(team);
    }

    /**
     * Arma la respuesta que ve el cliente de la API.
     *
     * Lee competitor.getTeam(), que es LAZY: este metodo tiene que ejecutarse
     * dentro de un metodo @Transactional o con el equipo ya traido por
     * @EntityGraph, o Hibernate tira LazyInitializationException. En este proyecto
     * es seguro que la tira, porque application.yml apaga open-in-view.
     */
    public static CompetitorResponse toResponse(Competitor competitor) {
        if (competitor == null) return null;
        Team team = competitor.getTeam();
        return new CompetitorResponse(
                competitor.getId(),
                competitor.getName(),
                competitor.getNickname(),
                competitor.getType(),
                competitor.getDateOfBirth(),
                calcularEdad(competitor.getDateOfBirth()),
                competitor.getWeightKg(),
                competitor.getHeightCm(),
                competitor.getCountryOfOrigin(),
                competitor.getStatus(),
                competitor.getRegisteredAt(),
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                competitor.getVictories(),
                competitor.getDefeats(),
                competitor.getCompletedRaces()
        );
    }

    /**
     * Edad en anios cumplidos.
     *
     * Period.between y no una resta de anios: la resta da 8 para alguien que
     * cumple en diciembre y todavia tiene 7. Period tiene en cuenta el mes y el
     * dia, y ademas maneja bien los 29 de febrero.
     */
    private static int calcularEdad(LocalDate fechaDeNacimiento) {
        if (fechaDeNacimiento == null) return 0;
        return Period.between(fechaDeNacimiento, LocalDate.now()).getYears();
    }
}
