package com.ticketya.ventas.service;

import com.ticketya.ventas.client.ReservasClient;
import com.ticketya.ventas.client.ReservasClient.DevolucionLugares;
import com.ticketya.ventas.dto.DevolucionRequest;
import com.ticketya.ventas.dto.DevolucionResponse;
import com.ticketya.ventas.dto.PageDevolucionResponse;
import com.ticketya.ventas.exception.BadGatewayException;
import com.ticketya.ventas.exception.BusinessRuleException;
import com.ticketya.ventas.exception.ConflictException;
import com.ticketya.ventas.exception.ForbiddenException;
import com.ticketya.ventas.exception.NotFoundException;
import com.ticketya.ventas.model.Devolucion;
import com.ticketya.ventas.model.Entrada;
import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import com.ticketya.ventas.model.enums.EstadoEntrada;
import com.ticketya.ventas.model.enums.EstadoReintegro;
import com.ticketya.ventas.model.enums.TipoReintegro;
import com.ticketya.ventas.repository.DevolucionRepository;
import com.ticketya.ventas.repository.EntradaRepository;
import com.ticketya.ventas.repository.ReintegroRepository;
import com.ticketya.ventas.service.PasarelaPagoService.ResultadoReembolso;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Devoluciones de entradas (US-18 y US-19).
 * <p>
 * Aprobar ejecuta dos pasos remotos fuera de la transaccion: liberar el lugar en ms-reservas y reintegrar el
 * importe en la pasarela mock. Cada paso deja registro al completarse ({@code lugarLiberado} y el
 * {@link Reintegro} COMPLETADO), asi que aprobar de nuevo una devolucion EN_PROCESO reintenta solo lo pendiente y
 * nunca devuelve el dinero ni el lugar dos veces.
 */
@Service
public class DevolucionService {

    private static final Set<EstadoDevolucion> EN_CURSO = Set.of(EstadoDevolucion.SOLICITADA, EstadoDevolucion.EN_PROCESO);

    private final DevolucionRepository devolucionRepository;
    private final EntradaRepository entradaRepository;
    private final ReintegroRepository reintegroRepository;
    private final ReservasClient reservasClient;
    private final PasarelaPagoService pasarelaPagoService;
    private final TransactionTemplate transaccion;
    private final Clock clock;
    private final ZoneId zonaEventos;
    private final long horasMinimas;
    private final Set<UUID> aprobacionesEnVuelo = ConcurrentHashMap.newKeySet();

    public DevolucionService(DevolucionRepository devolucionRepository,
                             EntradaRepository entradaRepository,
                             ReintegroRepository reintegroRepository,
                             ReservasClient reservasClient,
                             PasarelaPagoService pasarelaPagoService,
                             TransactionTemplate transaccion,
                             Clock clock,
                             @Value("${ticketya.devolucion.zona-horaria-eventos:America/Argentina/Buenos_Aires}")
                             ZoneId zonaEventos,
                             @Value("${ticketya.devolucion.horas-minimas-antes-del-evento:72}") long horasMinimas) {
        this.devolucionRepository = devolucionRepository;
        this.entradaRepository = entradaRepository;
        this.reintegroRepository = reintegroRepository;
        this.reservasClient = reservasClient;
        this.pasarelaPagoService = pasarelaPagoService;
        this.transaccion = transaccion;
        this.clock = clock;
        this.zonaEventos = zonaEventos;
        this.horasMinimas = horasMinimas;
    }

    // ------------------------------------------------------------------ solicitar

    /**
     * Crea la devolucion SOLICITADA y pasa la entrada a DEVOLUCION_SOLICITADA. La entrada se bloquea
     * (FOR UPDATE) para que dos solicitudes simultaneas no generen dos devoluciones en curso.
     *
     * @throws NotFoundException      (404) la entrada no existe
     * @throws ForbiddenException     (403) la entrada es de otro cliente
     * @throws ConflictException      (409 ENTRADA_NO_DEVOLVIBLE) usada, ya devuelta o con devolucion en curso
     * @throws BusinessRuleException  (422 FUERA_DE_PLAZO) faltan menos de 72 horas para el evento
     */
    public DevolucionResponse solicitar(String clienteId, DevolucionRequest request) {
        return transaccion.execute(status -> {
            Entrada entrada = entradaRepository.bloquearConCompra(request.entradaId())
                    .orElseThrow(() -> new NotFoundException("La entrada " + request.entradaId() + " no existe"));
            if (!entrada.getCompra().getClienteId().equals(clienteId)) {
                throw new ForbiddenException();
            }
            validarDevolvible(entrada);
            validarPlazo(entrada);

            Devolucion devolucion = new Devolucion();
            devolucion.setEntrada(entrada);
            devolucion.setEstado(EstadoDevolucion.SOLICITADA);
            devolucion.setMotivo(request.motivo());
            devolucion.setSolicitadaEn(LocalDateTime.now(clock));
            entrada.setEstado(EstadoEntrada.DEVOLUCION_SOLICITADA);
            entradaRepository.saveAndFlush(entrada);
            return DevolucionResponse.from(devolucionRepository.saveAndFlush(devolucion));
        });
    }

    private void validarDevolvible(Entrada entrada) {
        String motivo = switch (entrada.getEstado()) {
            case EMITIDA -> null;
            case DEVOLUCION_SOLICITADA -> "La entrada ya tiene una devolucion en curso";
            case USADA -> "La entrada ya fue usada";
            case REINTEGRO_PENDIENTE, REINTEGRADA -> "La entrada ya fue devuelta";
        };
        if (motivo == null && devolucionRepository.existsByEntradaIdAndEstadoIn(entrada.getId(), EN_CURSO)) {
            motivo = "La entrada ya tiene una devolucion en curso";
        }
        if (motivo != null) {
            throw new ConflictException("ENTRADA_NO_DEVOLVIBLE", motivo);
        }
    }

    /**
     * La fecha del evento es hora local (sin offset), por eso "ahora" se calcula en la zona de los eventos.
     * Faltar exactamente 72 horas todavia esta en plazo.
     */
    private void validarPlazo(Entrada entrada) {
        LocalDateTime ahora = LocalDateTime.now(clock.withZone(zonaEventos));
        if (ahora.plusHours(horasMinimas).isAfter(entrada.getEventoFechaHora())) {
            throw new BusinessRuleException("FUERA_DE_PLAZO",
                    "La devolucion solo se puede solicitar hasta " + horasMinimas + " horas antes del evento");
        }
    }

    // ------------------------------------------------------------------ consultar

    @Transactional(readOnly = true)
    public PageDevolucionResponse listar(EstadoDevolucion estado, int page, int size) {
        PageRequest pagina = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "solicitadaEn"));
        return PageDevolucionResponse.from(devolucionRepository.buscar(estado, pagina).map(DevolucionResponse::from));
    }

    @Transactional(readOnly = true)
    public DevolucionResponse obtener(UUID id, String solicitanteId, boolean esAdmin) {
        Devolucion devolucion = devolucionRepository.findDetalleById(id)
                .orElseThrow(() -> noEncontrada(id));
        if (!esAdmin && !devolucion.getEntrada().getCompra().getClienteId().equals(solicitanteId)) {
            throw new ForbiddenException();
        }
        return DevolucionResponse.from(devolucion);
    }

    // ------------------------------------------------------------------ aprobar

    /** Datos que necesitan los pasos remotos, leidos dentro de la transaccion que pasa a EN_PROCESO. */
    private record Pendiente(UUID reservaId, DevolucionLugares lugares, boolean lugarLiberado,
                             UUID reintegroId, boolean reintegroCompletado,
                             String transaccionPago, BigDecimal monto) {
    }

    /**
     * Pasa la devolucion a EN_PROCESO, libera el lugar en ms-reservas, reintegra el importe en la pasarela y la
     * deja REINTEGRADA con la entrada REINTEGRADA. Si un paso falla responde 502 y la devolucion queda
     * EN_PROCESO; aprobar de nuevo reintenta solo lo pendiente.
     *
     * @throws NotFoundException   (404) la devolucion no existe
     * @throws ConflictException   (409) ya esta REINTEGRADA o RECHAZADA, o hay otra aprobacion en curso
     * @throws BadGatewayException (502) fallo ms-reservas o la pasarela
     */
    public DevolucionResponse aprobar(UUID id, String adminId) {
        if (!aprobacionesEnVuelo.add(id)) {
            throw new ConflictException("CONFLICTO_CONCURRENCIA", "La devolucion ya se esta aprobando");
        }
        try {
            Pendiente pendiente = transaccion.execute(status -> iniciarAprobacion(id, adminId));

            if (!pendiente.lugarLiberado()) {
                reservasClient.devolver(pendiente.reservaId(), pendiente.lugares());
                transaccion.executeWithoutResult(status -> {
                    Devolucion d = devolucionRepository.bloquear(id).orElseThrow();
                    d.setLugarLiberado(true);
                    devolucionRepository.saveAndFlush(d);
                });
            }

            String transaccionReintegro = null;
            if (!pendiente.reintegroCompletado()) {
                transaccionReintegro = reintegrarEnPasarela(pendiente).transaccionId();
            }
            String tx = transaccionReintegro;
            return transaccion.execute(status -> completar(id, pendiente.reintegroId(), tx));
        } finally {
            aprobacionesEnVuelo.remove(id);
        }
    }

    private Pendiente iniciarAprobacion(UUID id, String adminId) {
        Devolucion devolucion = devolucionRepository.bloquear(id).orElseThrow(() -> noEncontrada(id));
        switch (devolucion.getEstado()) {
            case SOLICITADA -> {
                devolucion.setEstado(EstadoDevolucion.EN_PROCESO);
                devolucion.setResueltaPor(adminId);
                devolucion.setResueltaEn(LocalDateTime.now(clock));
            }
            case EN_PROCESO -> {
                // Reintento: se retoma lo pendiente.
            }
            case REINTEGRADA, RECHAZADA -> throw ConflictException.estadoInvalido(
                    "La devolucion " + id + " ya esta " + devolucion.getEstado());
        }

        Entrada entrada = devolucion.getEntrada();
        Reintegro reintegro = devolucion.getReintegro() != null
                ? devolucion.getReintegro()
                : reintegroRepository.findByEntradaIdAndTipo(entrada.getId(), TipoReintegro.DEVOLUCION)
                        .orElseGet(() -> nuevoReintegro(devolucion));
        if (reintegro.getDevolucion() == null) {
            reintegro.setDevolucion(devolucion);
        }
        reintegro = reintegroRepository.saveAndFlush(reintegro);
        devolucion.setReintegro(reintegro);
        devolucionRepository.saveAndFlush(devolucion);

        Pago pago = entrada.getCompra().getPago();
        DevolucionLugares lugares = entrada.getFila() != null && entrada.getNumero() != null
                ? DevolucionLugares.asiento(entrada.getFila(), entrada.getNumero())
                : DevolucionLugares.cupo(1);
        return new Pendiente(
                entrada.getCompra().getReservaId(),
                lugares,
                devolucion.isLugarLiberado(),
                reintegro.getId(),
                reintegro.getEstado() == EstadoReintegro.COMPLETADO,
                pago != null ? pago.getTransaccionPasarela() : null,
                reintegro.getMonto());
    }

    private static Reintegro nuevoReintegro(Devolucion devolucion) {
        Reintegro r = new Reintegro();
        r.setEntrada(devolucion.getEntrada());
        r.setDevolucion(devolucion);
        r.setTipo(TipoReintegro.DEVOLUCION);
        r.setMonto(devolucion.getEntrada().getPrecioUnitario());
        r.setEstado(EstadoReintegro.PENDIENTE);
        return r;
    }

    /** La clave deriva del id del reintegro, que se reutiliza en cada reintento: la pasarela no paga dos veces. */
    public static String claveReintegroDe(UUID reintegroId) {
        return "reintegro-" + reintegroId;
    }

    private ResultadoReembolso reintegrarEnPasarela(Pendiente pendiente) {
        try {
            return pasarelaPagoService.reintegrar(
                    claveReintegroDe(pendiente.reintegroId()), pendiente.transaccionPago(), pendiente.monto());
        } catch (BadGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadGatewayException("La pasarela no pudo reintegrar el pago: " + ex.getMessage());
        }
    }

    private DevolucionResponse completar(UUID id, UUID reintegroId, String transaccionReintegro) {
        Devolucion devolucion = devolucionRepository.bloquear(id).orElseThrow();
        Reintegro reintegro = reintegroRepository.findById(reintegroId).orElseThrow();
        if (reintegro.getEstado() != EstadoReintegro.COMPLETADO) {
            reintegro.setEstado(EstadoReintegro.COMPLETADO);
            reintegro.setTransaccionPasarela(transaccionReintegro);
            reintegroRepository.saveAndFlush(reintegro);
        }
        devolucion.setReintegro(reintegro);
        devolucion.setEstado(EstadoDevolucion.REINTEGRADA);
        Entrada entrada = devolucion.getEntrada();
        entrada.setEstado(EstadoEntrada.REINTEGRADA);
        entradaRepository.saveAndFlush(entrada);
        return DevolucionResponse.from(devolucionRepository.saveAndFlush(devolucion));
    }

    // ------------------------------------------------------------------ rechazar

    /**
     * Deja la devolucion RECHAZADA con el motivo y devuelve la entrada a EMITIDA. Solo se rechaza una
     * devolucion SOLICITADA: una EN_PROCESO ya pudo haber liberado el lugar o reintegrado el dinero.
     *
     * @throws NotFoundException (404) la devolucion no existe
     * @throws ConflictException (409) la devolucion no esta SOLICITADA
     */
    public DevolucionResponse rechazar(UUID id, String adminId, String motivo) {
        return transaccion.execute(status -> {
            Devolucion devolucion = devolucionRepository.bloquear(id).orElseThrow(() -> noEncontrada(id));
            if (devolucion.getEstado() != EstadoDevolucion.SOLICITADA) {
                throw ConflictException.estadoInvalido(
                        "La devolucion " + id + " esta " + devolucion.getEstado() + " y no se puede rechazar");
            }
            devolucion.setEstado(EstadoDevolucion.RECHAZADA);
            devolucion.setMotivoRechazo(motivo.trim());
            devolucion.setResueltaPor(adminId);
            devolucion.setResueltaEn(LocalDateTime.now(clock));
            Entrada entrada = devolucion.getEntrada();
            entrada.setEstado(EstadoEntrada.EMITIDA);
            entradaRepository.saveAndFlush(entrada);
            return DevolucionResponse.from(devolucionRepository.saveAndFlush(devolucion));
        });
    }

    private static NotFoundException noEncontrada(UUID id) {
        return new NotFoundException("La devolucion " + id + " no existe");
    }
}
