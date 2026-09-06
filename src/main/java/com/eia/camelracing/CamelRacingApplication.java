package com.eia.camelracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API de la liga de carreras de camellos contra enanos.
 *
 * Esta clase queda sola en el paquete raiz a proposito: @SpringBootApplication
 * activa el escaneo de componentes desde el paquete donde esta declarada hacia
 * abajo. Si se la moviera adentro de, por ejemplo, config/, Spring dejaria de
 * ver los controladores y servicios que estan en paquetes hermanos, y la
 * aplicacion arrancaria sin ningun endpoint y sin ningun error que lo explique.
 *
 * ORGANIZACION DEL CODIGO
 * Los paquetes se arman por dominio y no por capa: hay un paquete por entidad de
 * negocio (competitor, team, race, registration, result, audit) y adentro de cada
 * uno estan sus capas (controller, service, dto, entity, mapper, repository). Lo
 * transversal —seguridad, manejo de errores, configuracion de Swagger— vive en
 * common/.
 *
 * La alternativa, un controller/ y un service/ globales colgando de la raiz, se
 * ve mas ordenada al principio y envejece peor: para tocar una sola funcionalidad
 * hay que saltar entre seis carpetas, y nada impide que el servicio de carreras
 * empiece a usar el repositorio de resultados sin que nadie lo note.
 */
@SpringBootApplication
public class CamelRacingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamelRacingApplication.class, args);
    }

}
