package com.ticketya.fila.service;

import com.ticketya.fila.repository.FilaRepository;
import com.ticketya.fila.repository.FilaRepository.Tanda;
import com.ticketya.fila.trazabilidad.Correlacion;
import com.ticketya.fila.trazabilidad.RegistroOperaciones;
import com.ticketya.fila.trazabilidad.RegistroOperaciones.Operacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static com.ticketya.fila.trazabilidad.RegistroOperaciones.datos;

/**
 * Una corrida del proceso de la fila (design.md D7): por cada fila activa quita los turnos vencidos y habilita la
 * tanda si llego su momento. Puede correr a la vez en varias instancias: la exclusion la dan los scripts (D4 y D5),
 * y cada instancia registra solo lo que su script devolvio, asi que nada se registra dos veces.
 */
@Component
public class ProcesadorFila {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorFila.class);
    private static final int MAXIMO_VENCIDOS_POR_LLAMADA = 500;

    private final FilaRepository repository;
    private final RegistroOperaciones registro;
    private final Clock clock;

    public ProcesadorFila(FilaRepository repository, RegistroOperaciones registro, Clock clock) {
        this.repository = repository;
        this.registro = registro;
        this.clock = clock;
    }

    /**
     * Corre una vez sobre todas las filas activas, con un correlationId propio. Nunca lanza: si Redis no esta
     * disponible registra una advertencia y la corrida siguiente vuelve a intentar.
     */
    public void procesar() {
        Correlacion.enProceso(() -> {
            Set<String> activos;
            try {
                activos = repository.eventosActivos();
            } catch (RuntimeException e) {
                log.warn("No se pudieron leer las filas activas: {}", e.getMessage());
                return;
            }
            for (String id : activos) {
                procesarEvento(Long.parseLong(id));
            }
        });
    }

    private void procesarEvento(long eventoId) {
        try {
            if (!repository.existe(eventoId)) {
                // La fila vencio por TTL o se borro: deja de recorrerse.
                repository.quitarDeActivas(eventoId);
                return;
            }
            long ahora = clock.millis();
            vencer(eventoId, ahora);
            Tanda tanda = repository.habilitarTanda(eventoId, ahora);
            if (tanda != null) {
                String hasta = FilaService.fecha(tanda.habilitadoHasta()).toString();
                for (String cliente : tanda.clientes()) {
                    registro.registrar(Operacion.CLIENTE_HABILITADO, datos(
                            "eventoId", eventoId, "clienteId", cliente, "habilitadoHasta", hasta));
                }
            }
        } catch (RuntimeException e) {
            // Por ejemplo, Redis no disponible: la proxima corrida vuelve a intentar.
            log.warn("No se pudo procesar la fila del evento {}: {}", eventoId, e.getMessage());
        }
    }

    private void vencer(long eventoId, long ahora) {
        List<String> vencidos;
        do {
            vencidos = repository.vencerTurnos(eventoId, ahora, MAXIMO_VENCIDOS_POR_LLAMADA);
            for (String cliente : vencidos) {
                registro.registrar(Operacion.TURNO_VENCIDO, datos("eventoId", eventoId, "clienteId", cliente));
            }
        } while (vencidos.size() == MAXIMO_VENCIDOS_POR_LLAMADA);
    }
}
