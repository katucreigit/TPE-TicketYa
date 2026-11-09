package com.ticketya.reservas.service;

import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.repository.InventarioEventoRepository;
import com.ticketya.reservas.repository.projection.AvisoPendienteView;
import com.ticketya.reservas.repository.projection.EstadoAvisoView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Aviso de agotado a ms-eventos (US-07).
 * <p>
 * El aviso se manda despues del commit de la operacion que cambio los lugares libres; si falla, la operacion
 * se conserva y {@code agotado_notificado} no se actualiza. El job reintenta comparando el estado real
 * (lugares libres = 0) contra {@code agotado_notificado}. Null equivale a no agotado, que es como ms-eventos
 * deja el evento al publicarlo o reanudarlo. Mientras el inventario esta cerrado no se avisa nada, porque
 * agotado=false devolveria a PUBLICADO un evento suspendido o cancelado.
 */
@Service
public class AvisoAgotadoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoAgotadoService.class);

    private final InventarioEventoRepository inventarioEventoRepository;
    private final EventosClient eventosClient;
    private final TransactionTemplate nuevaTransaccion;

    public AvisoAgotadoService(InventarioEventoRepository inventarioEventoRepository,
                               EventosClient eventosClient,
                               PlatformTransactionManager transactionManager) {
        this.inventarioEventoRepository = inventarioEventoRepository;
        this.eventosClient = eventosClient;
        this.nuevaTransaccion = new TransactionTemplate(transactionManager);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Programa la evaluacion del aviso para despues del commit de la transaccion en curso.
     * Si la transaccion se revierte, no se avisa nada. Sin transaccion activa, evalua en el momento.
     */
    public void evaluarDespuesDelCommit(Long eventoId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // La operacion ya se confirmo: una falla del aviso no debe convertirse en error de la solicitud.
                    try {
                        evaluar(eventoId);
                    } catch (RuntimeException ex) {
                        log.warn("Fallo la evaluacion del aviso de agotado del evento {}; la reintentara el job",
                                eventoId, ex);
                    }
                }
            });
        } else {
            evaluar(eventoId);
        }
    }

    /** Compara el estado real del evento con el ultimo aviso confirmado y avisa si difieren. */
    public void evaluar(Long eventoId) {
        Optional<EstadoAvisoView> estado = nuevaTransaccion.execute(
                tx -> inventarioEventoRepository.estadoAviso(eventoId));
        if (estado == null || estado.isEmpty() || !Boolean.TRUE.equals(estado.get().getAbierto())) {
            return;
        }
        boolean agotado = estado.get().getLibres() == 0;
        boolean notificado = Boolean.TRUE.equals(estado.get().getAgotadoNotificado());
        if (agotado != notificado) {
            notificar(eventoId, agotado);
        }
    }

    /**
     * Reintenta los avisos de los eventos abiertos cuyo estado real difiere de agotado_notificado.
     * Lo invoca el job programado. Devuelve cuantos avisos llegaron bien.
     */
    public int reintentarPendientes() {
        List<AvisoPendienteView> pendientes = nuevaTransaccion.execute(
                tx -> inventarioEventoRepository.avisosPendientes());
        if (pendientes == null) {
            return 0;
        }
        int enviados = 0;
        for (AvisoPendienteView pendiente : pendientes) {
            if (notificar(pendiente.getEventoId(), pendiente.getAgotado())) {
                enviados++;
            }
        }
        return enviados;
    }

    private boolean notificar(Long eventoId, boolean agotado) {
        try {
            eventosClient.actualizarDisponibilidad(eventoId, agotado);
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar agotado={} del evento {} a ms-eventos; se reintentara: {}",
                    agotado, eventoId, ex.getMessage());
            return false;
        }
        nuevaTransaccion.executeWithoutResult(
                tx -> inventarioEventoRepository.marcarAgotadoNotificado(eventoId, agotado));
        return true;
    }
}
