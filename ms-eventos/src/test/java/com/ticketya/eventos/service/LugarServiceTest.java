
package com.ticketya.eventos.service;

import com.ticketya.eventos.dto.LugarRequest;
import com.ticketya.eventos.dto.SectorRequest;
import com.ticketya.eventos.exception.BadRequestException;
import com.ticketya.eventos.exception.NotFoundException;
import com.ticketya.eventos.model.Lugar;
import com.ticketya.eventos.model.enums.TipoSector;
import com.ticketya.eventos.repository.LugarRepository;
import com.ticketya.eventos.repository.SectorRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LugarServiceTest {

    private LugarRepository lugarRepository;
    private SectorRepository sectorRepository;
    private LugarService lugarService;

    @BeforeEach
    void setUp() {
        lugarRepository = mock(LugarRepository.class);
        sectorRepository = mock(SectorRepository.class);
        lugarService = new LugarService(lugarRepository, sectorRepository);
    }

    @Test
    void crearLugarCorrectamente() {
        LugarRequest request = new LugarRequest(
                "Estadio",
                "Calle 123",
                "Tandil",
                new BigDecimal("-37.32"),
                new BigDecimal("-59.13"),
                List.of(new SectorRequest("Campo", TipoSector.NO_NUMERADO,
                        500, null, null))
        );

        when(lugarRepository.save(any(Lugar.class))).thenAnswer(invocation -> {
            Lugar lugar = invocation.getArgument(0);
            lugar.setId(1L);
            return lugar;
        });

        var respuesta = lugarService.crear(request);

        assertEquals(1L, respuesta.id());
        assertEquals("Estadio", respuesta.nombre());
        assertEquals(1, respuesta.sectores().size());
        verify(lugarRepository).save(any(Lugar.class));
    }

    @Test
    void noPermiteSectoresDuplicadosAlCrearLugar() {
        LugarRequest request = new LugarRequest(
                "Estadio",
                "Calle 123",
                "Tandil",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                        new SectorRequest("Campo", TipoSector.NO_NUMERADO, 500, null, null),
                        new SectorRequest(" campo ", TipoSector.NO_NUMERADO, 300, null, null)
                )
        );

        assertThrows(BadRequestException.class,
                () -> lugarService.crear(request));

        verify(lugarRepository, never()).save(any(Lugar.class));
    }

    @Test
    void noPermiteSectorNumeradoSinFilas() {
        LugarRequest request = new LugarRequest(
                "Estadio",
                "Calle 123",
                "Tandil",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new SectorRequest("Platea", TipoSector.NUMERADO,
                        null, null, 20))
        );

        assertThrows(BadRequestException.class,
                () -> lugarService.crear(request));
    }

    @Test
    void noPermiteSectorNoNumeradoSinCapacidad() {
        LugarRequest request = new LugarRequest(
                "Estadio",
                "Calle 123",
                "Tandil",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new SectorRequest("Campo", TipoSector.NO_NUMERADO,
                        null, null, null))
        );

        assertThrows(BadRequestException.class,
                () -> lugarService.crear(request));
    }

    @Test
    void obtenerLugarInexistenteDevuelveNotFound() {
        when(lugarRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> lugarService.obtener(99L));
    }

    @Test
    void noPermitePaginacionInvalida() {
        assertThrows(BadRequestException.class,
                () -> lugarService.listar(null, -1, 10));

        assertThrows(BadRequestException.class,
                () -> lugarService.listar(null, 0, 0));
    }

    @Test
    void noPermiteAgregarSectorDuplicado() {
        Lugar lugar = new Lugar();
        lugar.setSectores(new ArrayList<>());
        lugar.agregarSector(crearSectorExistente("Campo"));

        when(lugarRepository.findById(1L)).thenReturn(Optional.of(lugar));

        SectorRequest request = new SectorRequest(
                " campo ",
                TipoSector.NO_NUMERADO,
                300,
                null,
                null
        );

        assertThrows(BadRequestException.class,
                () -> lugarService.agregarSector(1L, request));

        verify(lugarRepository, never()).save(any(Lugar.class));
    }

    private com.ticketya.eventos.model.Sector crearSectorExistente(String nombre) {
        var sector = new com.ticketya.eventos.model.Sector();
        sector.setNombre(nombre);
        sector.setTipo(TipoSector.NO_NUMERADO);
        sector.setCapacidad(500);
        return sector;
    }
    
}

