package com.ticketya.ventas.repository;

import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consultas y actualizaciones atomicas de EntradaRepository contra PostgreSQL 16 real (Testcontainers).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EntradaRepositoryTest {

    private static final long EVENTO = 42L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 23, 19, 30, 0);

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private EntityManager em;

    @Test
    void marcarUsadaSiEmitidaActualizaEstadoYAuditoriaCuandoCoincide() {
        Entrada entrada = crearEntrada(EVENTO, EstadoEntrada.EMITIDA);

        int afectadas = entradaRepository.marcarUsadaSiEmitida(entrada.getCodigoQr(), EVENTO, AHORA, "acc-7");

        assertThat(afectadas).isEqualTo(1);
        Entrada recargada = entradaRepository.findByCodigoQr(entrada.getCodigoQr()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEntrada.USADA);
        assertThat(recargada.getUsadaEn()).isEqualTo(AHORA);
        assertThat(recargada.getValidadaPor()).isEqualTo("acc-7");
    }

    @Test
    void marcarUsadaSiEmitidaDevuelveCeroPorQrInexistenteOFormatoNoUuid() {
        crearEntrada(EVENTO, EstadoEntrada.EMITIDA);

        assertThat(entradaRepository.marcarUsadaSiEmitida(UUID.randomUUID().toString(), EVENTO, AHORA, "acc-7"))
                .isZero();
        assertThat(entradaRepository.marcarUsadaSiEmitida("no-es-un-uuid", EVENTO, AHORA, "acc-7"))
                .isZero();
        assertThat(entradaRepository.marcarUsadaSiEmitida("cadena-arbitraria-mas-larga-que-treinta-y-seis-caracteres",
                EVENTO, AHORA, "acc-7"))
                .isZero();
        assertThat(entradaRepository.findByCodigoQr("no-es-un-uuid")).isEmpty();
    }

    @Test
    void marcarUsadaSiEmitidaDevuelveCeroCuandoElEventoEsIncorrectoYNoModificaLaEntrada() {
        Entrada entrada = crearEntrada(EVENTO, EstadoEntrada.EMITIDA);

        int afectadas = entradaRepository.marcarUsadaSiEmitida(entrada.getCodigoQr(), 99L, AHORA, "acc-7");

        assertThat(afectadas).isZero();
        Entrada recargada = entradaRepository.findById(entrada.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEntrada.EMITIDA);
        assertThat(recargada.getUsadaEn()).isNull();
        assertThat(recargada.getValidadaPor()).isNull();
    }

    @Test
    void marcarUsadaSiEmitidaDevuelveCeroParaCadaEstadoDistintoDeEmitida() {
        LocalDateTime usoPrevio = AHORA.minusHours(1);
        for (EstadoEntrada estado : new EstadoEntrada[]{
                EstadoEntrada.USADA,
                EstadoEntrada.DEVOLUCION_SOLICITADA,
                EstadoEntrada.REINTEGRO_PENDIENTE,
                EstadoEntrada.REINTEGRADA
        }) {
            Entrada entrada = crearEntrada(EVENTO, estado);
            if (estado == EstadoEntrada.USADA) {
                entrada.setUsadaEn(usoPrevio);
                entrada.setValidadaPor("acc-previo");
                entradaRepository.saveAndFlush(entrada);
                em.clear();
            }

            int afectadas = entradaRepository.marcarUsadaSiEmitida(entrada.getCodigoQr(), EVENTO, AHORA, "acc-7");

            assertThat(afectadas).as("estado %s", estado).isZero();
            Entrada recargada = entradaRepository.findById(entrada.getId()).orElseThrow();
            assertThat(recargada.getEstado()).isEqualTo(estado);
            if (estado == EstadoEntrada.USADA) {
                assertThat(recargada.getUsadaEn()).isEqualTo(usoPrevio);
                assertThat(recargada.getValidadaPor()).isEqualTo("acc-previo");
            }
        }
    }

    @Test
    void transicionarDesdeEmitidaSoloAplicaSiEstadoEsEmitida() {
        Entrada emitida = crearEntrada(EVENTO, EstadoEntrada.EMITIDA);

        assertThat(entradaRepository.transicionarDesdeEmitida(emitida.getId(), EstadoEntrada.DEVOLUCION_SOLICITADA))
                .isEqualTo(1);
        assertThat(entradaRepository.findById(emitida.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoEntrada.DEVOLUCION_SOLICITADA);
        assertThat(entradaRepository.transicionarDesdeEmitida(emitida.getId(), EstadoEntrada.REINTEGRO_PENDIENTE))
                .isZero();

        for (EstadoEntrada estado : new EstadoEntrada[]{
                EstadoEntrada.USADA,
                EstadoEntrada.REINTEGRO_PENDIENTE,
                EstadoEntrada.REINTEGRADA
        }) {
            Entrada otra = crearEntrada(EVENTO, estado);
            assertThat(entradaRepository.transicionarDesdeEmitida(otra.getId(), EstadoEntrada.REINTEGRO_PENDIENTE))
                    .as("estado %s", estado).isZero();
        }
    }

    private Entrada crearEntrada(Long eventoId, EstadoEntrada estado) {
        Compra compra = new Compra();
        compra.setReservaId(UUID.randomUUID());
        compra.setClienteId("cli-100");
        compra.setEventoId(eventoId);
        compra.setIdempotencyKey("k-" + UUID.randomUUID());
        compra.setRequestHash("a".repeat(64));
        compra.setTotal(new BigDecimal("45000.00"));
        compra.setEstado(EstadoCompra.APROBADA);

        Entrada entrada = new Entrada();
        entrada.setEventoId(eventoId);
        entrada.setSectorId(11L);
        entrada.setFila(1);
        entrada.setNumero(1);
        entrada.setPrecioUnitario(new BigDecimal("45000.00"));
        entrada.setEventoFechaHora(LocalDateTime.of(2027, 1, 15, 21, 0));
        entrada.setCodigoQr(UUID.randomUUID().toString());
        entrada.setEstado(estado);
        compra.agregarEntrada(entrada);

        Compra guardada = compraRepository.saveAndFlush(compra);
        Entrada persistida = guardada.getEntradas().get(0);
        em.clear();
        return persistida;
    }
}
