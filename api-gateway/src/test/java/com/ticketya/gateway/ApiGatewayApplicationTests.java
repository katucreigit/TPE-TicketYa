package com.ticketya.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Route;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
        assertThat(routeLocator).isNotNull();
    }

    // Son 5 rutas desde la Entrega 2: se sumó "auth" (/api/auth/** hacia ms-auth) al incorporar JWT.
    @Test
    void debeTenerConfiguradasLasRutasDeTodosLosServicios() {
        List<Route> rutas = Flux.from(routeLocator.getRoutes())
                .collectList()
                .block();

        assertThat(rutas).hasSize(6);

        assertThat(rutas)
                .extracting(Route::getId)
                .containsExactlyInAnyOrder(
                        "auth",
                        "reservas-disponibilidad",
                        "reservas",
                        "eventos",
                        "ventas",
                        "notificaciones"
                );
    }

    @Test
    void disponibilidadDebeTenerPrioridadSobreEventos() {
        List<Route> rutas = Flux.from(routeLocator.getRoutes())
                .collectList()
                .block();

        Route disponibilidad = rutas.stream()
                .filter(ruta -> ruta.getId().equals("reservas-disponibilidad"))
                .findFirst()
                .orElseThrow();

        Route eventos = rutas.stream()
                .filter(ruta -> ruta.getId().equals("eventos"))
                .findFirst()
                .orElseThrow();

        assertThat(disponibilidad.getOrder()).isLessThan(eventos.getOrder());
    }

    @Test
    void todasLasRutasDebenEliminarElPrefijoApi() {
        List<Route> rutas = Flux.from(routeLocator.getRoutes())
                .collectList()
                .block();

        assertThat(rutas).allSatisfy(ruta ->
                assertThat(ruta.getFilters().toString())
                        .contains("StripPrefix")
        );
    }

    @Test
    void noDebeExistirUnaRutaParaEndpointsInternos() {
        List<Route> rutas = Flux.from(routeLocator.getRoutes())
                .collectList()
                .block();

        assertThat(rutas)
                .noneMatch(ruta -> ruta.getId().contains("internal"));
    }
}