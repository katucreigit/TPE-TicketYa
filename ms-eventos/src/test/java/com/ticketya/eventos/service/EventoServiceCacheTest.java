
package com.ticketya.eventos.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticketya.eventos.client.ReservasClient;
import com.ticketya.eventos.client.VentasClient;
import com.ticketya.eventos.config.CacheConfig;
import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.EventoSector;
import com.ticketya.eventos.model.Lugar;
import com.ticketya.eventos.model.Organizador;
import com.ticketya.eventos.model.Sector;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.model.enums.TipoEvento;
import com.ticketya.eventos.model.enums.TipoSector;
import com.ticketya.eventos.repository.EventoRepository;
import com.ticketya.eventos.repository.EventoSectorRepository;
import com.ticketya.eventos.repository.LugarRepository;
import com.ticketya.eventos.repository.OrganizadorRepository;
import com.ticketya.eventos.repository.ParticipanteRepository;
import com.ticketya.eventos.repository.SectorRepository;

@SpringJUnitConfig(EventoServiceCacheTest.ConfiguracionTest.class)
class EventoServiceCacheTest {

    @Autowired
    private EventoService eventoService;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ReservasClient reservasClient;

    @Autowired
    private VentasClient ventasClient;

    @BeforeEach
    void prepararTest() {
        cacheManager.getCache("catalogoEventos").clear();
        cacheManager.getCache("detalleEvento").clear();
    
        reset(eventoRepository);
        reset(reservasClient);
        reset(ventasClient);
    }

    @Test
    void consultaRepetidaDelCatalogoReutilizaResultadoCacheado() {

        when(eventoRepository.findAll()).thenReturn(List.of());

        eventoService.buscarEventos(
                null, null, null, null, null, null, null,
                "fecha", "asc", 0, 10
        );

        eventoService.buscarEventos(
                null, null, null, null, null, null, null,
                "fecha", "asc", 0, 10
        );

        verify(eventoRepository, times(1)).findAll();
    }

    @Test
    void consultasConParametrosDiferentesGeneranEntradasIndependientes() {

        when(eventoRepository.findAll()).thenReturn(List.of());

        eventoService.buscarEventos(
                null, null, null, null, null, null, null,
                "fecha", "asc", 0, 10
        );

        eventoService.buscarEventos(
                null, null, null, "Buenos Aires", null, null, null,
                "fecha", "asc", 0, 10
        );

        verify(eventoRepository, times(2)).findAll();
    }

    @Test
    void consultaRepetidaDelDetalleReutilizaResultadoCacheado() {

    Evento evento = crearEvento(1L, EstadoEvento.PUBLICADO);

    when(eventoRepository.findById(1L))
            .thenReturn(java.util.Optional.of(evento));

    eventoService.obtenerEvento(1L, 10L, "CLIENTE");
    eventoService.obtenerEvento(1L, 20L, "CLIENTE");

    verify(eventoRepository, times(1)).findById(1L);
}

    @Test
    void consultasDeEventosDiferentesGeneranEntradasIndependientes() {

        Evento evento1 = crearEvento(1L, EstadoEvento.PUBLICADO);
        Evento evento2 = crearEvento(2L, EstadoEvento.PUBLICADO);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento1));

        when(eventoRepository.findById(2L))
                .thenReturn(java.util.Optional.of(evento2));

        eventoService.obtenerEvento(1L, 10L, "CLIENTE");
        eventoService.obtenerEvento(2L, 10L, "CLIENTE");

        verify(eventoRepository, times(1)).findById(1L);
        verify(eventoRepository, times(1)).findById(2L);
    }

    @Test
    void eventoBorradorNoSeGuardaEnCache() {

        Evento evento = crearEvento(1L, EstadoEvento.BORRADOR);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento));

        eventoService.obtenerEvento(1L, 1L, "ORGANIZADOR");
        eventoService.obtenerEvento(1L, 1L, "ORGANIZADOR");

        verify(eventoRepository, times(2)).findById(1L);
    }

    @Test
    void suspenderEventoInvalidaCatalogoYDetalle() {

    Evento evento = crearEvento(1L, EstadoEvento.PUBLICADO);

    when(eventoRepository.findById(1L))
            .thenReturn(java.util.Optional.of(evento));

    when(eventoRepository.save(any(Evento.class)))
            .thenAnswer(invocacion -> invocacion.getArgument(0));

    cacheManager.getCache("catalogoEventos")
            .put("clave-prueba", "catalogo");

    cacheManager.getCache("detalleEvento")
            .put(1L, "detalle");

    eventoService.suspender(1L, "ADMIN");

    assertEquals(EstadoEvento.SUSPENDIDO, evento.getEstado());

    assertNull(cacheManager.getCache("catalogoEventos").get("clave-prueba"));
    assertNull(cacheManager.getCache("detalleEvento").get(1L));

    verify(reservasClient, times(1)).cerrarInventario(1L);
    verify(eventoRepository, times(1)).save(evento);
}

    @Test
    void reanudarEventoInvalidaCatalogoYDetalle() {

        Evento evento = crearEvento(1L, EstadoEvento.SUSPENDIDO);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento));

        when(eventoRepository.save(any(Evento.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        cacheManager.getCache("catalogoEventos")
                .put("clave-prueba", "catalogo");

        cacheManager.getCache("detalleEvento")
                .put(1L, "detalle");

        eventoService.reanudar(1L, "ADMIN");

        assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());

        assertNull(cacheManager.getCache("catalogoEventos").get("clave-prueba"));
        assertNull(cacheManager.getCache("detalleEvento").get(1L));

        verify(reservasClient, times(1)).abrirInventario(1L);
        verify(eventoRepository, times(1)).save(evento);
    }

        @Test
        void publicarEventoInvalidaCatalogoYDetalle() {

        Evento evento = crearEvento(1L, EstadoEvento.BORRADOR);

        Sector sector = new Sector();
        sector.setId(1L);
        sector.setNombre("Campo");
        sector.setTipo(TipoSector.NO_NUMERADO);
        sector.setCapacidad(100);

        EventoSector eventoSector = new EventoSector();
        eventoSector.setEvento(evento);
        eventoSector.setSector(sector);
        eventoSector.setPrecio(new BigDecimal("1000"));

        evento.getSectores().add(eventoSector);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento));

        when(eventoRepository.save(any(Evento.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        cacheManager.getCache("catalogoEventos")
                .put("clave-prueba", "catalogo");

        cacheManager.getCache("detalleEvento")
                .put(1L, "detalle");

        eventoService.publicar(1L, 1L);

        assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());

        assertNull(cacheManager.getCache("catalogoEventos").get("clave-prueba"));
        assertNull(cacheManager.getCache("detalleEvento").get(1L));

        verify(reservasClient, times(1))
                .crearInventario(any(com.ticketya.eventos.dto.InventarioRequest.class));

        verify(eventoRepository, times(1)).save(evento);
    }

    @Test
    void cancelarEventoInvalidaCatalogoYDetalle() {

    Evento evento = crearEvento(1L, EstadoEvento.PUBLICADO);

    when(eventoRepository.findById(1L))
            .thenReturn(java.util.Optional.of(evento));

    when(eventoRepository.save(any(Evento.class)))
            .thenAnswer(invocacion -> invocacion.getArgument(0));

    cacheManager.getCache("catalogoEventos")
            .put("clave-prueba", "catalogo");

    cacheManager.getCache("detalleEvento")
            .put(1L, "detalle");

    eventoService.cancelar(1L, "ADMIN", "Cancelación de prueba");

    assertEquals(EstadoEvento.CANCELADO, evento.getEstado());

    assertNull(cacheManager.getCache("catalogoEventos").get("clave-prueba"));
    assertNull(cacheManager.getCache("detalleEvento").get(1L));

    verify(reservasClient, times(1)).cerrarInventario(1L);
    verify(ventasClient, times(1)).procesarCancelacion(1L);

    verify(eventoRepository, times(1)).save(evento);
    }

    private Evento crearEvento(Long id, EstadoEvento estado) {

        Organizador organizador = new Organizador();
        organizador.setId(1L);
    
        Lugar lugar = new Lugar();
        lugar.setId(1L);
    
        Evento evento = new Evento();
        evento.setId(id);
        evento.setNombre("Evento de prueba");
        evento.setDescripcion("Descripción de prueba");
        evento.setTipo(TipoEvento.RECITAL);
        evento.setEstado(estado);
        evento.setOrganizador(organizador);
        evento.setLugar(lugar);
        evento.setFechaHora(LocalDateTime.now());
    
        return evento;
    }

        @Test
        void cambioDeDisponibilidadInvalidaCatalogoYDetalle() {

        Evento evento = crearEvento(1L, EstadoEvento.PUBLICADO);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento));

        cacheManager.getCache("catalogoEventos")
                .put("clave-prueba", "catalogo");

        cacheManager.getCache("detalleEvento")
                .put(1L, "detalle");

        eventoService.actualizarDisponibilidad(1L, true);

        assertEquals(EstadoEvento.AGOTADO, evento.getEstado());

        assertNull(cacheManager.getCache("catalogoEventos").get("clave-prueba"));
        assertNull(cacheManager.getCache("detalleEvento").get(1L));

        verify(eventoRepository, times(1)).save(evento);
    }

    @Test
    void disponibilidadSinCambioNoInvalidaLasCaches() {

        Evento evento = crearEvento(1L, EstadoEvento.PUBLICADO);

        when(eventoRepository.findById(1L))
                .thenReturn(java.util.Optional.of(evento));

        cacheManager.getCache("catalogoEventos")
                .put("clave-prueba", "catalogo");

        cacheManager.getCache("detalleEvento")
                .put(1L, "detalle");

        eventoService.actualizarDisponibilidad(1L, false);

        assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());

        assertEquals(
                "catalogo",
                cacheManager.getCache("catalogoEventos").get("clave-prueba").get()
        );

        assertEquals(
                "detalle",
                cacheManager.getCache("detalleEvento").get(1L).get()
        );

        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Configuration
    @Import(CacheConfig.class)
    static class ConfiguracionTest {

        @Bean
        EventoRepository eventoRepository() {
            return mock(EventoRepository.class);
        }

        @Bean
        LugarRepository lugarRepository() {
            return mock(LugarRepository.class);
        }

        @Bean
        OrganizadorRepository organizadorRepository() {
            return mock(OrganizadorRepository.class);
        }

        @Bean
        ParticipanteRepository participanteRepository() {
            return mock(ParticipanteRepository.class);
        }

        @Bean
        EventoSectorRepository eventoSectorRepository() {
            return mock(EventoSectorRepository.class);
        }

        @Bean
        SectorRepository sectorRepository() {
            return mock(SectorRepository.class);
        }

        @Bean
        ReservasClient reservasClient() {
            return mock(ReservasClient.class);
        }

        @Bean
        VentasClient ventasClient() {
            return mock(VentasClient.class);
        }

        @Bean
        EventoService eventoService(
                EventoRepository eventoRepository,
                LugarRepository lugarRepository,
                OrganizadorRepository organizadorRepository,
                ParticipanteRepository participanteRepository,
                EventoSectorRepository eventoSectorRepository,
                SectorRepository sectorRepository,
                ReservasClient reservasClient,
                VentasClient ventasClient
        ) {
            return new EventoService(
                    eventoRepository,
                    lugarRepository,
                    organizadorRepository,
                    participanteRepository,
                    eventoSectorRepository,
                    sectorRepository,
                    reservasClient,
                    ventasClient
            );
        }
    }
}

