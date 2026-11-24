package com.ticketya.reservas.config;

import com.ticketya.reservas.dto.InventarioRequest;
import com.ticketya.reservas.dto.InventarioRequest.Sector;
import com.ticketya.reservas.exception.ConflictException;
import com.ticketya.reservas.model.enums.TipoSector;
import com.ticketya.reservas.repository.InventarioEventoRepository;
import com.ticketya.reservas.service.InventarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga el inventario de los eventos PUBLICADO precargados por ms-eventos (data.sql) si todavia no existen.
 * Es idempotente y se desactiva en pruebas con {@code ticketya.seed.habilitado=false}.
 */
@Component
@ConditionalOnProperty(name = "ticketya.seed.habilitado", havingValue = "true", matchIfMissing = true)
public class DatosInicialesSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesSeed.class);

    private static final List<InventarioRequest> INVENTARIOS_INICIALES = List.of(
            // Evento 1: Festival de Verano 2027 (Estadio Ciudad de Tandil: Campo + Platea Alta)
            new InventarioRequest(1L, 4, List.of(
                    new Sector(1L, TipoSector.NO_NUMERADO, null, null, 5000),
                    new Sector(2L, TipoSector.NUMERADO, 20, 30, null)
            )),
            // Evento 2: Clasico de Verano (Luna Park: Campo + Platea)
            new InventarioRequest(2L, 2, List.of(
                    new Sector(5L, TipoSector.NO_NUMERADO, null, null, 8000),
                    new Sector(6L, TipoSector.NUMERADO, 30, 40, null)
            ))
    );

    private final InventarioEventoRepository inventarioEventoRepository;
    private final InventarioService inventarioService;

    public DatosInicialesSeed(InventarioEventoRepository inventarioEventoRepository,
                              InventarioService inventarioService) {
        this.inventarioEventoRepository = inventarioEventoRepository;
        this.inventarioService = inventarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (InventarioRequest request : INVENTARIOS_INICIALES) {
            if (inventarioEventoRepository.existsById(request.eventoId())) {
                continue;
            }
            try {
                inventarioService.crearInventario(request);
                log.info("Inventario inicial creado para evento {}", request.eventoId());
            } catch (ConflictException ignored) {
                // Otro arranque concurrente ya creo el inventario.
            }
        }
    }
}
