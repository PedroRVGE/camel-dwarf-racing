package com.eia.camelracing.support;

/**
 * Los nombres de las tres capas de pruebas, en un solo lugar.
 *
 * POR QUE CONSTANTES Y NO EL TEXTO SUELTO EN CADA @Tag
 * Una etiqueta mal escrita no falla: JUnit no valida los nombres, asi que un
 * @Tag("integration") en vez de @Tag("integracion") deja ese test fuera de
 * "./gradlew test -Pcapa=integracion" sin decir absolutamente nada. El test no
 * se rompe, simplemente deja de correr, que es la peor forma de perder una
 * prueba: sigue estando en el repositorio y ya no cuida nada.
 *
 * Con constantes eso no puede pasar, porque el error es de compilacion.
 *
 * QUE SIGNIFICA CADA CAPA
 *
 *   UNIT         Nada de Spring, nada de Docker, nada de base de datos. Se prueba
 *                una clase sola, con sus colaboradores reemplazados por mocks, o
 *                directamente una funcion pura. Corren en segundos y son las que
 *                se ejecutan mientras se escribe codigo.
 *
 *   INTEGRACION  Postgres de verdad, levantado con Testcontainers. Prueban lo que
 *                un mock no puede probar: que la consulta JPQL sea valida, que la
 *                constraint unica exista, que Flyway aplique el esquema. Necesitan
 *                Docker.
 *
 *   E2E          El sistema completo: la aplicacion arriba, Postgres, y un
 *                Keycloak real emitiendo tokens firmados. Se le pega por HTTP como
 *                lo haria el frontend. Son las mas lentas y las que menos hay.
 *
 * La forma de correrlas por separado esta en build.gradle y explicada en el
 * README de esta carpeta.
 */
public final class Capas {

    public static final String UNIT = "unit";
    public static final String INTEGRACION = "integracion";
    public static final String E2E = "e2e";

    private Capas() {
        // Clase de constantes: no se instancia.
    }
}
