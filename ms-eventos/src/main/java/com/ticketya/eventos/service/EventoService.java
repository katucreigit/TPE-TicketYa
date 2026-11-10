package com.ticketya.eventos.service;

import com.ticketya.eventos.dto.EventoRequest;
import com.ticketya.eventos.dto.EventoResponse;
import com.ticketya.eventos.dto.ParticipanteResponse;
import com.ticketya.eventos.dto.PrecioSectorRequest;
import com.ticketya.eventos.dto.SectorEventoResponse;
import com.ticketya.eventos.exception.ConflictException;
import com.ticketya.eventos.exception.BadRequestException;
import com.ticketya.eventos.dto.EventoResumenResponse;
import com.ticketya.eventos.dto.PageEventoResumenResponse;
import com.ticketya.eventos.model.enums.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

import org.springframework.web.client.HttpClientErrorException;

import com.ticketya.eventos.exception.ForbiddenException;
import com.ticketya.eventos.exception.NotFoundException;
import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.EventoSector;
import com.ticketya.eventos.model.Lugar;
import com.ticketya.eventos.model.Organizador;
import com.ticketya.eventos.model.Participante;
import com.ticketya.eventos.model.Sector;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.EventoRepository;
import com.ticketya.eventos.repository.EventoSectorRepository;
import com.ticketya.eventos.repository.LugarRepository;
import com.ticketya.eventos.repository.OrganizadorRepository;
import com.ticketya.eventos.repository.ParticipanteRepository;
import com.ticketya.eventos.repository.SectorRepository;

import com.ticketya.eventos.client.ReservasClient;
import com.ticketya.eventos.client.VentasClient;
import com.ticketya.eventos.dto.InventarioRequest;
import com.ticketya.eventos.model.enums.TipoSector;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;
import com.ticketya.eventos.exception.BusinessRuleException;
import com.ticketya.eventos.exception.BadGatewayException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final OrganizadorRepository organizadorRepository;
    private final ParticipanteRepository participanteRepository;
    private final EventoSectorRepository eventoSectorRepository;
    private final SectorRepository sectorRepository;
    private final ReservasClient reservasClient;
    private final VentasClient ventasClient;

    public EventoService(
            EventoRepository eventoRepository,
            LugarRepository lugarRepository,
            OrganizadorRepository organizadorRepository,
            ParticipanteRepository participanteRepository,
            EventoSectorRepository eventoSectorRepository,
SectorRepository sectorRepository,
ReservasClient reservasClient,
            VentasClient ventasClient) {

        this.eventoRepository = eventoRepository;
        this.lugarRepository = lugarRepository;
        this.organizadorRepository = organizadorRepository;
        this.participanteRepository = participanteRepository;
        this.eventoSectorRepository = eventoSectorRepository;
        this.sectorRepository = sectorRepository;
        this.reservasClient = reservasClient;
        this.ventasClient = ventasClient;
    }

    @Transactional
    public EventoResponse crear(Long organizadorId, EventoRequest request) {

        Organizador organizador = organizadorRepository.findById(organizadorId)
                .orElseThrow(() -> new NotFoundException(
                        "ORGANIZADOR_NO_ENCONTRADO",
                        "No se encontró el organizador con ID " + organizadorId
                ));

        Lugar lugar = lugarRepository.findById(request.lugarId())
                .orElseThrow(() -> new NotFoundException(
                        "LUGAR_NO_ENCONTRADO",
                        "No se encontró el lugar con ID " + request.lugarId()
                ));

        Evento evento = new Evento();

        evento.setNombre(request.nombre().trim());
        evento.setDescripcion(request.descripcion());
        evento.setTipo(request.tipo());
        evento.setOrganizador(organizador);
        evento.setLugar(lugar);
        evento.setFechaHora(request.fechaHora());
        evento.setMaxEntradasPorCliente(
                request.maxEntradasPorCliente() != null
                        ? request.maxEntradasPorCliente() : 4
        );
        evento.setAltaDemanda(
                request.altaDemanda() != null && request.altaDemanda()
        );

        if (request.participantes() != null) {
            for (var participanteRequest : request.participantes()) {
                Participante participante = new Participante();
                participante.setNombre(participanteRequest.nombre().trim());
                participante.setTipo(participanteRequest.tipo());

                Participante guardado = participanteRepository.save(participante);
                evento.getParticipantes().add(guardado);
            }
        }

        Evento guardado = eventoRepository.save(evento);

        return convertirRespuesta(guardado);
    }

    @Transactional
    public EventoResponse actualizar(
            Long eventoId,
            Long organizadorId,
            EventoRequest request) {

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                ));

        if (!evento.getOrganizador().getId().equals(organizadorId)) {
            throw new ForbiddenException(
                    "Solo el organizador dueño puede editar este evento."
            );
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException(
                    "EVENTO_NO_EDITABLE",
                    "Solo se pueden editar eventos en BORRADOR."
            );
        }

        Lugar lugar = lugarRepository.findById(request.lugarId())
                .orElseThrow(() -> new NotFoundException(
                        "LUGAR_NO_ENCONTRADO",
                        "No se encontró el lugar con ID " + request.lugarId()
                ));

        evento.setNombre(request.nombre().trim());
        evento.setDescripcion(request.descripcion());
        evento.setTipo(request.tipo());
        evento.setLugar(lugar);
        evento.setFechaHora(request.fechaHora());

        if (request.maxEntradasPorCliente() != null) {
            evento.setMaxEntradasPorCliente(request.maxEntradasPorCliente());
        }

        if (request.altaDemanda() != null) {
            evento.setAltaDemanda(request.altaDemanda());
        }

        if (request.participantes() != null) {
            evento.getParticipantes().clear();

            for (var participanteRequest : request.participantes()) {
                Participante participante = new Participante();
                participante.setNombre(participanteRequest.nombre().trim());
                participante.setTipo(participanteRequest.tipo());

                Participante guardado = participanteRepository.save(participante);
                evento.getParticipantes().add(guardado);
            }
        }

        Evento actualizado = eventoRepository.save(evento);

        return convertirRespuesta(actualizado);
    }

    @Transactional
    public List<SectorEventoResponse> configurarPrecios(
            Long eventoId,
            Long organizadorId,
            List<PrecioSectorRequest> preciosRequest) {

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                ));

        if (!evento.getOrganizador().getId().equals(organizadorId)) {
            throw new ForbiddenException(
                    "Solo el organizador dueño puede configurar los precios."
            );
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException(
                    "EVENTO_NO_EDITABLE",
                    "Solo se pueden configurar precios en eventos BORRADOR."
            );
        }

        Set<Long> sectoresProcesados = new HashSet<>();
        List<EventoSector> nuevosPrecios = new ArrayList<>();

        for (PrecioSectorRequest precioRequest : preciosRequest) {

            if (!sectoresProcesados.add(precioRequest.sectorId())) {
                throw new ConflictException(
                        "SECTOR_REPETIDO",
                        "No se puede repetir un sector en la lista de precios."
                );
            }

            Sector sector = sectorRepository.findById(precioRequest.sectorId())
                    .orElseThrow(() -> new NotFoundException(
                            "SECTOR_NO_ENCONTRADO",
                            "No se encontró el sector con ID " + precioRequest.sectorId()
                    ));

            if (!sector.getLugar().getId().equals(evento.getLugar().getId())) {
                throw new ConflictException(
                        "SECTOR_LUGAR_INVALIDO",
                        "El sector no pertenece al lugar del evento."
                );
            }

            EventoSector eventoSector = new EventoSector();
            eventoSector.setEvento(evento);
            eventoSector.setSector(sector);
            eventoSector.setPrecio(precioRequest.precio());

            nuevosPrecios.add(eventoSector);
        }

        evento.getSectores().clear();
        evento.getSectores().addAll(nuevosPrecios);

        eventoRepository.save(evento);

        return convertirPrecios(nuevosPrecios);
    }
    @Transactional
public EventoResponse publicar(Long eventoId, Long organizadorId) {

    Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NotFoundException(
                    "EVENTO_NO_ENCONTRADO",
                    "No se encontró el evento con ID " + eventoId
            ));

    if (!evento.getOrganizador().getId().equals(organizadorId)) {
        throw new ForbiddenException(
                "Solo el organizador dueño puede publicar este evento."
        );
    }

    if (evento.getEstado() != EstadoEvento.BORRADOR) {
        throw new ConflictException(
                "ESTADO_INVALIDO",
                "El evento no está en estado BORRADOR."
        );
    }

    if (evento.getSectores().isEmpty()) {
        throw new BusinessRuleException(
                "SIN_PRECIOS",
                "Debe configurar al menos un sector con precio."
        );
    }

    List<InventarioRequest.Sector> sectores = evento.getSectores()
            .stream()
            .map(es -> {
                Sector sector = es.getSector();

                return new InventarioRequest.Sector(
                        sector.getId(),
                        sector.getTipo(),
                        sector.getFilas(),
                        sector.getAsientosPorFila(),
                        sector.getCapacidad()
                );
            })
            .toList();

    InventarioRequest inventarioRequest = new InventarioRequest(
            evento.getId(),
            evento.getMaxEntradasPorCliente(),
            sectores
    );

    try {
        reservasClient.crearInventario(inventarioRequest);

    } catch (HttpClientErrorException.Conflict e) {
        // El inventario ya existe: se considera una publicación válida.

    } catch (RestClientException e) {
        throw new BadGatewayException(
                "INVENTARIO_NO_DISPONIBLE",
                "No se pudo inicializar el inventario del evento."
        );
    }

    evento.setEstado(EstadoEvento.PUBLICADO);

    Evento publicado = eventoRepository.save(evento);

    return convertirRespuesta(publicado);

}
@Transactional
public EventoResponse suspender(Long eventoId, String rol) {

    if (!"ADMIN".equalsIgnoreCase(rol)) {
        throw new ForbiddenException("Solo un ADMIN puede suspender eventos.");
    }

    Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NotFoundException(
                    "EVENTO_NO_ENCONTRADO",
                    "No se encontró el evento con ID " + eventoId
            ));

    if (evento.getEstado() != EstadoEvento.PUBLICADO
            && evento.getEstado() != EstadoEvento.AGOTADO) {
        throw new ConflictException(
                "ESTADO_INVALIDO",
                "Solo se pueden suspender eventos PUBLICADOS o AGOTADOS."
        );
    }

    try {
        reservasClient.cerrarInventario(eventoId);
    } catch (RestClientException e) {
        throw new BadGatewayException(
                "INVENTARIO_NO_DISPONIBLE",
                "No se pudo cerrar el inventario del evento."
        );
    }

    evento.setEstado(EstadoEvento.SUSPENDIDO);

    Evento suspendido = eventoRepository.save(evento);

    return convertirRespuesta(suspendido);
}
@Transactional
public EventoResponse reanudar(Long eventoId, String rol) {

    if (!"ADMIN".equalsIgnoreCase(rol)) {
        throw new ForbiddenException("Solo un ADMIN puede reanudar eventos.");
    }

    Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NotFoundException(
                    "EVENTO_NO_ENCONTRADO",
                    "No se encontró el evento con ID " + eventoId
            ));

    if (evento.getEstado() != EstadoEvento.SUSPENDIDO) {
        throw new ConflictException(
                "ESTADO_INVALIDO",
                "Solo se pueden reanudar eventos SUSPENDIDOS."
        );
    }

    try {
        reservasClient.abrirInventario(eventoId);
    } catch (RestClientException e) {
        throw new BadGatewayException(
                "INVENTARIO_NO_DISPONIBLE",
                "No se pudo reabrir el inventario del evento."
        );
    }

    evento.setEstado(EstadoEvento.PUBLICADO);

    Evento reanudado = eventoRepository.save(evento);

    return convertirRespuesta(reanudado);
}
    @Transactional
    public EventoResponse cancelar(Long eventoId, String rol, String motivo) {

        if (!"ADMIN".equalsIgnoreCase(rol)) {
            throw new ForbiddenException("Solo un ADMIN puede cancelar eventos.");
        }

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                ));

        if (evento.getEstado() == EstadoEvento.FINALIZADO
                || evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ConflictException(
                    "ESTADO_INVALIDO",
                    "No se puede cancelar un evento finalizado o ya cancelado."
            );
        }

       if (evento.getEstado() == EstadoEvento.PUBLICADO
        || evento.getEstado() == EstadoEvento.AGOTADO) {
    try {
        reservasClient.cerrarInventario(eventoId);
    } catch (RestClientException e) {
        throw new BadGatewayException(
                "INVENTARIO_NO_DISPONIBLE",
                "No se pudo cerrar el inventario del evento."
        );
    }
}
        try {
            ventasClient.procesarCancelacion(eventoId);
        } catch (RestClientException e) {
            throw new BadGatewayException(
                    "REINTEGRO_NO_DISPONIBLE",
                    "No se pudieron procesar los reintegros del evento."
            );
        }

        evento.setMotivoCancelacion(motivo.trim());
        evento.setEstado(EstadoEvento.CANCELADO);

        Evento cancelado = eventoRepository.save(evento);

        return convertirRespuesta(cancelado);
    }

    @Transactional(readOnly = true)
    public PageEventoResumenResponse buscarEventos(
            TipoEvento tipo,
            Long organizadorId,
            String participante,
            String ciudad,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String q,
            String orden,
            String dir,
            int page,
            int size) {

        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new BadRequestException(
                    "FECHAS_INVALIDAS",
                    "La fechaDesde no puede ser posterior a fechaHasta."
            );
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException(
                    "PAGINACION_INVALIDA",
                    "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100."
            );
        }

        orden = (orden == null || orden.isBlank()) ? "fecha" : orden.toLowerCase();
        dir = (dir == null || dir.isBlank()) ? "asc" : dir.toLowerCase();

        if (!List.of("fecha", "precio", "relevancia").contains(orden)) {
            throw new BadRequestException("ORDEN_INVALIDO", "El orden indicado no es válido.");
        }

        if (!List.of("asc", "desc").contains(dir)) {
            throw new BadRequestException("DIRECCION_INVALIDA", "La dirección debe ser asc o desc.");
        }

        final String textoBusqueda = q == null ? "" : q.trim().toLowerCase();
        final String nombreParticipante = participante == null
                ? "" : participante.trim().toLowerCase();
        final String ciudadBusqueda = ciudad == null ? "" : ciudad.trim();

        List<Evento> eventos = eventoRepository.findAll().stream()
                .filter(e -> e.getEstado() == EstadoEvento.PUBLICADO
                        || e.getEstado() == EstadoEvento.AGOTADO)
                .filter(e -> tipo == null || e.getTipo() == tipo)
                .filter(e -> organizadorId == null
                        || e.getOrganizador().getId().equals(organizadorId))
                .filter(e -> ciudadBusqueda.isBlank()
                        || e.getLugar().getCiudad().equalsIgnoreCase(ciudadBusqueda))
                .filter(e -> fechaDesde == null
                        || !e.getFechaHora().toLocalDate().isBefore(fechaDesde))
                .filter(e -> fechaHasta == null
                        || !e.getFechaHora().toLocalDate().isAfter(fechaHasta))
                .filter(e -> nombreParticipante.isBlank()
                        || e.getParticipantes().stream().anyMatch(p ->
                                p.getNombre().toLowerCase().contains(nombreParticipante)))
                .filter(e -> textoBusqueda.isBlank()
                        || coincideTexto(e, textoBusqueda))
                .toList();

        java.util.Comparator<Evento> comparador;

        switch (orden) {
            case "precio" -> comparador = java.util.Comparator.comparing(
                    this::obtenerPrecioMinimo,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
            ).thenComparing(Evento::getFechaHora)
             .thenComparingLong(Evento::getId);
            case "relevancia" -> comparador = java.util.Comparator
                    .comparingInt((Evento e) -> puntuacionRelevancia(e, textoBusqueda))
                    .thenComparing(Evento::getFechaHora)
                    .thenComparingLong(Evento::getId);
            default -> comparador = java.util.Comparator.comparing(Evento::getFechaHora)
                    .thenComparingLong(Evento::getId);
        }

        if ("desc".equals(dir)) {
            comparador = comparador.reversed();
        }

        List<Evento> ordenados = eventos.stream()
                .sorted(comparador)
                .toList();

        long totalElementos = ordenados.size();
        int totalPaginas = (int) Math.ceil((double) totalElementos / size);

        long inicioLong = (long) page * size;
        int inicio = (int) Math.min(inicioLong, totalElementos);
        int fin = (int) Math.min(inicioLong + size, totalElementos);

        List<EventoResumenResponse> contenido = ordenados.subList(inicio, fin)
                .stream()
                .map(e -> new EventoResumenResponse(
                        e.getId(),
                        e.getNombre(),
                        e.getTipo(),
                        e.getEstado(),
                        e.getLugar().getCiudad(),
                        e.getFechaHora(),
                        obtenerPrecioMinimo(e),
                        e.getParticipantes().stream()
                                .map(Participante::getNombre)
                                .toList()
                ))
                .toList();

        return new PageEventoResumenResponse(
                contenido,
                page,
                size,
                totalElementos,
                totalPaginas
        );
    }

    private BigDecimal obtenerPrecioMinimo(Evento evento) {
        return evento.getSectores().stream()
                .map(EventoSector::getPrecio)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private boolean coincideTexto(Evento evento, String texto) {
        boolean coincideNombre = evento.getNombre().toLowerCase().contains(texto);

        boolean coincideDescripcion = evento.getDescripcion() != null
                && evento.getDescripcion().toLowerCase().contains(texto);

        boolean coincideTipo = evento.getTipo() != null
                && evento.getTipo().name().toLowerCase().contains(texto);

        boolean coincideParticipante = evento.getParticipantes().stream()
                .anyMatch(p -> p.getNombre().toLowerCase().contains(texto));

        return coincideNombre || coincideDescripcion || coincideTipo || coincideParticipante;
    }

    private int puntuacionRelevancia(Evento evento, String texto) {
        if (texto.isBlank()) {
            return 0;
        }

        String nombre = evento.getNombre().toLowerCase();

        if (nombre.equals(texto)) return 0;
        if (nombre.startsWith(texto)) return 1;
        if (nombre.contains(texto)) return 2;

        boolean coincideParticipante = evento.getParticipantes().stream()
                .anyMatch(p -> p.getNombre().toLowerCase().contains(texto));

        if (coincideParticipante) return 3;

        if (evento.getDescripcion() != null
                && evento.getDescripcion().toLowerCase().contains(texto)) {
            return 4;
        }

        return 5;
    }

    @Transactional(readOnly = true)
    public EventoResponse obtenerEvento(Long eventoId, Long usuarioId, String rol) {

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                ));

        if (evento.getEstado() == EstadoEvento.BORRADOR) {
            boolean esOrganizador = evento.getOrganizador().getId().equals(usuarioId);
            boolean esAdmin = "ADMIN".equalsIgnoreCase(rol);

            if (!esOrganizador && !esAdmin) {
                throw new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                );
            }
        }

        return convertirRespuesta(evento);
    }

    @Transactional(readOnly = true)
    public List<SectorEventoResponse> obtenerSectores(Long eventoId) {

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "EVENTO_NO_ENCONTRADO",
                        "No se encontró el evento con ID " + eventoId
                ));

        return convertirPrecios(evento.getSectores());
    }

    private List<SectorEventoResponse> convertirPrecios(List<EventoSector> precios) {

        return precios.stream()
                .map(es -> new SectorEventoResponse(
                        es.getSector().getId(),
                        es.getSector().getNombre(),
                        es.getSector().getTipo(),
                        es.getPrecio(),
                        es.getSector().getFilas(),
                        es.getSector().getAsientosPorFila(),
                        es.getSector().getCapacidad()
                ))
                .toList();
    }

    private EventoResponse convertirRespuesta(Evento evento) {

        List<ParticipanteResponse> participantes = evento.getParticipantes()
                .stream()
                .map(p -> new ParticipanteResponse(
                        p.getId(),
                        p.getNombre(),
                        p.getTipo()
                ))
                .toList();

        return new EventoResponse(
                evento.getId(),
                evento.getNombre(),
                evento.getDescripcion(),
                evento.getTipo(),
                evento.getEstado(),
                evento.getOrganizador().getId(),
                evento.getLugar().getId(),
                evento.getFechaHora(),
                participantes,
                evento.getMaxEntradasPorCliente(),
                evento.isAltaDemanda(),
                evento.getMotivoCancelacion()
        );
    }
    @Transactional
public void actualizarDisponibilidad(Long eventoId, boolean agotado) {

    Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NotFoundException(
                    "EVENTO_NO_ENCONTRADO",
                    "No se encontró el evento con ID " + eventoId
            ));

    if (evento.getEstado() != EstadoEvento.PUBLICADO
            && evento.getEstado() != EstadoEvento.AGOTADO) {
        return;
    }

    evento.setEstado(
            agotado ? EstadoEvento.AGOTADO : EstadoEvento.PUBLICADO
    );

    eventoRepository.save(evento);
}
}
