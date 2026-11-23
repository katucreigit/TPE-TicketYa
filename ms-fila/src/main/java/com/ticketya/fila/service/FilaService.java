package com.ticketya.fila.service;

import com.ticketya.fila.config.FilaProperties;
import com.ticketya.fila.dto.ActivarFilaRequest;
import com.ticketya.fila.dto.FilaConfiguracion;
import com.ticketya.fila.dto.FilaEstadoCliente;
import com.ticketya.fila.dto.FilaEstadoInterno;
import com.ticketya.fila.exception.ApiException;
import com.ticketya.fila.repository.FilaRepository;
import com.ticketya.fila.repository.FilaRepository.Lectura;
import com.ticketya.fila.repository.FilaRepository.ResultadoActivar;
import com.ticketya.fila.repository.FilaRepository.ResultadoDesactivar;
import com.ticketya.fila.repository.FilaRepository.ResultadoIngreso;
import com.ticketya.fila.trazabilidad.RegistroOperaciones;
import com.ticketya.fila.trazabilidad.RegistroOperaciones.Operacion;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.ticketya.fila.trazabilidad.RegistroOperaciones.datos;

/**
 * Activacion, ingreso y consultas de la fila virtual. La consistencia la dan los scripts de {@link FilaRepository};
 * aca solo se arma la respuesta y se registran las operaciones criticas segun lo que devolvio cada script.
 * <p>
 * La ventana de un habilitado se compara con la hora en el momento de cada consulta (design.md D5): un turno vencido
 * deja de habilitar aunque el proceso periodico todavia no lo haya quitado.
 */
@Service
public class FilaService {

    private final FilaRepository repository;
    private final FilaProperties properties;
    private final RegistroOperaciones registro;
    private final Clock clock;

    public FilaService(FilaRepository repository, FilaProperties properties, RegistroOperaciones registro, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.registro = registro;
        this.clock = clock;
    }

    public FilaConfiguracion activar(Long eventoId, ActivarFilaRequest request) {
        int tamano = request != null && request.tamanoTanda() != null
                ? request.tamanoTanda() : properties.getTamanoTandaDefault();
        int segundos = request != null && request.segundosEntreTandas() != null
                ? request.segundosEntreTandas() : properties.getSegundosEntreTandasDefault();
        ResultadoActivar r = repository.activar(eventoId, tamano, segundos, properties.getMinutosVentana(),
                ahora(), properties.getTtlHoras() * 3600L);
        registro.registrar(Operacion.FILA_ACTIVADA, datos(
                "eventoId", eventoId,
                "tamanoTanda", r.tamanoTanda(),
                "segundosEntreTandas", r.segundosEntreTandas(),
                "reactivacion", r.estabaActiva()));
        return new FilaConfiguracion(eventoId, true, r.tamanoTanda(), r.segundosEntreTandas(), r.minutosVentana(),
                fecha(r.proximaTandaEn()), r.enEspera(), r.habilitados());
    }

    public void desactivar(Long eventoId) {
        ResultadoDesactivar r = repository.desactivar(eventoId);
        if (r.estabaActiva()) {
            registro.registrar(Operacion.FILA_DESACTIVADA, datos(
                    "eventoId", eventoId,
                    "enEspera", r.enEspera(),
                    "habilitados", r.habilitados()));
        }
    }

    public FilaEstadoCliente ingresar(Long eventoId, String clienteId) {
        ResultadoIngreso r = repository.ingresar(eventoId, clienteId, ahora());
        if (!r.activa()) {
            throw ApiException.filaNoActiva(eventoId);
        }
        if (r.vencido()) {
            registro.registrar(Operacion.TURNO_VENCIDO, datos("eventoId", eventoId, "clienteId", clienteId));
        }
        if (r.habilitadoHasta() != null) {
            return FilaEstadoCliente.habilitado(eventoId, fecha(r.habilitadoHasta()));
        }
        if (r.nuevo()) {
            registro.registrar(Operacion.INGRESO_FILA, datos(
                    "eventoId", eventoId, "clienteId", clienteId, "posicion", r.posicion()));
        }
        return FilaEstadoCliente.enEspera(eventoId, r.posicion());
    }

    public FilaEstadoCliente posicion(Long eventoId, String clienteId) {
        Lectura l = repository.leer(eventoId, clienteId);
        if (!l.activa()) {
            return FilaEstadoCliente.sinFila(eventoId);
        }
        if (vigente(l.habilitadoHasta())) {
            return FilaEstadoCliente.habilitado(eventoId, fecha(l.habilitadoHasta()));
        }
        if (l.posicion() != null) {
            return FilaEstadoCliente.enEspera(eventoId, l.posicion());
        }
        return FilaEstadoCliente.fueraDeFila(eventoId);
    }

    public FilaEstadoInterno estadoInterno(Long eventoId, String clienteId) {
        Lectura l = repository.leer(eventoId, clienteId);
        if (!l.activa()) {
            return new FilaEstadoInterno(false, true);
        }
        return new FilaEstadoInterno(true, vigente(l.habilitadoHasta()));
    }

    private boolean vigente(Long habilitadoHasta) {
        return habilitadoHasta != null && habilitadoHasta > ahora();
    }

    private long ahora() {
        return clock.millis();
    }

    static LocalDateTime fecha(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
    }
}
