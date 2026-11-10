
package com.ticketya.eventos.service;

import com.ticketya.eventos.client.ReservasClient;
import com.ticketya.eventos.client.VentasClient;
import com.ticketya.eventos.exception.ConflictException;
import com.ticketya.eventos.model.Evento;
import com.ticketya.eventos.model.enums.EstadoEvento;
import com.ticketya.eventos.repository.*;
import org.junit.jupiter.api.Test;
import com.ticketya.eventos.model.Organizador;
import com.ticketya.eventos.model.Lugar;
import com.ticketya.eventos.exception.BusinessRuleException;
import com.ticketya.eventos.model.Sector;
import com.ticketya.eventos.model.enums.TipoSector;
import java.util.ArrayList;
import com.ticketya.eventos.exception.BadGatewayException;
import org.springframework.web.client.RestClientException;
import com.ticketya.eventos.model.EventoSector;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import java.util.HashSet;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EventoServiceTest {

    @Test
    void noPermiteSuspenderEventoEnBorrador() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        LugarRepository lugarRepository = mock(LugarRepository.class);
        OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
        ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
        EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        ReservasClient reservasClient = mock(ReservasClient.class);
        VentasClient ventasClient = mock(VentasClient.class);

        EventoService service = new EventoService(
                eventoRepository,
                lugarRepository,
                organizadorRepository,
                participanteRepository,
                eventoSectorRepository,
                sectorRepository,
                reservasClient,
                ventasClient
        );

        Evento evento = new Evento();
        evento.setEstado(EstadoEvento.BORRADOR);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThrows(
                ConflictException.class,
                () -> service.suspender(1L, "ADMIN")
        );

        verifyNoInteractions(reservasClient);
    }

    @Test
    void noPermiteSuspenderEventoSiNoEsAdmin() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        LugarRepository lugarRepository = mock(LugarRepository.class);
        OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
        ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
        EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        ReservasClient reservasClient = mock(ReservasClient.class);
        VentasClient ventasClient = mock(VentasClient.class);

        EventoService service = new EventoService(
                eventoRepository,
                lugarRepository,
                organizadorRepository,
                participanteRepository,
                eventoSectorRepository,
                sectorRepository,
                reservasClient,
                ventasClient
        );

        assertThrows(
                com.ticketya.eventos.exception.ForbiddenException.class,
                () -> service.suspender(1L, "ORGANIZADOR")
        );

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(reservasClient);

    }
    @Test
    void noPermiteReanudarEventoQueNoEstaSuspendido() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        LugarRepository lugarRepository = mock(LugarRepository.class);
        OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
        ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
        EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        ReservasClient reservasClient = mock(ReservasClient.class);
        VentasClient ventasClient = mock(VentasClient.class);

        EventoService service = new EventoService(
                eventoRepository,
                lugarRepository,
                organizadorRepository,
                participanteRepository,
                eventoSectorRepository,
                sectorRepository,
                reservasClient,
                ventasClient
        );

        Evento evento = new Evento();
evento.setEstado(EstadoEvento.PUBLICADO);

Organizador organizador = new Organizador();
organizador.setId(1L);
organizador.setNombre("Organizador de prueba");
organizador.setEmail("prueba@test.com");

evento.setOrganizador(organizador);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThrows(
                ConflictException.class,
                () -> service.reanudar(1L, "ADMIN")
        );

        verifyNoInteractions(reservasClient);
    }

    @Test
    void adminPuedeSuspenderEventoPublicado() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        LugarRepository lugarRepository = mock(LugarRepository.class);
        OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
        ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
        EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        ReservasClient reservasClient = mock(ReservasClient.class);
        VentasClient ventasClient = mock(VentasClient.class);

        EventoService service = new EventoService(
                eventoRepository,
                lugarRepository,
                organizadorRepository,
                participanteRepository,
                eventoSectorRepository,
                sectorRepository,
                reservasClient,
                ventasClient
        );

        Evento evento = new Evento();
        evento.setEstado(EstadoEvento.PUBLICADO);
        Organizador organizador = new Organizador();
organizador.setId(1L);
organizador.setNombre("Organizador de prueba");
organizador.setEmail("prueba@test.com");

evento.setOrganizador(organizador);
Lugar lugar = new Lugar();
lugar.setId(1L);
lugar.setNombre("Lugar de prueba");
lugar.setDireccion("Calle 123");
lugar.setCiudad("Tandil");

evento.setLugar(lugar);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.suspender(1L, "ADMIN");

        org.junit.jupiter.api.Assertions.assertEquals(EstadoEvento.SUSPENDIDO, evento.getEstado());
        verify(reservasClient).cerrarInventario(1L);
        verify(eventoRepository).save(evento);
    }

    @Test
    void adminPuedeReanudarEventoSuspendido() {

        EventoRepository eventoRepository = mock(EventoRepository.class);
        LugarRepository lugarRepository = mock(LugarRepository.class);
        OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
        ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
        EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
        SectorRepository sectorRepository = mock(SectorRepository.class);
        ReservasClient reservasClient = mock(ReservasClient.class);
        VentasClient ventasClient = mock(VentasClient.class);

        EventoService service = new EventoService(
                eventoRepository,
                lugarRepository,
                organizadorRepository,
                participanteRepository,
                eventoSectorRepository,
                sectorRepository,
                reservasClient,
                ventasClient
        );

        Evento evento = new Evento();
        evento.setEstado(EstadoEvento.SUSPENDIDO);

        Organizador organizador = new Organizador();
        organizador.setId(1L);
        organizador.setNombre("Organizador de prueba");
        organizador.setEmail("prueba@test.com");
        evento.setOrganizador(organizador);

        Lugar lugar = new Lugar();
        lugar.setId(1L);
        lugar.setNombre("Lugar de prueba");
        lugar.setDireccion("Calle 123");
        lugar.setCiudad("Tandil");
        evento.setLugar(lugar);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reanudar(1L, "ADMIN");

        org.junit.jupiter.api.Assertions.assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());
        verify(reservasClient).abrirInventario(1L);
        verify(eventoRepository).save(evento);
    }
    @Test
void noPermitePublicarSiFaltanPreciosDeSectores() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);

    Sector sector = new Sector();
    sector.setId(10L);
    sector.setLugar(lugar);
    sector.setNombre("Platea");
    sector.setTipo(TipoSector.NO_NUMERADO);
    sector.setCapacidad(100);

    lugar.setSectores(new ArrayList<>(java.util.List.of(sector)));

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setEstado(EstadoEvento.BORRADOR);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

    assertThrows(
            BusinessRuleException.class,
            () -> service.publicar(1L, 1L)
    );

    verifyNoInteractions(reservasClient);
    verify(eventoRepository, never()).save(any(Evento.class));
}
@Test
void noPublicaSiReservasNoEstaDisponible() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);

    Sector sector = new Sector();
    sector.setId(10L);
    sector.setLugar(lugar);
    sector.setNombre("Platea");
    sector.setTipo(TipoSector.NO_NUMERADO);
    sector.setCapacidad(100);

    lugar.setSectores(new java.util.ArrayList<>(java.util.List.of(sector)));

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setEstado(EstadoEvento.BORRADOR);

    EventoSector eventoSector = new EventoSector();
    eventoSector.setEvento(evento);
    eventoSector.setSector(sector);
    eventoSector.setPrecio(new java.math.BigDecimal("1000"));

    evento.setSectores(new java.util.ArrayList<>(java.util.List.of(eventoSector)));

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

    doThrow(new RestClientException("Servicio no disponible"))
            .when(reservasClient).crearInventario(any());

    assertThrows(
            BadGatewayException.class,
            () -> service.publicar(1L, 1L)
    );

    org.junit.jupiter.api.Assertions.assertEquals(
            EstadoEvento.BORRADOR,
            evento.getEstado()
    );

    verify(reservasClient).crearInventario(any());
    verify(eventoRepository, never()).save(any(Evento.class));
}
@Test
void publicaEventoSiInventarioYaExiste() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);

    Sector sector = new Sector();
    sector.setId(10L);
    sector.setLugar(lugar);
    sector.setNombre("Platea");
    sector.setTipo(TipoSector.NO_NUMERADO);
    sector.setCapacidad(100);

    lugar.setSectores(new java.util.ArrayList<>(java.util.List.of(sector)));

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setEstado(EstadoEvento.BORRADOR);

    EventoSector eventoSector = new EventoSector();
    eventoSector.setEvento(evento);
    eventoSector.setSector(sector);
    eventoSector.setPrecio(new java.math.BigDecimal("1000"));

    evento.setSectores(new java.util.ArrayList<>(java.util.List.of(eventoSector)));

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

    doThrow(HttpClientErrorException.create(
            HttpStatus.CONFLICT,
            "Conflict",
            HttpHeaders.EMPTY,
            null,
            null
    )).when(reservasClient).crearInventario(any());

    when(eventoRepository.save(any(Evento.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    service.publicar(1L, 1L);

    org.junit.jupiter.api.Assertions.assertEquals(
            EstadoEvento.PUBLICADO,
            evento.getEstado()
    );

    verify(reservasClient).crearInventario(any());
    verify(eventoRepository).save(evento);
}
@Test
void publicaEventoCorrectamenteCuandoReservasEstaDisponible() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);

    Sector sector = new Sector();
    sector.setId(10L);
    sector.setLugar(lugar);
    sector.setNombre("Platea");
    sector.setTipo(TipoSector.NO_NUMERADO);
    sector.setCapacidad(100);

    lugar.setSectores(new ArrayList<>(java.util.List.of(sector)));

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setEstado(EstadoEvento.BORRADOR);

    EventoSector eventoSector = new EventoSector();
    eventoSector.setEvento(evento);
    eventoSector.setSector(sector);
    eventoSector.setPrecio(new java.math.BigDecimal("1000"));

    evento.setSectores(new ArrayList<>(java.util.List.of(eventoSector)));

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
    when(eventoRepository.save(any(Evento.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    service.publicar(1L, 1L);

    org.junit.jupiter.api.Assertions.assertEquals(
            EstadoEvento.PUBLICADO,
            evento.getEstado()
    );

    verify(reservasClient).crearInventario(any());
    verify(eventoRepository).save(evento);
}
@Test
void adminPuedeCancelarEventoPublicado() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.PUBLICADO);

    Organizador organizador = new Organizador();
    organizador.setId(1L);
    organizador.setNombre("Organizador de prueba");
    organizador.setEmail("prueba@test.com");
    evento.setOrganizador(organizador);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setNombre("Lugar de prueba");
    lugar.setDireccion("Calle 123");
    lugar.setCiudad("Tandil");
    evento.setLugar(lugar);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
    when(eventoRepository.save(any(Evento.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    service.cancelar(1L, "ADMIN", "  Suspensión del evento  ");

    org.junit.jupiter.api.Assertions.assertEquals(
            EstadoEvento.CANCELADO, evento.getEstado()
    );
    org.junit.jupiter.api.Assertions.assertEquals(
            "Suspensión del evento", evento.getMotivoCancelacion()
    );

    verify(reservasClient).cerrarInventario(1L);
    verify(ventasClient).procesarCancelacion(1L);
    verify(eventoRepository).save(evento);
}

@Test
void busquedaSoloDevuelveEventosPublicadosOAgotados() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository,
            lugarRepository,
            organizadorRepository,
            participanteRepository,
            eventoSectorRepository,
            sectorRepository,
            reservasClient,
            ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento publicado = new Evento();
    publicado.setId(1L);
    publicado.setNombre("Recital");
    publicado.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    publicado.setEstado(EstadoEvento.PUBLICADO);
    publicado.setOrganizador(organizador);
    publicado.setLugar(lugar);
    publicado.setFechaHora(java.time.LocalDateTime.of(2027, 5, 10, 21, 0));
    publicado.setParticipantes(new HashSet<>());
    publicado.setSectores(new ArrayList<>());

    Evento borrador = new Evento();
    borrador.setId(2L);
    borrador.setEstado(EstadoEvento.BORRADOR);

    when(eventoRepository.findAll())
            .thenReturn(java.util.List.of(publicado, borrador));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.totalElements());
org.junit.jupiter.api.Assertions.assertEquals(
        1L, resultado.content().get(0).id()
);
}

}
