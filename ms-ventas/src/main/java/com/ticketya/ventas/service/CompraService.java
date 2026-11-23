package com.ticketya.ventas.service;

import com.ticketya.ventas.client.EventoRemoto.SnapshotCompra;
import com.ticketya.ventas.client.EventosClient;
import com.ticketya.ventas.client.NotificacionesClient;
import com.ticketya.ventas.client.NotificacionesClient.TipoNotificacion;
import com.ticketya.ventas.client.RecomendacionesClient;
import com.ticketya.ventas.client.ReservaRemota;
import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.CompraResponse;
import com.ticketya.ventas.dto.EntradaResponse;
import com.ticketya.ventas.dto.MedioPagoRequest;
import com.ticketya.ventas.dto.PageCompraResponse;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.GatewayTimeoutException;
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
import com.ticketya.ventas.service.PasarelaPagoService.EstadoConsulta;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoCobro;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoConsultaCobro;
import com.ticketya.ventas.trazabilidad.RegistroOperaciones;
import com.ticketya.ventas.trazabilidad.RegistroOperaciones.Operacion;
import com.ticketya.ventas.trazabilidad.RegistroOperaciones.ResultadoReconciliacion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.ticketya.ventas.trazabilidad.RegistroOperaciones.datos;

/**
 * Orquestador de la saga de compra (specs compra-entradas y resiliencia-pagos):
 * valida la reserva en ms-reservas, obtiene el snapshot de ms-eventos, cobra en la pasarela
 * con clave idempotente derivada del id de la compra, consulta el estado antes de reintentar ante
 * timeouts, persiste {@code PAGO_INCIERTO} si la incertidumbre persiste, confirma la reserva y
 * emite las entradas con QR unico.
 */
@Service
public class CompraService {

    private static final int DEFAULT_MAX_REINTENTOS = 2;
    private static final String MSG_PAGO_RECHAZADO = "La pasarela rechazo el pago. Los lugares fueron liberados.";
    private static final String MSG_RESERVA_VENCIDA = "La reserva vencio. No se realizo ningun cobro.";
    private static final String MSG_PASARELA_TIMEOUT = "La pasarela de pagos no respondio. Reintente con la misma Idempotency-Key.";

    /** Resultado de POST /compras: {@code repeticion=true} responde 200 OK y {@code false} responde 201 Created. */
    public record ResultadoCompra(CompraResponse compra, boolean repeticion) {
    }

    private final CompraRepository compraRepository;
    private final ReservasClient reservasClient;
    private final EventosClient eventosClient;
    private final PasarelaPagoService pasarelaPagoService;
    private final NotificacionesClient notificacionesClient;
    private RecomendacionesClient recomendacionesClient;
    private boolean avisarRecomendaciones = true;
    private final TransactionTemplate transaccion;
    private final RegistroOperaciones registro;
    private final int maxReintentos;
    private final Set<String> clavesEnVuelo = ConcurrentHashMap.newKeySet();
    private final Set<UUID> comprasEnResolucion = ConcurrentHashMap.newKeySet();

    public CompraService(CompraRepository compraRepository,
                         ReservasClient reservasClient,
                         EventosClient eventosClient,
                         PasarelaPagoService pasarelaPagoService,
                         NotificacionesClient notificacionesClient,
                         TransactionTemplate transaccion) {
        this(compraRepository, reservasClient, eventosClient, pasarelaPagoService,
                notificacionesClient, transaccion, DEFAULT_MAX_REINTENTOS, new RegistroOperaciones());
    }

    public CompraService(CompraRepository compraRepository,
                         ReservasClient reservasClient,
                         EventosClient eventosClient,
                         PasarelaPagoService pasarelaPagoService,
                         NotificacionesClient notificacionesClient,
                         TransactionTemplate transaccion,
                         int maxReintentos) {
        this(compraRepository, reservasClient, eventosClient, pasarelaPagoService,
                notificacionesClient, transaccion, maxReintentos, new RegistroOperaciones());
    }

    @Autowired
    public CompraService(CompraRepository compraRepository,
                         ReservasClient reservasClient,
                         EventosClient eventosClient,
                         PasarelaPagoService pasarelaPagoService,
                         NotificacionesClient notificacionesClient,
                         TransactionTemplate transaccion,
                         @Value("${ticketya.pagos.max-reintentos:2}") int maxReintentos,
                         ObjectProvider<RegistroOperaciones> registro) {
        this(compraRepository, reservasClient, eventosClient, pasarelaPagoService,
                notificacionesClient, transaccion, maxReintentos,
                registro.getIfAvailable(RegistroOperaciones::new));
    }

    public CompraService(CompraRepository compraRepository,
                         ReservasClient reservasClient,
                         EventosClient eventosClient,
                         PasarelaPagoService pasarelaPagoService,
                         NotificacionesClient notificacionesClient,
                         TransactionTemplate transaccion,
                         int maxReintentos,
                         RegistroOperaciones registro) {
        this.compraRepository = compraRepository;
        this.reservasClient = reservasClient;
        this.eventosClient = eventosClient;
        this.pasarelaPagoService = pasarelaPagoService;
        this.notificacionesClient = notificacionesClient;
        this.transaccion = transaccion;
        this.registro = registro;
        this.maxReintentos = Math.max(0, maxReintentos);
    }

    public ResultadoCompra crear(String clienteId, String idempotencyKey, CompraRequest request) {
        return crear(clienteId, idempotencyKey, request, null);
    }

    public ResultadoCompra crear(String clienteId, String idempotencyKey,
                                 CompraRequest request, String clienteEmail) {
        String requestHash = calcularHash(request);
        String claveConcurrencia = clienteId + ":" + idempotencyKey;
        if (!clavesEnVuelo.add(claveConcurrencia)) {
            throw new ConflictException("CONFLICTO_CONCURRENCIA",
                    "Ya hay una solicitud en curso para la misma Idempotency-Key");
        }
        try {
            return ejecutarSaga(clienteId, idempotencyKey, request, requestHash, clienteEmail);
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
                                         CompraRequest request, String requestHash, String clienteEmail) {
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
                case PAGO_INCIERTO -> {
                    return reintentarPagoIncierto(existente, clienteId, request);
                }
                case PENDIENTE -> {
                    // Reintento tras falla de comunicacion (502): se reutiliza la misma compra y clave de cobro.
                }
            }
        }

        ReservaRemota reserva = validarReservaOActualizarFallida(existente, clienteId, idempotencyKey, request, requestHash);

        Compra compra = existente != null
                ? existente
                : registrarPendiente(clienteId, clienteEmail, idempotencyKey, request.reservaId(),
                        reserva.eventoId(), requestHash, BigDecimal.ZERO);

        SnapshotCompra snapshot = eventosClient.obtenerSnapshotParaCompra(reserva.eventoId(), reserva.sectorId());
        BigDecimal total = snapshot.precioUnitario().multiply(BigDecimal.valueOf(reserva.cantidad()));
        compra = actualizarPendiente(compra.getId(), reserva.eventoId(), total);

        ResultadoCobro cobro = cobrarResiliente(compra.getId(), request.medioPago(), total);
        return completarTrasCobro(compra.getId(), compra.getClienteId(), compra.getClienteEmail(),
                request.medioPago(), total, cobro, reserva, snapshot);
    }

    private ResultadoCompra reintentarPagoIncierto(Compra existente, String clienteId, CompraRequest request) {
        UUID compraId = existente.getId();
        if (!intentarAdquirirResolucion(compraId)) {
            throw new ConflictException("CONFLICTO_CONCURRENCIA",
                    "Ya hay una resolucion en curso para la compra " + compraId);
        }
        try {
            Compra actual = compraRepository.findWithDetallesById(compraId).orElse(existente);
            switch (actual.getEstado()) {
                case APROBADA -> {
                    return new ResultadoCompra(CompraResponse.from(actual), true);
                }
                case RECHAZADA -> throw new PaymentRequiredException(MSG_PAGO_RECHAZADO);
                case FALLIDA -> throw new GoneException(MSG_RESERVA_VENCIDA);
                case PENDIENTE, PAGO_INCIERTO -> {
                    // Continua resolucion consultando primero el estado en pasarela.
                }
            }

            String claveCobro = claveCobroDe(compraId);
            ResultadoConsultaCobro consulta = consultarEstadoSeguro(claveCobro);

            if (consulta.aprobado()) {
                return resolverPagoInciertoAprobado(actual, clienteId, request, consulta.transaccionId());
            }
            if (consulta.rechazado()) {
                reservasClient.liberar(request.reservaId());
                guardarRechazada(compraId, request.medioPago(), actual.getTotal());
                throw new PaymentRequiredException(MSG_PAGO_RECHAZADO);
            }
            if (consulta.noEncontrado()) {
                ReservaRemota reserva = validarReservaOActualizarFallida(
                        actual, clienteId, actual.getIdempotencyKey(), request, actual.getRequestHash());
                SnapshotCompra snapshot = eventosClient.obtenerSnapshotParaCompra(reserva.eventoId(), reserva.sectorId());
                BigDecimal total = snapshot.precioUnitario().multiply(BigDecimal.valueOf(reserva.cantidad()));
                actualizarPendiente(compraId, reserva.eventoId(), total);
                ResultadoCobro cobro = cobrarResiliente(compraId, request.medioPago(), total);
                return completarTrasCobro(compraId, actual.getClienteId(), actual.getClienteEmail(),
                        request.medioPago(), total, cobro, reserva, snapshot);
            }

            throw new GatewayTimeoutException(MSG_PASARELA_TIMEOUT);
        } finally {
            liberarResolucion(compraId);
        }
    }

    private ResultadoCompra resolverPagoInciertoAprobado(Compra compra,
                                                         String clienteId,
                                                         CompraRequest request,
                                                         String transaccionId) {
        ReservaRemota reserva;
        try {
            reserva = reservasClient.obtener(request.reservaId(), clienteId);
        } catch (NotFoundException ex) {
            pasarelaPagoService.reembolsar(transaccionId);
            guardarFallidaConReembolso(compra.getId(), request.medioPago(), compra.getTotal(), transaccionId);
            throw ex;
        }

        String estadoReserva = reserva.estado();
        if ("VENCIDA".equals(estadoReserva)) {
            pasarelaPagoService.reembolsar(transaccionId);
            guardarFallidaConReembolso(compra.getId(), request.medioPago(), compra.getTotal(), transaccionId);
            throw new GoneException("La reserva vencio. Se reembolso el pago.");
        }
        if ("LIBERADA".equals(estadoReserva)) {
            pasarelaPagoService.reembolsar(transaccionId);
            guardarFallidaConReembolso(compra.getId(), request.medioPago(), compra.getTotal(), transaccionId);
            throw ConflictException.estadoInvalido("La reserva " + request.reservaId() + " esta LIBERADA");
        }

        SnapshotCompra snapshot = eventosClient.obtenerSnapshotParaCompra(reserva.eventoId(), reserva.sectorId());
        BigDecimal total = snapshot.precioUnitario().multiply(BigDecimal.valueOf(reserva.cantidad()));

        if (!"CONFIRMADA".equals(estadoReserva)) {
            try {
                reservasClient.confirmar(reserva.reservaId());
            } catch (GoneException | ConflictException ex) {
                pasarelaPagoService.reembolsar(transaccionId);
                guardarFallidaConReembolso(compra.getId(), request.medioPago(), total, transaccionId);
                throw ex;
            }
        }

        CompraResponse aprobada = guardarAprobadaConEntradas(
                compra.getId(), request.medioPago(), total, transaccionId, reserva, snapshot);
        notificarCompraConfirmada(compra.getClienteId(), compra.getClienteEmail(), snapshot.eventoId(), aprobada);
        return new ResultadoCompra(aprobada, false);
    }

    private ReservaRemota validarReservaOActualizarFallida(Compra existente,
                                                            String clienteId,
                                                            String idempotencyKey,
                                                            CompraRequest request,
                                                            String requestHash) {
        try {
            return reservasClient.validar(request.reservaId(), clienteId);
        } catch (GoneException ex) {
            marcarORegistrarFallida(existente, clienteId, idempotencyKey, request.reservaId(), requestHash);
            throw ex;
        }
    }

    private ResultadoCobro cobrarResiliente(UUID compraId, MedioPagoRequest medioPago, BigDecimal total) {
        String claveCobro = claveCobroDe(compraId);
        try {
            return pasarelaPagoService.cobrar(claveCobro, medioPago.token(), total);
        } catch (GatewayTimeoutException ex) {
            for (int intento = 1; intento <= maxReintentos; intento++) {
                ResultadoConsultaCobro consulta = consultarEstadoSeguro(claveCobro);
                if (consulta.aprobado()) {
                    return new ResultadoCobro(EstadoPago.APROBADO, consulta.transaccionId());
                }
                if (consulta.rechazado()) {
                    return new ResultadoCobro(EstadoPago.RECHAZADO, null);
                }
                if (consulta.noEncontrado()) {
                    try {
                        return pasarelaPagoService.cobrar(claveCobro, medioPago.token(), total);
                    } catch (GatewayTimeoutException retryEx) {
                        // Continua dentro del limite configurado hasta resolver o pasar a PAGO_INCIERTO.
                    }
                }
            }
            guardarPagoIncierto(compraId, medioPago, total);
            throw new GatewayTimeoutException(MSG_PASARELA_TIMEOUT);
        }
    }

    private ResultadoCompra completarTrasCobro(UUID compraId,
                                               String clienteId,
                                               String clienteEmail,
                                               MedioPagoRequest medioPago,
                                               BigDecimal total,
                                               ResultadoCobro cobro,
                                               ReservaRemota reserva,
                                               SnapshotCompra snapshot) {
        if (!cobro.aprobado()) {
            reservasClient.liberar(reserva.reservaId());
            guardarRechazada(compraId, medioPago, total);
            throw new PaymentRequiredException(MSG_PAGO_RECHAZADO);
        }

        try {
            reservasClient.confirmar(reserva.reservaId());
        } catch (GoneException | ConflictException ex) {
            pasarelaPagoService.reembolsar(cobro.transaccionId());
            guardarFallidaConReembolso(compraId, medioPago, total, cobro.transaccionId());
            throw ex;
        }

        CompraResponse aprobada = guardarAprobadaConEntradas(
                compraId, medioPago, total, cobro.transaccionId(), reserva, snapshot);
        notificarCompraConfirmada(clienteId, clienteEmail, snapshot.eventoId(), aprobada);
        return new ResultadoCompra(aprobada, false);
    }

    void notificarCompraConfirmada(String clienteId, String clienteEmail, Long eventoId, CompraResponse aprobada) {
        List<Map<String, Object>> entradas = aprobada.entradas().stream()
                .map(CompraService::entradaResumen)
                .toList();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("compraId", aprobada.compraId().toString());
        datos.put("eventoId", eventoId);
        datos.put("total", aprobada.total());
        datos.put("entradas", entradas);

        notificacionesClient.enviar(
                TipoNotificacion.COMPRA_CONFIRMADA,
                clienteId,
                clienteEmail,
                datos,
                "compra-confirmada-" + aprobada.compraId()
        );
        registrarEnRecomendaciones(clienteId, eventoId, aprobada);
    }

    /**
     * add-preferencias-recomendaciones (design.md D8): este metodo es el punto comun de los tres caminos que
     * confirman una compra (directa, reintento y reconciliacion), asi que el aviso va aca. Es best-effort: el
     * cliente nunca lanza y sus timeouts son cortos.
     */
    private void registrarEnRecomendaciones(String clienteId, Long eventoId, CompraResponse aprobada) {
        if (avisarRecomendaciones && recomendacionesClient != null) {
            recomendacionesClient.registrarCompra(aprobada.compraId().toString(), clienteId, eventoId);
        }
    }

    /**
     * Inyeccion opcional por setter para no cambiar los constructores que usan las pruebas: sin cliente (por
     * ejemplo en un @WebMvcTest) no se avisa.
     */
    @Autowired(required = false)
    public void setRecomendacionesClient(RecomendacionesClient recomendacionesClient) {
        this.recomendacionesClient = recomendacionesClient;
    }

    /** {@code ticketya.recomendaciones.avisar}: true por defecto; el perfil de prueba lo apaga. */
    @Autowired(required = false)
    public void setAvisarRecomendaciones(@Value("${ticketya.recomendaciones.avisar:true}") boolean avisar) {
        this.avisarRecomendaciones = avisar;
    }

    private static Map<String, Object> entradaResumen(EntradaResponse entrada) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("entradaId", entrada.entradaId().toString());
        item.put("sectorId", entrada.sectorId());
        item.put("precioUnitario", entrada.precioUnitario());
        if (entrada.fila() != null) {
            item.put("fila", entrada.fila());
        }
        if (entrada.numero() != null) {
            item.put("numero", entrada.numero());
        }
        return item;
    }

    private ResultadoConsultaCobro consultarEstadoSeguro(String claveCobro) {
        try {
            return pasarelaPagoService.consultarEstado(claveCobro);
        } catch (GatewayTimeoutException ex) {
            return new ResultadoConsultaCobro(EstadoConsulta.INCIERTO, null);
        }
    }

    boolean intentarAdquirirResolucion(UUID compraId) {
        return comprasEnResolucion.add(compraId);
    }

    void liberarResolucion(UUID compraId) {
        comprasEnResolucion.remove(compraId);
    }

    /** Clave de cobro idempotente hacia la pasarela, derivada del id de la compra. */
    public static String claveCobroDe(UUID compraId) {
        return "cobro-" + compraId;
    }

    private Compra registrarPendiente(String clienteId, String clienteEmail, String idempotencyKey, UUID reservaId,
                                      Long eventoId, String requestHash, BigDecimal total) {
        try {
            return transaccion.execute(status -> {
                Compra nueva = new Compra();
                nueva.setClienteId(clienteId);
                nueva.setClienteEmail(clienteEmail);
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

    void guardarPagoIncierto(UUID compraId, MedioPagoRequest medioPago, BigDecimal total) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            EstadoCompra estadoAnterior = c.getEstado();
            c.setEstado(EstadoCompra.PAGO_INCIERTO);
            actualizarOCrearPago(c, medioPago, total, EstadoPago.INCIERTO, null);
            compraRepository.saveAndFlush(c);
            if (estadoAnterior != EstadoCompra.PAGO_INCIERTO) {
                registro.registrar(Operacion.PAGO_INCIERTO, datos(
                        "compraId", c.getId(),
                        "reservaId", c.getReservaId(),
                        "eventoId", c.getEventoId(),
                        "clienteId", c.getClienteId(),
                        "monto", total));
            }
        });
    }

    void guardarRechazada(UUID compraId, MedioPagoRequest medioPago, BigDecimal total) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            EstadoCompra estadoAnterior = c.getEstado();
            c.setEstado(EstadoCompra.RECHAZADA);
            actualizarOCrearPago(c, medioPago, total, EstadoPago.RECHAZADO, null);
            compraRepository.saveAndFlush(c);
            if (estadoAnterior != EstadoCompra.RECHAZADA) {
                registro.registrar(Operacion.COMPRA_RECHAZADA, datos(
                        "compraId", c.getId(),
                        "reservaId", c.getReservaId(),
                        "eventoId", c.getEventoId(),
                        "clienteId", c.getClienteId(),
                        "monto", total));
                if (estadoAnterior == EstadoCompra.PAGO_INCIERTO) {
                    registro.registrar(Operacion.PAGO_RECONCILIADO, datos(
                            "compraId", c.getId(),
                            "reservaId", c.getReservaId(),
                            "eventoId", c.getEventoId(),
                            "clienteId", c.getClienteId(),
                            "monto", total,
                            "resultado", ResultadoReconciliacion.RECHAZADA));
                }
            }
        });
    }

    void guardarFallidaNoConcretado(UUID compraId, BigDecimal total) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            EstadoCompra estadoAnterior = c.getEstado();
            c.setEstado(EstadoCompra.FALLIDA);
            actualizarOCrearPago(c, null, total, EstadoPago.FALLIDO, null);
            compraRepository.saveAndFlush(c);
            if (estadoAnterior == EstadoCompra.PAGO_INCIERTO) {
                registro.registrar(Operacion.PAGO_RECONCILIADO, datos(
                        "compraId", c.getId(),
                        "reservaId", c.getReservaId(),
                        "eventoId", c.getEventoId(),
                        "clienteId", c.getClienteId(),
                        "monto", total,
                        "resultado", ResultadoReconciliacion.FALLIDA));
            }
        });
    }

    void guardarFallidaConReembolso(UUID compraId, MedioPagoRequest medioPago,
                                    BigDecimal total, String transaccionId) {
        transaccion.executeWithoutResult(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            EstadoCompra estadoAnterior = c.getEstado();
            c.setEstado(EstadoCompra.FALLIDA);
            actualizarOCrearPago(c, medioPago, total, EstadoPago.REEMBOLSADO, transaccionId);
            compraRepository.saveAndFlush(c);
            if (estadoAnterior != EstadoCompra.FALLIDA) {
                registro.registrar(Operacion.COMPRA_REEMBOLSADA, datos(
                        "compraId", c.getId(),
                        "reservaId", c.getReservaId(),
                        "eventoId", c.getEventoId(),
                        "clienteId", c.getClienteId(),
                        "monto", total,
                        "transaccion", transaccionId));
                if (estadoAnterior == EstadoCompra.PAGO_INCIERTO) {
                    registro.registrar(Operacion.PAGO_RECONCILIADO, datos(
                            "compraId", c.getId(),
                            "reservaId", c.getReservaId(),
                            "eventoId", c.getEventoId(),
                            "clienteId", c.getClienteId(),
                            "monto", total,
                            "transaccion", transaccionId,
                            "resultado", ResultadoReconciliacion.REEMBOLSADA));
                }
            }
        });
    }

    CompraResponse guardarAprobadaConEntradas(UUID compraId, MedioPagoRequest medioPago,
                                             BigDecimal total, String transaccionId,
                                             ReservaRemota reserva, SnapshotCompra snapshot) {
        return transaccion.execute(status -> {
            Compra c = compraRepository.findById(compraId).orElseThrow();
            EstadoCompra estadoAnterior = c.getEstado();
            c.setTotal(total);
            c.setEstado(EstadoCompra.APROBADA);
            actualizarOCrearPago(c, medioPago, total, EstadoPago.APROBADO, transaccionId);

            if (c.getEntradas().isEmpty()) {
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
            }

            Compra guardada = compraRepository.saveAndFlush(c);
            if (estadoAnterior != EstadoCompra.APROBADA) {
                registro.registrar(Operacion.COMPRA_APROBADA, datos(
                        "compraId", guardada.getId(),
                        "reservaId", guardada.getReservaId(),
                        "eventoId", guardada.getEventoId(),
                        "clienteId", guardada.getClienteId(),
                        "monto", guardada.getTotal(),
                        "entradas", guardada.getEntradas().size(),
                        "transaccion", transaccionId));
                if (estadoAnterior == EstadoCompra.PAGO_INCIERTO) {
                    registro.registrar(Operacion.PAGO_RECONCILIADO, datos(
                            "compraId", guardada.getId(),
                            "reservaId", guardada.getReservaId(),
                            "eventoId", guardada.getEventoId(),
                            "clienteId", guardada.getClienteId(),
                            "monto", guardada.getTotal(),
                            "entradas", guardada.getEntradas().size(),
                            "transaccion", transaccionId,
                            "resultado", ResultadoReconciliacion.APROBADA));
                }
            }
            return CompraResponse.from(guardada);
        });
    }

    private static void actualizarOCrearPago(Compra compra, MedioPagoRequest medioPago, BigDecimal monto,
                                             EstadoPago estado, String transaccionPasarela) {
        Pago pago = compra.getPago();
        if (pago == null) {
            pago = new Pago();
            compra.setPagoBidireccional(pago);
        }
        if (medioPago != null) {
            pago.setMedioPagoTipo(medioPago.tipo());
            pago.setUltimos4(medioPago.ultimos4());
        }
        if (monto != null) {
            pago.setMonto(monto);
        }
        pago.setEstado(estado);
        pago.setTransaccionPasarela(transaccionPasarela);
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
