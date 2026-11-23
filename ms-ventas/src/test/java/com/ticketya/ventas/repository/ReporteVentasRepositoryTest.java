package com.ticketya.ventas.repository;

import com.ticketya.ventas.TestcontainersConfiguration;
import com.ticketya.ventas.dto.ReporteSectorVentasResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de la consulta JPQL agregada de reportes contra PostgreSQL 16 real (Testcontainers).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReporteVentasRepositoryTest {

    private static final long EVENTO_ID = 42L;
    private static final long OTRO_EVENTO_ID = 99L;
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2028, 6, 15, 21, 0);

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private EntityManager em;

    private Compra crearCompra(long eventoId, EstadoCompra estado, BigDecimal total) {
        Compra compra = new Compra();
        compra.setReservaId(UUID.randomUUID());
        compra.setClienteId("cli-" + UUID.randomUUID());
        compra.setEventoId(eventoId);
        compra.setIdempotencyKey("idem-" + UUID.randomUUID());
        compra.setRequestHash("hash-" + UUID.randomUUID().toString().replace("-", ""));
        compra.setTotal(total);
        compra.setEstado(estado);
        em.persist(compra);
        return compra;
    }

    private void agregarEntrada(Compra compra, long sectorId, BigDecimal precioUnitario, EstadoEntrada estado) {
        Entrada entrada = new Entrada();
        entrada.setEventoId(compra.getEventoId());
        entrada.setSectorId(sectorId);
        entrada.setPrecioUnitario(precioUnitario);
        entrada.setEventoFechaHora(FECHA_EVENTO);
        entrada.setCodigoQr(UUID.randomUUID().toString());
        entrada.setEstado(estado);
        compra.agregarEntrada(entrada);
        em.persist(entrada);
    }

    @Test
    void agrupaPorSectoresOrdenadosYCalculaContadoresYRecaudacionNeta() {
        BigDecimal precio11 = new BigDecimal("45000.50");
        BigDecimal precio12 = new BigDecimal("28000.25");

        // Se persiste primero el sector 12 para comprobar ORDER BY e.sectorId ASC.
        Compra compra12 = crearCompra(EVENTO_ID, EstadoCompra.APROBADA, precio12.multiply(BigDecimal.valueOf(4)));
        agregarEntrada(compra12, 12L, precio12, EstadoEntrada.EMITIDA);
        agregarEntrada(compra12, 12L, precio12, EstadoEntrada.EMITIDA);
        agregarEntrada(compra12, 12L, precio12, EstadoEntrada.EMITIDA);
        agregarEntrada(compra12, 12L, precio12, EstadoEntrada.REINTEGRADA);

        Compra compra11 = crearCompra(EVENTO_ID, EstadoCompra.APROBADA, precio11.multiply(BigDecimal.valueOf(7)));
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.EMITIDA);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.EMITIDA);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.USADA);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.DEVOLUCION_SOLICITADA);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.REINTEGRO_PENDIENTE);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.REINTEGRADA);
        agregarEntrada(compra11, 11L, precio11, EstadoEntrada.REINTEGRADA);

        em.flush();
        em.clear();

        List<ReporteSectorVentasResponse> resultado = entradaRepository.resumirVentasPorSector(EVENTO_ID);

        assertThat(resultado).hasSize(2);

        ReporteSectorVentasResponse sector11 = resultado.get(0);
        assertThat(sector11.sectorId()).isEqualTo(11L);
        assertThat(sector11.entradasVendidas()).isEqualTo(7L);
        assertThat(sector11.entradasDevueltas()).isEqualTo(2L);
        assertThat(sector11.entradasReintegroPendiente()).isEqualTo(1L);
        assertThat(sector11.entradasDevolucionSolicitada()).isEqualTo(1L);
        assertThat(sector11.recaudacionNeta()).isEqualByComparingTo("225002.50");

        ReporteSectorVentasResponse sector12 = resultado.get(1);
        assertThat(sector12.sectorId()).isEqualTo(12L);
        assertThat(sector12.entradasVendidas()).isEqualTo(4L);
        assertThat(sector12.entradasDevueltas()).isEqualTo(1L);
        assertThat(sector12.entradasReintegroPendiente()).isEqualTo(0L);
        assertThat(sector12.entradasDevolucionSolicitada()).isEqualTo(0L);
        assertThat(sector12.recaudacionNeta()).isEqualByComparingTo("84000.75");
    }

    @Test
    void sectorConTodasLasEntradasReintegradasDevuelveRecaudacionNetaCeroNoNula() {
        BigDecimal precio = new BigDecimal("45000.50");
        Compra compra = crearCompra(EVENTO_ID, EstadoCompra.APROBADA, precio.multiply(BigDecimal.valueOf(2)));
        agregarEntrada(compra, 11L, precio, EstadoEntrada.REINTEGRADA);
        agregarEntrada(compra, 11L, precio, EstadoEntrada.REINTEGRADA);

        em.flush();
        em.clear();

        List<ReporteSectorVentasResponse> resultado = entradaRepository.resumirVentasPorSector(EVENTO_ID);

        assertThat(resultado).hasSize(1);
        ReporteSectorVentasResponse sector = resultado.get(0);
        assertThat(sector.sectorId()).isEqualTo(11L);
        assertThat(sector.entradasVendidas()).isEqualTo(2L);
        assertThat(sector.entradasDevueltas()).isEqualTo(2L);
        assertThat(sector.entradasReintegroPendiente()).isEqualTo(0L);
        assertThat(sector.entradasDevolucionSolicitada()).isEqualTo(0L);
        assertThat(sector.recaudacionNeta()).isNotNull();
        assertThat(sector.recaudacionNeta()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void excluyeComprasNoAprobadasYVentasDeOtrosEventos() {
        BigDecimal precio = new BigDecimal("10000.00");

        Compra aprobada = crearCompra(EVENTO_ID, EstadoCompra.APROBADA, new BigDecimal("20000.00"));
        agregarEntrada(aprobada, 11L, precio, EstadoEntrada.EMITIDA);
        agregarEntrada(aprobada, 11L, precio, EstadoEntrada.USADA);

        for (EstadoCompra estadoNoAprobado : List.of(
                EstadoCompra.PENDIENTE,
                EstadoCompra.PAGO_INCIERTO,
                EstadoCompra.RECHAZADA,
                EstadoCompra.FALLIDA)) {
            Compra noAprobada = crearCompra(EVENTO_ID, estadoNoAprobado, precio);
            agregarEntrada(noAprobada, 11L, precio, EstadoEntrada.EMITIDA);
            agregarEntrada(noAprobada, 15L, precio, EstadoEntrada.EMITIDA);
        }

        Compra otroEvento = crearCompra(OTRO_EVENTO_ID, EstadoCompra.APROBADA, new BigDecimal("30000.00"));
        agregarEntrada(otroEvento, 11L, precio, EstadoEntrada.EMITIDA);
        agregarEntrada(otroEvento, 11L, precio, EstadoEntrada.EMITIDA);
        agregarEntrada(otroEvento, 11L, precio, EstadoEntrada.EMITIDA);

        em.flush();
        em.clear();

        List<ReporteSectorVentasResponse> resultado = entradaRepository.resumirVentasPorSector(EVENTO_ID);

        assertThat(resultado).hasSize(1);
        ReporteSectorVentasResponse sector = resultado.get(0);
        assertThat(sector.sectorId()).isEqualTo(11L);
        assertThat(sector.entradasVendidas()).isEqualTo(2L);
        assertThat(sector.recaudacionNeta()).isEqualByComparingTo("20000.00");
    }

    @Test
    void eventoSinVentasAprobadasDevuelveListaVacia() {
        Compra pendiente = crearCompra(77L, EstadoCompra.PENDIENTE, new BigDecimal("15000.00"));
        agregarEntrada(pendiente, 11L, new BigDecimal("15000.00"), EstadoEntrada.EMITIDA);

        em.flush();
        em.clear();

        assertThat(entradaRepository.resumirVentasPorSector(77L)).isEmpty();
        assertThat(entradaRepository.resumirVentasPorSector(999L)).isEmpty();
    }
}
