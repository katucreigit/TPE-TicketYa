package com.ticketya.eventos.dto;

import java.math.BigDecimal;
import java.util.List;

public record LugarResponse(
    Long id,
    String nombre,
    String direccion,
    String ciudad,
    BigDecimal latitud,
    BigDecimal longitud,
    List<SectorResponse> sectores
) {}
