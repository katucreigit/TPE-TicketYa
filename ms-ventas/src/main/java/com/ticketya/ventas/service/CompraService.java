package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.CompraResponse;
import com.ticketya.ventas.dto.MedioPagoRequest;
import com.ticketya.ventas.dto.PageCompraResponse;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GoneException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.exception.PaymentRequiredException;
import com.ticketya.ventas.model.Compra;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.enums.EstadoCompra;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoPago;
import com.ticketya.ventas.repository.CompraRepository;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoCobro;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestador de la saga de compra (spec compra-entradas):
 * valida la reserva en ms-reservas, obtiene el snapshot de ms-eventos, cobra en la pasarela mock
 * con una clave derivada del id de la compra, confirma la reserva y emite las entradas con QR unico.
 */
@Service
public class CompraService {

    private static final String MSG_PAGO_RECHAZADO = "La pasarela rechazo el pago. Los lugares fueron liberados.";
    private static final String MSG_RESERVA_VENCIDA = "La reserva vencio. No se realizo ningun cobro.";

    /** Resultado de POST /compras: {@code repeticion=true} responde 200 OK y {@code false} responde 201 Created. */
    public record ResultadoCompra(CompraResponse compra, boolean repeticion) {
    }

    private final CompraRepository compraRepository;
    private final ReservasClient reservasClient;
    private final EventosClient eventosClient;
    private final PasarelaPagoService pasarelaPagoService;
    private final TransactionTemplate transaccion;
    private final Set<String> clavesEnVuelo = ConcurrentHashMap.newKeySet();

    public CompraService(CompraRepository compraRepository,
                         ReservasClient reservasClient,
                         EventosClient eventosClient,
                         PasarelaPagoService pasarelaPagoService,
                         TransactionTemplate transaccion) {
        this.compraRepository = compraRepository;
        this.reservasClient = reservasClient;
        this.eventosClient = eventosClient;
        this.pasarelaPagoService = pasarelaPagoService;
        this.transaccion = transaccion;
    }

    public ResultadoCompra crear(String clienteId, String idempotencyKey, CompraRequest request) {
        String requestHash = calcularHash(request);
        String claveConcurrencia = clienteId + ":" + idempotencyKey;
        if (!clavesEnVuelo.add(claveConcurrencia)) {
            throw new ConflictException("CONFLICTO_CONCURRENCIA",
                    "Ya hay una solicitud en curso para la misma Idempotency-Key");
        }
        try {
            return ejecutarSaga(clienteId, idempotencyKey, request, requestHash);
        } finally {
            clavesEnVuelo.remove(claveConcurrencia);
        }
    }

    @Transactional(readOnly = true)
    public PageCompraResponse listarDelCliente(String clienteId, int page, int size) {
        Page<CompraResponse> compras = compraRepository
                .findByClienteIdOrderByCreadaEnDesc(clienteId, PageRequest.of(page, size))
                .map(CompraResponse::from);
        return PageCompraResponse.from(compras);
    }

    @Transactional(readOnly = true)
    public CompraResponse obtener(UUID id, String solicitanteId, boolean esAdmin) {
        Compra compra = compraRepository.findWithDetallesById(id)
                .orElseThrow(() -> new NotFoundException("La compra " + id + " no existe"));
        if (!esAdmin && !compra.getClienteId().equals(solicitanteId)) {
            throw new ForbiddenException();
        }
        return CompraResponse.from(compra);
    }

    private ResultadoCompra ejecutarSaga(String clienteId, String idempotencyKey,
                                         CompraRequest request, String requestHash) {
        Compra existente = compraRepository.findByClienteIdAndIdempotencyKey(clienteId, idempotencyKey).orElse(null);
        if (existente != null) {
            if (!existente.getRequestHash().equals(requestHash)) {
                throw new BusinessRuleException("IDEMPOTENCY_KEY_REUTILIZADA",
                        "La clave ya fue usada con una solicitud distinta");
            }
            switch (existente.getEstado()) {
                case APROBADA -> {
                    return new ResultadoCompra(CompraResponse.from(existente), true);
                }
                case RECHAZADA -> throw new PaymentRequiredException(MSG_PAGO_RECHAZADO);
                case FALLIDA -> throw new GoneException(MSG_RESERVA_VENCIDA);
                case PENDIENTE -> {
                    // Reintento tras falla de comunicacion (502/504): se reutiliza la misma compra y clave de cobro.
                }
            }
        }

        ReservaRemota reserva;
        try {
            reserva = reservasClient.validar(request.reservaId(), clienteId);
        } catch (GoneException ex) {
            marcarORegistrarFallida(existente, clienteId, idempotencyKey, request.reservaId(), requestHash);
            throw ex;
        }

        Compra compra = existente != null
                ? existente
                : registrarPendiente(clienteId, idempotencyKey, request.reservaId(),
                        reserva.eventoId(), requestHash, BigDecimal.ZERO);

        SnapshotCompra snapshot = eventosClient.obtenerSnapshotParaCompra(reserva.eventoId(), reserva.sectorId());
        BigDecimal total = snapshot.precioUnitario().multiply(BigDecimal.valueOf(reserva.cantidad()));
        compra = actualizarPendiente(compra.getId(), reserva.eventoId(), total);

        String claveCobro = claveCobroDe(compra.getId());
        ResultadoCobro cobro = pasarelaPagoService.cobrar(claveCobro, request.medioPago().token(), total);

        if (!cobro.aprobado()) {
            reservasClient.liberar(reserva.reservaId());
            guardarRechazada(compra.getId(), request.medioPago(), total);
            throw new PaymentRequiredException(MSG_PAGO_RECHAZADO);
        }

        try {
            reservasClient.confirmar(reserva.reservaId());
        } catch (GoneException | ConflictException ex) {
            pasarelaPagoService.reembolsar(cobro.transaccionId());
            guardarFallidaConReembolso(compra.getId(), request.medioPago(), total, cobro.transaccionId());
            throw ex;
        }

        CompraResponse aprobada = guardarAprobadaConEntradas(
                compra.getId(), request.medioPago(), total, cobro.transaccionId(), reserva, snapshot);
        return new ResultadoCompra(aprobada, false);
    }

    /** Clave de cobro idempotente hacia la pasarela, derivada del id de la compra. */
    public static String claveCobroDe(UUID compraId) {
        return "cobro-" + compraId;
    }

    private Compra registrarPendiente(String clienteId, String idempotencyKey, UUID reservaId,
                                      Long eventoId, String requestHash, BigDecimal total) {
        try {
            return transaccion.execute(status -> {
                Compra nueva = new Compra();
                nueva.setClienteId(clienteId);
                nueva.setIdempotencyKey(idempotencyKey);
                nueva.setReservaId(reservaId);
                nueva.setEventoId(eventoId);
                nueva.setRequestHash(requestHash);
                nueva.setTotal(total);
                nueva.setEstado(EstadoCompra.PENDIENTE);
                return compraRepository.saveAndFlush(nueva);
            });
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("CONFLICTO_CONCURRENCIA",
                    "Ya existe una solicitud con la misma Idempotency-Key");
        }
    }

    private Compra actualizarPendiente(UUID compraId, Long eventoId, BigDecimal total) {
        return transaccion.execute(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            c.setEventoId(eventoId);
            c.setTotal(total);
            return compraRepository.saveAndFlush(c);
        });
    }

    private void marcarORegistrarFallida(Compra existente, String clienteId, String idempotencyKey,
                                         UUID reservaId, String requestHash) {
        transaccion.executeWithoutResult(status -> {
            if (existente != null) {
                Compra c = compraRepository.findById(existente.getId()).orElseThrow();
                c.setEstado(EstadoCompra.FALLIDA);
                compraRepository.saveAndFlush(c);
            } else {
                Compra fallida = new Compra();
                fallida.setClienteId(clienteId);
                fallida.setIdempotencyKey(idempotencyKey);
                fallida.setReservaId(reservaId);
                fallida.setEventoId(0L);
                fallida.setRequestHash(requestHash);
                fallida.setTotal(BigDecimal.ZERO);
                fallida.setEstado(EstadoCompra.FALLIDA);
                compraRepository.saveAndFlush(fallida);
            }
        });
    }

    private void guardarRechazada(UUID compraId, MedioPagoRequest medioPago, BigDecimal total) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            c.setEstado(EstadoCompra.RECHAZADA);
            c.setPagoBidireccional(crearPago(medioPago, total, EstadoPago.RECHAZADO, null));
            compraRepository.saveAndFlush(c);
        });
    }

    private void guardarFallidaConReembolso(UUID compraId, MedioPagoRequest medioPago,
                                            BigDecimal total, String transaccionId) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            c.setEstado(EstadoCompra.FALLIDA);
            c.setPagoBidireccional(crearPago(medioPago, total, EstadoPago.REEMBOLSADO, transaccionId));
            compraRepository.saveAndFlush(c);
        });
    }

    private CompraResponse guardarAprobadaConEntradas(UUID compraId, MedioPagoRequest medioPago,
                                                      BigDecimal total, String transaccionId,
                                                      ReservaRemota reserva, SnapshotCompra snapshot) {
        return transaccion.execute(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            c.setEstado(EstadoCompra.APROBADA);
            c.setPagoBidireccional(crearPago(medioPago, total, EstadoPago.APROBADO, transaccionId));

            List<ReservaRemota.Item> items = reserva.items();
            if (items != null && !items.isEmpty()) {
                for (ReservaRemota.Item item : items) {
                    c.agregarEntrada(crearEntrada(reserva, snapshot, item.fila(), item.numero()));
                }
            } else {
                for (int i = 0; i < reserva.cantidad(); i++) {
                    c.agregarEntrada(crearEntrada(reserva, snapshot, null, null));
                }
            }

            Compra guardada = compraRepository.saveAndFlush(c);
            return CompraResponse.from(guardada);
        });
    }

    private static Pago crearPago(MedioPagoRequest medioPago, BigDecimal monto,
                                  EstadoPago estado, String transaccionPasarela) {
        Pago pago = new Pago();
        pago.setMedioPagoTipo(medioPago.tipo());
        pago.setUltimos4(medioPago.ultimos4());
        pago.setMonto(monto);
        pago.setEstado(estado);
        pago.setTransaccionPasarela(transaccionPasarela);
        return pago;
    }

    private static Entrada crearEntrada(ReservaRemota reserva, SnapshotCompra snapshot,
                                        Integer fila, Integer numero) {
        Entrada entrada = new Entrada();
        entrada.setEventoId(reserva.eventoId());
        entrada.setSectorId(reserva.sectorId());
        entrada.setFila(fila);
        entrada.setNumero(numero);
        entrada.setPrecioUnitario(snapshot.precioUnitario());
        entrada.setEventoFechaHora(snapshot.fechaHoraEvento());
        entrada.setCodigoQr(UUID.randomUUID().toString());
        entrada.setEstado(EstadoEntrada.EMITIDA);
        return entrada;
    }

    static String calcularHash(CompraRequest request) {
        MedioPagoRequest mp = request.medioPago();
        String canonical = request.reservaId() + "|"
                + mp.tipo() + "|"
                + mp.token() + "|"
                + (mp.ultimos4() != null ? mp.ultimos4() : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
