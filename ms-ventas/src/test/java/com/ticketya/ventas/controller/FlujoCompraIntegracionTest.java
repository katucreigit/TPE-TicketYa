package com.ticketya.ventas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GlobalExceptionHandler;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.repository.CompraRepository;
import com.ticketya.ventas.repository.EntradaRepository;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.EntradaService;
import com.ticketya.ventas.service.PasarelaPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion HTTP del flujo completo de compra, reintento tras tok_timeout,
 * consulta de compras/entradas y control de acceso 403 entre clientes.
 */
@WebMvcTest({CompraController.class, EntradaController.class})
@Import({
        GlobalExceptionHandler.class,
        CompraService.class,
        EntradaService.class,
        PasarelaPagoService.class,
        FlujoCompraIntegracionTest.TxConfig.class
})
class FlujoCompraIntegracionTest {

    @TestConfiguration
    static class TxConfig {
        @Bean
        TransactionTemplate transactionTemplate() {
            return new TransactionTemplate() {
                @Override
                public void afterPropertiesSet() {
                    // Sin PlatformTransactionManager en WebMvcTest.
                }

                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    TransactionStatus status = new SimpleTransactionStatus();
                    return action.doInTransaction(status);
                }
            };
        }
    }

    private static final UUID RESERVA_ID = UUID.fromString("5b1c7e0a-3f4d-4c8a-9a62-1d7c0f2e9b11");
    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2027, 1, 15, 21, 0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompraRepository compraRepository;
    @MockitoBean
    private EntradaRepository entradaRepository;
    @MockitoBean
    private ReservasClient reservasClient;
    @MockitoBean
    private EventosClient eventosClient;

    private final Map<UUID, Compra> comprasDb = new ConcurrentHashMap<>();
    private final Map<UUID, Entrada> entradasDb = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        comprasDb.clear();
        entradasDb.clear();

        when(compraRepository.saveAndFlush(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            for (Entrada e : c.getEntradas()) {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
                entradasDb.put(e.getId(), e);
            }
            comprasDb.put(c.getId(), c);
            return c;
        });
        when(compraRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(comprasDb.get(inv.getArgument(0))));
        when(compraRepository.findWithDetallesById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(comprasDb.get(inv.getArgument(0))));
        when(compraRepository.findByClienteIdAndIdempotencyKey(anyString(), anyString())).thenAnswer(inv -> {
            String cli = inv.getArgument(0);
            String key = inv.getArgument(1);
            return comprasDb.values().stream()
                    .filter(c -> cli.equals(c.getClienteId()) && key.equals(c.getIdempotencyKey()))
                    .findFirst();
        });
        when(compraRepository.findByClienteIdOrderByCreadaEnDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> {
            String cli = inv.getArgument(0);
            Pageable p = inv.getArgument(1);
            List<Compra> lista = comprasDb.values().stream()
                    .filter(c -> cli.equals(c.getClienteId()))
                    .toList();
            return new PageImpl<>(lista, p, lista.size());
        });

        when(entradaRepository.findWithCompraById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(entradasDb.get(inv.getArgument(0))));
        when(entradaRepository.buscarDelCliente(anyString(), any(), any(), any(Pageable.class))).thenAnswer(inv -> {
            String cli = inv.getArgument(0);
            Pageable p = inv.getArgument(3);
            List<Entrada> lista = entradasDb.values().stream()
                    .filter(e -> cli.equals(e.getCompra().getClienteId()))
                    .toList();
            return new PageImpl<>(lista, p, lista.size());
        });
    }

    private static ReservaRemota reservaVigente() {
        return new ReservaRemota(RESERVA_ID, 42L, 11L, "cli-100", "PENDIENTE", 2,
                List.of(new ReservaRemota.Item(3, 10), new ReservaRemota.Item(3, 11)),
                LocalDateTime.now().plusMinutes(10));
    }

    private static SnapshotCompra snapshotEvento() {
        return new SnapshotCompra(42L, 11L, FECHA_EVENTO, new BigDecimal("45000.00"));
    }

    @Test
    void flujoCompletoExitosoCompraEmisionIdempotenciaYConsultas() throws Exception {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        String body = """
                {
                  "reservaId": "%s",
                  "medioPago": {"tipo": "TARJETA_CREDITO", "token": "tok_ok", "ultimos4": "4242"}
                }""".formatted(RESERVA_ID);

        String jsonResp = mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-flujo-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.total").value(90000.00))
                .andExpect(jsonPath("$.medioPago.tipo").value("TARJETA_CREDITO"))
                .andExpect(jsonPath("$.medioPago.ultimos4").value("4242"))
                .andExpect(jsonPath("$.medioPago.token").doesNotExist())
                .andExpect(jsonPath("$.entradas", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String compraId = objectMapper.readTree(jsonResp).get("compraId").asText();
        String entradaId = objectMapper.readTree(jsonResp).get("entradas").get(0).get("entradaId").asText();

        // Repeticion idempotente responde 200 con la misma compra sin volver a confirmar
        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-flujo-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compraId").value(compraId));
        verify(reservasClient, times(1)).confirmar(RESERVA_ID);

        // Consultas del cliente dueno
        mvc.perform(get("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/compras/" + compraId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compraId").value(compraId));

        mvc.perform(get("/entradas")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/entradas/" + entradaId)
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value(entradaId))
                .andExpect(jsonPath("$.codigoQr").isNotEmpty());

        // Otro cliente recibe 403 al consultar la compra o la entrada ajenas
        mvc.perform(get("/compras/" + compraId)
                        .header("X-User-Id", "cli-200")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        mvc.perform(get("/entradas/" + entradaId)
                        .header("X-User-Id", "cli-200")
                        .header("X-User-Rol", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void tokTimeoutResponde504YReintentoConMismaClaveCompletaLaCompraSinCobrarDosVeces() throws Exception {
        when(reservasClient.validar(RESERVA_ID, "cli-100")).thenReturn(reservaVigente());
        when(eventosClient.obtenerSnapshotParaCompra(42L, 11L)).thenReturn(snapshotEvento());

        String body = """
                {
                  "reservaId": "%s",
                  "medioPago": {"tipo": "TARJETA_DEBITO", "token": "tok_timeout", "ultimos4": "1111"}
                }""".formatted(RESERVA_ID);

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-timeout-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PASARELA_TIMEOUT"));

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-100")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-timeout-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.entradas", hasSize(2)));
    }

    @Test
    void otroClienteRecibe403AlIntentarPagarReservaAjena() throws Exception {
        when(reservasClient.validar(RESERVA_ID, "cli-200")).thenThrow(new ForbiddenException());

        String body = """
                {
                  "reservaId": "%s",
                  "medioPago": {"tipo": "TARJETA_CREDITO", "token": "tok_ok", "ultimos4": "4242"}
                }""".formatted(RESERVA_ID);

        mvc.perform(post("/compras")
                        .header("X-User-Id", "cli-200")
                        .header("X-User-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k-ajena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));

        verify(reservasClient, never()).confirmar(any());
    }
}
