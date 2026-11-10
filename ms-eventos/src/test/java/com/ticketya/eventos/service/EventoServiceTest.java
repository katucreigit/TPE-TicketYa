
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
import com.ticketya.eventos.model.enums.TipoParticipante;
import com.ticketya.eventos.model.Participante;
import com.ticketya.eventos.exception.BadRequestException;
import com.ticketya.eventos.model.enums.TipoEvento;
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
void noPermiteCancelarSiNoEsAdmin() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    assertThrows(
            com.ticketya.eventos.exception.ForbiddenException.class,
            () -> service.cancelar(1L, "ORGANIZADOR", "motivo")
    );

    verifyNoInteractions(eventoRepository);
    verifyNoInteractions(reservasClient);
    verifyNoInteractions(ventasClient);
}

@Test
void noPermiteCancelarEventoYaCancelado() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.CANCELADO);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

    assertThrows(
            ConflictException.class,
            () -> service.cancelar(1L, "ADMIN", "motivo")
    );

    verifyNoInteractions(reservasClient);
    verifyNoInteractions(ventasClient);
    verify(eventoRepository, never()).save(any());
}

@Test
void noPermiteCancelarEventoFinalizado() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.FINALIZADO);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

    assertThrows(
            ConflictException.class,
            () -> service.cancelar(1L, "ADMIN", "motivo")
    );

    verifyNoInteractions(reservasClient);
    verifyNoInteractions(ventasClient);
    verify(eventoRepository, never()).save(any());
}

@Test
void errorAlCerrarInventarioImpideLaCancelacion() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.PUBLICADO);

    Organizador organizador = new Organizador();
    organizador.setId(1L);
    evento.setOrganizador(organizador);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");
    evento.setLugar(lugar);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
    doThrow(new RestClientException("ms-reservas no disponible"))
            .when(reservasClient).cerrarInventario(1L);

    assertThrows(
            BadGatewayException.class,
            () -> service.cancelar(1L, "ADMIN", "motivo")
    );

    verify(reservasClient).cerrarInventario(1L);
    verifyNoInteractions(ventasClient);
    verify(eventoRepository, never()).save(any());
    org.junit.jupiter.api.Assertions.assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());
}

@Test
void errorEnVentasImpideLaCancelacion() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    // BORRADOR: no llama a cerrarInventario, solo a ventas
    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.BORRADOR);

    Organizador organizador = new Organizador();
    organizador.setId(1L);
    evento.setOrganizador(organizador);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");
    evento.setLugar(lugar);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
    doThrow(new RestClientException("ms-ventas no disponible"))
            .when(ventasClient).procesarCancelacion(1L);

    assertThrows(
            BadGatewayException.class,
            () -> service.cancelar(1L, "ADMIN", "motivo")
    );

    verifyNoInteractions(reservasClient);
    verify(ventasClient).procesarCancelacion(1L);
    verify(eventoRepository, never()).save(any());
    org.junit.jupiter.api.Assertions.assertEquals(EstadoEvento.BORRADOR, evento.getEstado());
}

@Test
void falloParcialInventarioCerradoPeroVentasFallaEventoNoQuedaCancelado() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setEstado(EstadoEvento.PUBLICADO);

    Organizador organizador = new Organizador();
    organizador.setId(1L);
    evento.setOrganizador(organizador);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");
    evento.setLugar(lugar);

    when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
    // reservas cierra OK (no lanza excepción)
    doNothing().when(reservasClient).cerrarInventario(1L);
    // ventas falla
    doThrow(new RestClientException("ms-ventas no disponible"))
            .when(ventasClient).procesarCancelacion(1L);

    assertThrows(
            BadGatewayException.class,
            () -> service.cancelar(1L, "ADMIN", "motivo")
    );

    // el inventario se cerró pero el evento NO quedó cancelado
    verify(reservasClient).cerrarInventario(1L);
    verify(ventasClient).procesarCancelacion(1L);
    verify(eventoRepository, never()).save(any());
    org.junit.jupiter.api.Assertions.assertEquals(EstadoEvento.PUBLICADO, evento.getEstado());
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
void ordenarPorFechaAscendente() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento A");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 6, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento B");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 1, 1, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "asc", 0, 20
    );

    // B (enero) debe aparecer antes que A (junio)
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(1).id());
}

@Test
void ordenarPorFechaDescendente() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento A");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 6, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento B");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 1, 1, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "desc", 0, 20
    );

    // A (junio) debe aparecer antes que B (enero)
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(1).id());
}

@Test
void ordenarPorPrecioAscendente() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Sector sectorA = new Sector();
    sectorA.setId(10L);
    sectorA.setLugar(lugar);
    sectorA.setNombre("Campo");
    sectorA.setTipo(TipoSector.NO_NUMERADO);
    sectorA.setCapacidad(100);

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento caro");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());

    EventoSector esA = new EventoSector();
    esA.setEvento(eventoA);
    esA.setSector(sectorA);
    esA.setPrecio(new java.math.BigDecimal("5000"));
    eventoA.setSectores(new ArrayList<>(java.util.List.of(esA)));

    Sector sectorB = new Sector();
    sectorB.setId(11L);
    sectorB.setLugar(lugar);
    sectorB.setNombre("Platea");
    sectorB.setTipo(TipoSector.NO_NUMERADO);
    sectorB.setCapacidad(100);

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento barato");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>());

    EventoSector esB = new EventoSector();
    esB.setEvento(eventoB);
    esB.setSector(sectorB);
    esB.setPrecio(new java.math.BigDecimal("500"));
    eventoB.setSectores(new ArrayList<>(java.util.List.of(esB)));

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "precio", "asc", 0, 20
    );

    // B (500) debe aparecer antes que A (5000)
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(1).id());
}

@Test
void ordenarPorPrecioDescendente() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Sector sectorA = new Sector();
    sectorA.setId(10L);
    sectorA.setLugar(lugar);
    sectorA.setNombre("Campo");
    sectorA.setTipo(TipoSector.NO_NUMERADO);
    sectorA.setCapacidad(100);

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento caro");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());

    EventoSector esA = new EventoSector();
    esA.setEvento(eventoA);
    esA.setSector(sectorA);
    esA.setPrecio(new java.math.BigDecimal("5000"));
    eventoA.setSectores(new ArrayList<>(java.util.List.of(esA)));

    Sector sectorB = new Sector();
    sectorB.setId(11L);
    sectorB.setLugar(lugar);
    sectorB.setNombre("Platea");
    sectorB.setTipo(TipoSector.NO_NUMERADO);
    sectorB.setCapacidad(100);

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento barato");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>());

    EventoSector esB = new EventoSector();
    esB.setEvento(eventoB);
    esB.setSector(sectorB);
    esB.setPrecio(new java.math.BigDecimal("500"));
    eventoB.setSectores(new ArrayList<>(java.util.List.of(esB)));

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "precio", "desc", 0, 20
    );

    // A (5000) debe aparecer antes que B (500)
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(1).id());
}

@Test
void ordenarPorRelevanciaAscendente() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    // eventoA: nombre empieza con "rock" → puntuacion 1 (más relevante)
    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Rock Festival");
    eventoA.setDescripcion("Un evento de música");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    // eventoB: nombre no tiene "rock", descripcion sí → puntuacion 4 (menos relevante que participante)
    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Gran Show");
    eventoB.setDescripcion("Noche de rock nacional");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoB, eventoA));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            "rock", "relevancia", "asc", 0, 20
    );

    // A (puntuacion 1) debe aparecer antes que B (puntuacion 4)
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(1).id());
}

@Test
void ordenarPorRelevanciaDescendentePoneMenosRelevantesPrimero() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    // eventoA: nombre empieza con "rock" → puntuacion 1 (más relevante)
    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Rock Festival");
    eventoA.setDescripcion("Un evento de música");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    // eventoB: nombre no tiene "rock", descripcion sí → puntuacion 4 (menos relevante)
    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Gran Show");
    eventoB.setDescripcion("Noche de rock nacional");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            "rock", "relevancia", "desc", 0, 20
    );

    // con desc el comparador se invierte: B (puntuacion 4) aparece antes que A (puntuacion 1)
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(1).id());
}

@Test
void relevanciaParticipanteEsMasRelevanteQueDescripcion() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    // eventoA: "rock" solo en descripcion → puntuacion 4
    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Gran Show");
    eventoA.setDescripcion("Noche de rock nacional");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    // eventoB: "rock" en nombre de participante → puntuacion 3 (más relevante que descripcion)
    Participante participante = new Participante();
    participante.setId(1L);
    participante.setNombre("Los Rockeros");
    participante.setTipo(TipoParticipante.ARTISTA);

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Festival de Verano");
    eventoB.setDescripcion("Gran evento musical");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>(java.util.List.of(participante)));
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            "rock", "relevancia", "asc", 0, 20
    );

    // B (participante, puntuacion 3) debe aparecer antes que A (descripcion, puntuacion 4)
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(1).id());
}

@Test
void busquedaPorTextoEncuentraEventosPorTipo() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    // eventoA: tipo RECITAL, q="recital" debe encontrarlo
    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Gran Noche");
    eventoA.setDescripcion("Un evento especial");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    // eventoB: tipo TEATRO, q="recital" NO debe encontrarlo
    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Obra de Teatro");
    eventoB.setDescripcion("Una obra especial");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.TEATRO);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            "recital", "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void desempatePorIdCuandoDosEventosTienenLaMismaFecha() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    java.time.LocalDateTime mismaFecha = java.time.LocalDateTime.of(2027, 5, 1, 20, 0);

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento A");
    eventoA.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(mismaFecha);
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento B");
    eventoB.setTipo(com.ticketya.eventos.model.enums.TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(mismaFecha);
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoB, eventoA));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "asc", 0, 20
    );

    // con misma fecha, desempata por id: A (1) antes que B (2)
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(1).id());
}

@Test
void filtroPorFechaDesdeExcluyeEventosAnteriores() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoAntiguo = new Evento();
    eventoAntiguo.setId(1L);
    eventoAntiguo.setNombre("Evento Antiguo");
    eventoAntiguo.setTipo(TipoEvento.RECITAL);
    eventoAntiguo.setEstado(EstadoEvento.PUBLICADO);
    eventoAntiguo.setOrganizador(organizador);
    eventoAntiguo.setLugar(lugar);
    eventoAntiguo.setFechaHora(java.time.LocalDateTime.of(2027, 1, 10, 20, 0));
    eventoAntiguo.setParticipantes(new HashSet<>());
    eventoAntiguo.setSectores(new ArrayList<>());

    Evento eventoNuevo = new Evento();
    eventoNuevo.setId(2L);
    eventoNuevo.setNombre("Evento Nuevo");
    eventoNuevo.setTipo(TipoEvento.RECITAL);
    eventoNuevo.setEstado(EstadoEvento.PUBLICADO);
    eventoNuevo.setOrganizador(organizador);
    eventoNuevo.setLugar(lugar);
    eventoNuevo.setFechaHora(java.time.LocalDateTime.of(2027, 6, 20, 20, 0));
    eventoNuevo.setParticipantes(new HashSet<>());
    eventoNuevo.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoAntiguo, eventoNuevo));

    var resultado = service.buscarEventos(
            null, null, null, null,
            java.time.LocalDate.of(2027, 3, 1), null,
            null, "fecha", "asc", 0, 20
    );

    // solo el evento a partir de marzo
    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
}

@Test
void filtroPorFechaHastaExcluyeEventosPosteriores() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoAntiguo = new Evento();
    eventoAntiguo.setId(1L);
    eventoAntiguo.setNombre("Evento Antiguo");
    eventoAntiguo.setTipo(TipoEvento.RECITAL);
    eventoAntiguo.setEstado(EstadoEvento.PUBLICADO);
    eventoAntiguo.setOrganizador(organizador);
    eventoAntiguo.setLugar(lugar);
    eventoAntiguo.setFechaHora(java.time.LocalDateTime.of(2027, 1, 10, 20, 0));
    eventoAntiguo.setParticipantes(new HashSet<>());
    eventoAntiguo.setSectores(new ArrayList<>());

    Evento eventoNuevo = new Evento();
    eventoNuevo.setId(2L);
    eventoNuevo.setNombre("Evento Nuevo");
    eventoNuevo.setTipo(TipoEvento.RECITAL);
    eventoNuevo.setEstado(EstadoEvento.PUBLICADO);
    eventoNuevo.setOrganizador(organizador);
    eventoNuevo.setLugar(lugar);
    eventoNuevo.setFechaHora(java.time.LocalDateTime.of(2027, 6, 20, 20, 0));
    eventoNuevo.setParticipantes(new HashSet<>());
    eventoNuevo.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoAntiguo, eventoNuevo));

    var resultado = service.buscarEventos(
            null, null, null, null,
            null, java.time.LocalDate.of(2027, 3, 1),
            null, "fecha", "asc", 0, 20
    );

    // solo el evento antes de marzo
    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void filtroPorOrganizadorIdDevuelveSoloSusEventos() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador org1 = new Organizador();
    org1.setId(1L);

    Organizador org2 = new Organizador();
    org2.setId(2L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoOrg1 = new Evento();
    eventoOrg1.setId(1L);
    eventoOrg1.setNombre("Evento de org1");
    eventoOrg1.setTipo(TipoEvento.RECITAL);
    eventoOrg1.setEstado(EstadoEvento.PUBLICADO);
    eventoOrg1.setOrganizador(org1);
    eventoOrg1.setLugar(lugar);
    eventoOrg1.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoOrg1.setParticipantes(new HashSet<>());
    eventoOrg1.setSectores(new ArrayList<>());

    Evento eventoOrg2 = new Evento();
    eventoOrg2.setId(2L);
    eventoOrg2.setNombre("Evento de org2");
    eventoOrg2.setTipo(TipoEvento.TEATRO);
    eventoOrg2.setEstado(EstadoEvento.PUBLICADO);
    eventoOrg2.setOrganizador(org2);
    eventoOrg2.setLugar(lugar);
    eventoOrg2.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoOrg2.setParticipantes(new HashSet<>());
    eventoOrg2.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoOrg1, eventoOrg2));

    var resultado = service.buscarEventos(
            null, 1L, null, null, null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void relevanciaSinQOrdenaPorFecha() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento eventoA = new Evento();
    eventoA.setId(1L);
    eventoA.setNombre("Evento A");
    eventoA.setTipo(TipoEvento.RECITAL);
    eventoA.setEstado(EstadoEvento.PUBLICADO);
    eventoA.setOrganizador(organizador);
    eventoA.setLugar(lugar);
    eventoA.setFechaHora(java.time.LocalDateTime.of(2027, 6, 1, 20, 0));
    eventoA.setParticipantes(new HashSet<>());
    eventoA.setSectores(new ArrayList<>());

    Evento eventoB = new Evento();
    eventoB.setId(2L);
    eventoB.setNombre("Evento B");
    eventoB.setTipo(TipoEvento.RECITAL);
    eventoB.setEstado(EstadoEvento.PUBLICADO);
    eventoB.setOrganizador(organizador);
    eventoB.setLugar(lugar);
    eventoB.setFechaHora(java.time.LocalDateTime.of(2027, 1, 1, 20, 0));
    eventoB.setParticipantes(new HashSet<>());
    eventoB.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoA, eventoB));

    // sin q, relevancia equivale a fecha asc
    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            null, "relevancia", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(1).id());
}

@Test
void busquedaSinCoincidenciasDevuelveListaVacia() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setNombre("Festival de Rock");
    evento.setTipo(TipoEvento.RECITAL);
    evento.setEstado(EstadoEvento.PUBLICADO);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    evento.setParticipantes(new HashSet<>());
    evento.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(evento));

    var resultado = service.buscarEventos(
            null, null, null, "Mar del Plata", null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(0, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(0L, resultado.totalElements());
    org.junit.jupiter.api.Assertions.assertEquals(0, resultado.totalPages());
}

@Test
void filtroporCiudadDevuelveSoloEventosDeLaCiudad() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugarTandil = new Lugar();
    lugarTandil.setId(1L);
    lugarTandil.setCiudad("Tandil");

    Lugar lugarBsAs = new Lugar();
    lugarBsAs.setId(2L);
    lugarBsAs.setCiudad("Buenos Aires");

    Evento eventoTandil = new Evento();
    eventoTandil.setId(1L);
    eventoTandil.setNombre("Evento Tandil");
    eventoTandil.setTipo(TipoEvento.RECITAL);
    eventoTandil.setEstado(EstadoEvento.PUBLICADO);
    eventoTandil.setOrganizador(organizador);
    eventoTandil.setLugar(lugarTandil);
    eventoTandil.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    eventoTandil.setParticipantes(new HashSet<>());
    eventoTandil.setSectores(new ArrayList<>());

    Evento eventoBsAs = new Evento();
    eventoBsAs.setId(2L);
    eventoBsAs.setNombre("Evento Buenos Aires");
    eventoBsAs.setTipo(TipoEvento.RECITAL);
    eventoBsAs.setEstado(EstadoEvento.PUBLICADO);
    eventoBsAs.setOrganizador(organizador);
    eventoBsAs.setLugar(lugarBsAs);
    eventoBsAs.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    eventoBsAs.setParticipantes(new HashSet<>());
    eventoBsAs.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(eventoTandil, eventoBsAs));

    var resultado = service.buscarEventos(
            null, null, null, "Tandil", null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void filtroporCiudadNoDistingueMayusculas() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento evento = new Evento();
    evento.setId(1L);
    evento.setNombre("Evento en Tandil");
    evento.setTipo(TipoEvento.RECITAL);
    evento.setEstado(EstadoEvento.PUBLICADO);
    evento.setOrganizador(organizador);
    evento.setLugar(lugar);
    evento.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    evento.setParticipantes(new HashSet<>());
    evento.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(evento));

    var resultado = service.buscarEventos(
            null, null, null, "TANDIL", null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
}

@Test
void filtroPorTipoDevuelveSoloEventosDelTipoIndicado() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento recital = new Evento();
    recital.setId(1L);
    recital.setNombre("Recital de Rock");
    recital.setTipo(TipoEvento.RECITAL);
    recital.setEstado(EstadoEvento.PUBLICADO);
    recital.setOrganizador(organizador);
    recital.setLugar(lugar);
    recital.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    recital.setParticipantes(new HashSet<>());
    recital.setSectores(new ArrayList<>());

    Evento teatro = new Evento();
    teatro.setId(2L);
    teatro.setNombre("Obra de Teatro");
    teatro.setTipo(TipoEvento.TEATRO);
    teatro.setEstado(EstadoEvento.PUBLICADO);
    teatro.setOrganizador(organizador);
    teatro.setLugar(lugar);
    teatro.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    teatro.setParticipantes(new HashSet<>());
    teatro.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(recital, teatro));

    var resultado = service.buscarEventos(
            TipoEvento.RECITAL, null, null, null, null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void filtroPorParticipanteDevuelveSoloEventosConEseParticipante() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Participante participante = new Participante();
    participante.setId(1L);
    participante.setNombre("Los Andariegos");
    participante.setTipo(TipoParticipante.ARTISTA);

    Evento conParticipante = new Evento();
    conParticipante.setId(1L);
    conParticipante.setNombre("Festival de Verano");
    conParticipante.setTipo(TipoEvento.RECITAL);
    conParticipante.setEstado(EstadoEvento.PUBLICADO);
    conParticipante.setOrganizador(organizador);
    conParticipante.setLugar(lugar);
    conParticipante.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    conParticipante.setParticipantes(new HashSet<>(java.util.List.of(participante)));
    conParticipante.setSectores(new ArrayList<>());

    Evento sinParticipante = new Evento();
    sinParticipante.setId(2L);
    sinParticipante.setNombre("Otro Evento");
    sinParticipante.setTipo(TipoEvento.RECITAL);
    sinParticipante.setEstado(EstadoEvento.PUBLICADO);
    sinParticipante.setOrganizador(organizador);
    sinParticipante.setLugar(lugar);
    sinParticipante.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    sinParticipante.setParticipantes(new HashSet<>());
    sinParticipante.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(conParticipante, sinParticipante));

    // búsqueda parcial en minúsculas
    var resultado = service.buscarEventos(
            null, null, "andariegos", null, null, null,
            null, "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
}

@Test
void busquedaPorTextoEncuentraEventosPorNombreYDescripcion() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    // coincide por nombre
    Evento porNombre = new Evento();
    porNombre.setId(1L);
    porNombre.setNombre("Festival de Verano");
    porNombre.setDescripcion("Un gran evento");
    porNombre.setTipo(TipoEvento.RECITAL);
    porNombre.setEstado(EstadoEvento.PUBLICADO);
    porNombre.setOrganizador(organizador);
    porNombre.setLugar(lugar);
    porNombre.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    porNombre.setParticipantes(new HashSet<>());
    porNombre.setSectores(new ArrayList<>());

    // coincide por descripcion
    Evento porDescripcion = new Evento();
    porDescripcion.setId(2L);
    porDescripcion.setNombre("Gran Show");
    porDescripcion.setDescripcion("El mejor festival del año");
    porDescripcion.setTipo(TipoEvento.RECITAL);
    porDescripcion.setEstado(EstadoEvento.PUBLICADO);
    porDescripcion.setOrganizador(organizador);
    porDescripcion.setLugar(lugar);
    porDescripcion.setFechaHora(java.time.LocalDateTime.of(2027, 3, 2, 20, 0));
    porDescripcion.setParticipantes(new HashSet<>());
    porDescripcion.setSectores(new ArrayList<>());

    // no coincide
    Evento sinCoincidencia = new Evento();
    sinCoincidencia.setId(3L);
    sinCoincidencia.setNombre("Otro Evento");
    sinCoincidencia.setDescripcion("Algo completamente distinto");
    sinCoincidencia.setTipo(TipoEvento.TEATRO);
    sinCoincidencia.setEstado(EstadoEvento.PUBLICADO);
    sinCoincidencia.setOrganizador(organizador);
    sinCoincidencia.setLugar(lugar);
    sinCoincidencia.setFechaHora(java.time.LocalDateTime.of(2027, 3, 3, 20, 0));
    sinCoincidencia.setParticipantes(new HashSet<>());
    sinCoincidencia.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(porNombre, porDescripcion, sinCoincidencia));

    var resultado = service.buscarEventos(
            null, null, null, null, null, null,
            "festival", "fecha", "asc", 0, 20
    );

    org.junit.jupiter.api.Assertions.assertEquals(2, resultado.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(1L, resultado.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, resultado.content().get(1).id());
}

@Test
void paginacionDevuelveSoloLaPaginaCorrecta() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    Organizador organizador = new Organizador();
    organizador.setId(1L);

    Lugar lugar = new Lugar();
    lugar.setId(1L);
    lugar.setCiudad("Tandil");

    Evento e1 = new Evento();
    e1.setId(1L);
    e1.setNombre("Evento 1");
    e1.setTipo(TipoEvento.RECITAL);
    e1.setEstado(EstadoEvento.PUBLICADO);
    e1.setOrganizador(organizador);
    e1.setLugar(lugar);
    e1.setFechaHora(java.time.LocalDateTime.of(2027, 1, 1, 20, 0));
    e1.setParticipantes(new HashSet<>());
    e1.setSectores(new ArrayList<>());

    Evento e2 = new Evento();
    e2.setId(2L);
    e2.setNombre("Evento 2");
    e2.setTipo(TipoEvento.RECITAL);
    e2.setEstado(EstadoEvento.PUBLICADO);
    e2.setOrganizador(organizador);
    e2.setLugar(lugar);
    e2.setFechaHora(java.time.LocalDateTime.of(2027, 2, 1, 20, 0));
    e2.setParticipantes(new HashSet<>());
    e2.setSectores(new ArrayList<>());

    Evento e3 = new Evento();
    e3.setId(3L);
    e3.setNombre("Evento 3");
    e3.setTipo(TipoEvento.RECITAL);
    e3.setEstado(EstadoEvento.PUBLICADO);
    e3.setOrganizador(organizador);
    e3.setLugar(lugar);
    e3.setFechaHora(java.time.LocalDateTime.of(2027, 3, 1, 20, 0));
    e3.setParticipantes(new HashSet<>());
    e3.setSectores(new ArrayList<>());

    when(eventoRepository.findAll()).thenReturn(java.util.List.of(e1, e2, e3));

    // página 0, tamaño 2 → devuelve los primeros 2
    var pagina0 = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "asc", 0, 2
    );

    org.junit.jupiter.api.Assertions.assertEquals(2, pagina0.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(3L, pagina0.totalElements());
    org.junit.jupiter.api.Assertions.assertEquals(2, pagina0.totalPages());
    org.junit.jupiter.api.Assertions.assertEquals(1L, pagina0.content().get(0).id());
    org.junit.jupiter.api.Assertions.assertEquals(2L, pagina0.content().get(1).id());

    // página 1, tamaño 2 → devuelve el tercero
    var pagina1 = service.buscarEventos(
            null, null, null, null, null, null,
            null, "fecha", "asc", 1, 2
    );

    org.junit.jupiter.api.Assertions.assertEquals(1, pagina1.content().size());
    org.junit.jupiter.api.Assertions.assertEquals(3L, pagina1.content().get(0).id());
}

@Test
void rangoDeFechasInvalidoLanzaExcepcion() {

    EventoRepository eventoRepository = mock(EventoRepository.class);
    LugarRepository lugarRepository = mock(LugarRepository.class);
    OrganizadorRepository organizadorRepository = mock(OrganizadorRepository.class);
    ParticipanteRepository participanteRepository = mock(ParticipanteRepository.class);
    EventoSectorRepository eventoSectorRepository = mock(EventoSectorRepository.class);
    SectorRepository sectorRepository = mock(SectorRepository.class);
    ReservasClient reservasClient = mock(ReservasClient.class);
    VentasClient ventasClient = mock(VentasClient.class);

    EventoService service = new EventoService(
            eventoRepository, lugarRepository, organizadorRepository,
            participanteRepository, eventoSectorRepository, sectorRepository,
            reservasClient, ventasClient
    );

    java.time.LocalDate fechaDesde = java.time.LocalDate.of(2027, 6, 1);
    java.time.LocalDate fechaHasta = java.time.LocalDate.of(2027, 1, 1);

    assertThrows(
            BadRequestException.class,
            () -> service.buscarEventos(
                    null, null, null, null, fechaDesde, fechaHasta,
                    null, "fecha", "asc", 0, 20
            )
    );

    verifyNoInteractions(eventoRepository);
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
