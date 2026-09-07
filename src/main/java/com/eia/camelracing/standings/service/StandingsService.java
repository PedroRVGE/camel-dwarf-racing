package com.eia.camelracing.standings.service;

import com.eia.camelracing.common.dto.PageResponse;
import com.eia.camelracing.standings.dto.StandingResponse;
import com.eia.camelracing.standings.dto.StandingRow;
import com.eia.camelracing.standings.repository.IStandingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * La tabla de posiciones, para competidores y para equipos.
 *
 * ES EL SERVICIO MAS CHICO DEL PROYECTO Y ESO ESTA BIEN
 * No tiene reglas de negocio propias: las posiciones son una consecuencia de los
 * resultados, no una decision. Toda la logica esta en dos lugares: la escala de
 * puntos y el orden, que viven en la consulta, y la numeracion de los puestos, que
 * es lo unico que la base no puede hacer y esta explicado abajo.
 */
@Service
@RequiredArgsConstructor
public class StandingsService {

    private final IStandingsRepository standingsRepository;

    /** La tabla de los competidores individuales. */
    @Transactional(readOnly = true)
    public PageResponse<StandingResponse> getCompetitorStandings(Pageable pageable) {
        return numerar(standingsRepository.posicionesDeCompetidores(soloPaginacion(pageable)));
    }

    /** La tabla de los equipos. */
    @Transactional(readOnly = true)
    public PageResponse<StandingResponse> getTeamStandings(Pageable pageable) {
        return numerar(standingsRepository.posicionesDeEquipos(soloPaginacion(pageable)));
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    /**
     * Se queda con el numero de pagina y el tamano, y DESCARTA el ordenamiento que
     * haya pedido el cliente.
     *
     * Es la unica consulta del proyecto donde el sort no se respeta, y es a
     * proposito. En un listado de competidores, ordenar por peso o por apodo es una
     * preferencia legitima de quien mira. En una tabla de posiciones no: el orden ES
     * la tabla. Una clasificacion ordenada por nombre no es otra vista de la
     * clasificacion, es otra cosa que ya no clasifica nada, y ademas dejaria los
     * numeros de puesto diciendo cualquier cosa, porque se calculan asumiendo que la
     * fila 1 es la del primero.
     *
     * Si se dejara pasar el sort del Pageable, Spring Data lo agregaria DESPUES del
     * ORDER BY de la consulta y romperia el orden por puntos.
     */
    private Pageable soloPaginacion(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * Le pone el numero de puesto a cada fila.
     *
     * El puesto es GLOBAL, no relativo a la pagina: se cuenta desde el offset, asi
     * que la primera fila de la segunda pagina de 20 es el puesto 21 y no el 1. Es
     * lo que uno espera de una clasificacion, y es la razon por la que este calculo
     * no puede hacerse en la consulta (ver el comentario en StandingRow).
     *
     * Los empates comparten puntos pero no puesto: dos con 27 puntos quedan como
     * 3ro y 4to, desempatados por victorias y despues por nombre. Un puesto
     * compartido obligaria a numerar salteado (3ro, 3ro, 5to) y a que el frontend
     * entienda esa convencion; para el alcance de este sistema, un orden total y
     * estable es mas simple de leer y de programar.
     */
    private PageResponse<StandingResponse> numerar(Page<StandingRow> pagina) {
        long offset = pagina.getPageable().isPaged() ? pagina.getPageable().getOffset() : 0;

        List<StandingResponse> filas = new ArrayList<>(pagina.getNumberOfElements());
        int i = 0;
        for (StandingRow row : pagina.getContent()) {
            filas.add(new StandingResponse(
                    (int) (offset + ++i),
                    row.participantId(),
                    row.participantName(),
                    valor(row.points()),
                    valor(row.racesFinished()),
                    valor(row.victories()),
                    valor(row.defeats())));
        }

        return new PageResponse<>(
                filas,
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }

    /**
     * SUM() en SQL devuelve null cuando no suma ninguna fila.
     *
     * Con estas consultas no deberia pasar, porque cada grupo tiene al menos un
     * resultado. Igual se cubre: un NullPointerException en la tabla de posiciones
     * por un caso borde seria una caida boba y evitable.
     */
    private long valor(Long numero) {
        return numero == null ? 0L : numero;
    }
}
