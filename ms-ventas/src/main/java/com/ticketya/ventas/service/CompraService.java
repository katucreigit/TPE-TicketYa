package com.ticketya.ventas.service;

import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.CompraResponse;
import org.springframework.stereotype.Service;

/**
 * Servicio de compras. La orquestacion de la saga, el cobro y la emision de entradas se completan en 3.2-3.4.
 */
@Service
public class CompraService {

    /**
     * Resultado de procesar POST /compras: {@code repeticion=false} responde 201 Created y
     * {@code repeticion=true} responde 200 OK (misma Idempotency-Key ya procesada).
     */
    public record ResultadoCompra(CompraResponse compra, boolean repeticion) {
    }

    public ResultadoCompra crear(String clienteId, String idempotencyKey, CompraRequest request) {
        throw new UnsupportedOperationException("Saga de compra pendiente (tareas 3.2-3.4)");
    }
}

