package com.ticketya.ventas.dto;

import com.ticketya.ventas.model.Pago;
import com.ticketya.ventas.model.enums.TipoMedioPago;

/** Objeto {@code medioPago} dentro del schema {@code Compra} de openapi.yaml (nunca expone el token). */
public record MedioPagoResponse(
        TipoMedioPago tipo,
        String ultimos4
) {
    public static MedioPagoResponse from(Pago pago) {
        if (pago == null) {
            return null;
        }
        return new MedioPagoResponse(pago.getMedioPagoTipo(), pago.getUltimos4());
    }
}

