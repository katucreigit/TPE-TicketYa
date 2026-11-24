package com.ticketya.reservas.config;

import com.ticketya.reservas.dto.InventarioRequest;
import com.ticketya.reservas.repository.InventarioEventoRepository;
import com.ticketya.reservas.service.InventarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatosInicialesSeedTest {

    @Test
    void creaInventariosInicialesCuandoNoExisten() {
        InventarioEventoRepository repo = mock(InventarioEventoRepository.class);
        InventarioService service = mock(InventarioService.class);
        when(repo.existsById(1L)).thenReturn(false);
        when(repo.existsById(2L)).thenReturn(false);

        new DatosInicialesSeed(repo, service).run(new DefaultApplicationArguments());

        verify(service).crearInventario(argThat((InventarioRequest r) -> r.eventoId().equals(1L) && r.sectores().size() == 2));
        verify(service).crearInventario(argThat((InventarioRequest r) -> r.eventoId().equals(2L) && r.sectores().size() == 2));
        verify(service, times(2)).crearInventario(any());
    }

    @Test
    void noRecreaInventariosQueYaExisten() {
        InventarioEventoRepository repo = mock(InventarioEventoRepository.class);
        InventarioService service = mock(InventarioService.class);
        when(repo.existsById(1L)).thenReturn(true);
        when(repo.existsById(2L)).thenReturn(true);

        new DatosInicialesSeed(repo, service).run(new DefaultApplicationArguments());

        verify(service, never()).crearInventario(any());
    }
}
