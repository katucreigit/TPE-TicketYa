package com.ticketya.reservas.service;

import com.ticketya.reservas.dto.AsientoRef;
import com.ticketya.reservas.dto.ReservaRequest;
import com.ticketya.reservas.exception.CodigoError;
import com.ticketya.reservas.model.enums.TipoSector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reglas de reserva que no dependen de la base: forma del pedido, tipo de sector y limite por cliente.
 * Funciones puras para poder probarlas con tests unitarios; la concurrencia sigue resuelta en la base.
 */
final class ReglasReserva {

    private ReglasReserva() {
    }

    /** Valida la forma del pedido (400). Devuelve true si trae asientos y false si trae cantidad. */
    static boolean validarPedido(ReservaRequest request) {
        boolean conAsientos = request.asientos() != null && !request.asientos().isEmpty();
        boolean conCantidad = request.cantidad() != null;
        if (conAsientos == conCantidad) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("Debe enviarse asientos (sector numerado) o cantidad (sector no numerado), no ambos"));
        }
        if (conCantidad) {
            validarCantidad(request.cantidad());
        } else {
            validarSinRepetidos(request.asientos(), "asientos");
        }
        return conAsientos;
    }

    /** Asientos solo en sectores numerados y cantidad solo en no numerados (400). */
    static void validarTipoDeSector(TipoSector tipo, boolean conAsientos) {
        if (tipo == TipoSector.NUMERADO && !conAsientos) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("El sector es numerado: se deben enviar asientos, no cantidad"));
        }
        if (tipo == TipoSector.NO_NUMERADO && conAsientos) {
            throw CodigoError.validacion("Datos invalidos",
                    List.of("El sector no es numerado: se debe enviar cantidad, no asientos"));
        }
    }

    static void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw CodigoError.validacion("Datos invalidos", List.of("cantidad debe ser mayor a 0"));
        }
    }

    /** Un mismo asiento no puede aparecer dos veces en el pedido (400). */
    static void validarSinRepetidos(List<AsientoRef> asientos, String campo) {
        Set<AsientoRef> vistos = new HashSet<>();
        List<String> repetidos = asientos.stream()
                .filter(a -> !vistos.add(a))
                .map(a -> campo + " repite el asiento " + CodigoError.asiento(a.fila(), a.numero()))
                .distinct()
                .toList();
        if (!repetidos.isEmpty()) {
            throw CodigoError.validacion("Datos invalidos", repetidos);
        }
    }

    /** Lo ya contado mas lo pedido no puede superar maxPorCliente (422 LIMITE_POR_CLIENTE). */
    static void verificarLimite(long contadas, int pedidas, int maxPorCliente) {
        if (contadas + pedidas > maxPorCliente) {
            throw CodigoError.limitePorCliente(maxPorCliente, contadas);
        }
    }

    /** Orden en que se ejecutan los UPDATE de asientos: fila y numero ascendentes, para evitar deadlocks. */
    static List<AsientoRef> ordenados(List<AsientoRef> asientos) {
        return asientos.stream().sorted(AsientoRef.POR_FILA_Y_NUMERO).toList();
    }
}
