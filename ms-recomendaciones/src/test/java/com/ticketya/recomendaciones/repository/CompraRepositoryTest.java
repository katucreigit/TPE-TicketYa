package com.ticketya.recomendaciones.repository;

import com.ticketya.recomendaciones.IntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Las agregaciones de design.md D4 contra MongoDB real. */
class CompraRepositoryTest extends IntegracionTest {

    @Autowired
    private CompraRepository repository;

    @Test
    void compradoresDistintosPorEvento() throws Exception {
        registrarCompra("a", "101", 42L);
        registrarCompra("b", "101", 42L);          // el mismo cliente dos veces: cuenta una
        registrarCompra("c", "102", 42L, 43L);     // una compra con dos eventos
        registrarCompra("d", "103", 43L);

        assertThat(repository.compradoresPorEvento()).isEqualTo(Map.of(42L, 2L, 43L, 2L));
    }

    @Test
    void eventosDeUnCliente() throws Exception {
        registrarCompra("a", "101", 42L);
        registrarCompra("b", "101", 43L, 42L);
        registrarCompra("c", "102", 44L);

        assertThat(repository.eventosDe("101")).containsExactlyInAnyOrder(42L, 43L);
        assertThat(repository.eventosDe("999")).isEmpty();
    }

    @Test
    void compradoresEnComunExcluyeAlPropioClienteYSusEventos() throws Exception {
        compro("101", 10L, 50L);          // el cliente consultado
        compro("102", 10L, 42L);
        compro("103", 10L, 42L, 43L);
        compro("104", 10L, 42L);
        compro("105", 99L, 43L);           // no compro el 10: no cuenta

        Map<Long, Long> enComun = repository.compradoresEnComun(Set.of(10L, 50L), "101");

        assertThat(enComun).isEqualTo(Map.of(42L, 3L, 43L, 1L));
    }

    @Test
    void tambienCompraronSinExcluirANadie() throws Exception {
        compro("101", 42L, 43L, 44L);
        compro("102", 42L, 43L);
        compro("103", 44L);

        assertThat(repository.compradoresEnComun(Set.of(42L), null)).isEqualTo(Map.of(43L, 2L, 44L, 1L));
    }

    @Test
    void sinEventosOSinCompradoresDevuelveVacio() throws Exception {
        compro("101", 42L);

        assertThat(repository.compradoresEnComun(Set.of(), "101")).isEmpty();
        assertThat(repository.compradoresEnComun(Set.of(99L), null)).isEmpty();
        assertThat(repository.compradoresEnComun(Set.of(42L), "101")).isEmpty();
    }
}
