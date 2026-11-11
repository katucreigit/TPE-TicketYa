package com.ticketya.ventas.dto;

import com.ticketya.ventas.model.Devolucion;
import com.ticketya.ventas.model.Reintegro;
import com.ticketya.ventas.model.enums.EstadoDevolucion;
import com.ticketya.ventas.model.enums.EstadoReintegro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Schema {@code Devolucion} de openapi.yaml. El monto es el precio pagado por la entrada. */
public record DevolucionResponse(
        UUID devolucionId,
        UUID entradaId,
        EstadoDevolucion estado,
        BigDecimal monto,
        String motivo,
        String motivoRechazo,
        LocalDateTime solicitadaEn,
        ReintegroResponse reintegro
) {

    public record ReintegroResponse(BigDecimal monto, EstadoReintegro estado) {
    }

    public static DevolucionResponse from(Devolucion d) {
        Reintegro r = d.getReintegro();
        return new DevolucionResponse(
                d.getId(),
                d.getEntrada().getId(),
                d.getEstado(),
                d.getEntrada().getPrecioUnitario(),
                d.getMotivo(),
                d.getMotivoRechazo(),
                d.getSolicitadaEn(),
                r == null ? null : new ReintegroResponse(r.getMonto(), r.getEstado())
        );
    }
}
