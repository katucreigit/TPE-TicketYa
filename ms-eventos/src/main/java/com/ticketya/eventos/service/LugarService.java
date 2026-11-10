package com.ticketya.eventos.service;

import com.ticketya.eventos.dto.*;
import com.ticketya.eventos.exception.BadRequestException;
import com.ticketya.eventos.exception.NotFoundException;
import com.ticketya.eventos.model.Lugar;
import com.ticketya.eventos.model.Sector;
import com.ticketya.eventos.model.enums.TipoSector;
import com.ticketya.eventos.repository.LugarRepository;
import com.ticketya.eventos.repository.SectorRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LugarService {

    private final LugarRepository lugarRepository;
    private final SectorRepository sectorRepository;

    public LugarService(LugarRepository lugarRepository,
                        SectorRepository sectorRepository) {
        this.lugarRepository = lugarRepository;
        this.sectorRepository = sectorRepository;
    }

    @Transactional
    public LugarResponse crear(LugarRequest request) {
        Lugar lugar = new Lugar();

        lugar.setNombre(request.nombre());
        lugar.setDireccion(request.direccion());
        lugar.setCiudad(request.ciudad());
        lugar.setLatitud(request.latitud());
        lugar.setLongitud(request.longitud());

        if (request.sectores() != null) {
            Set<String> nombres = new HashSet<>();

            for (SectorRequest sectorRequest : request.sectores()) {
                validarSector(sectorRequest);

                String nombreNormalizado =
                        sectorRequest.nombre().trim().toLowerCase(Locale.ROOT);

                if (!nombres.add(nombreNormalizado)) {
                    throw new BadRequestException(
                            "SECTOR_DUPLICADO",
                            "No puede haber sectores con nombres repetidos."
                    );
                }

                lugar.agregarSector(convertirSector(sectorRequest));
            }
        }

        Lugar guardado = lugarRepository.save(lugar);
        return convertirLugar(guardado);
    }

    @Transactional(readOnly = true)
    public PageLugarResponse listar(String ciudad, int page, int size) {
        if (page < 0 || size < 1) {
            throw new BadRequestException(
                    "PAGINACION_INVALIDA",
                    "La página debe ser mayor o igual a cero y el tamaño mayor a cero."
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Lugar> resultado;

        if (ciudad == null || ciudad.isBlank()) {
            resultado = lugarRepository.findAll(pageable);
        } else {
            resultado = lugarRepository.findByCiudadIgnoreCase(
                    ciudad.trim(), pageable
            );
        }

        List<LugarResponse> lugares = resultado.getContent()
                .stream()
                .map(this::convertirLugar)
                .toList();

        return new PageLugarResponse(
                lugares,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public LugarResponse obtener(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "LUGAR_NO_ENCONTRADO",
                        "No se encontró el lugar con ID " + id
                ));

        return convertirLugar(lugar);
    }

    @Transactional
    public SectorResponse agregarSector(Long lugarId, SectorRequest request) {
        Lugar lugar = lugarRepository.findById(lugarId)
                .orElseThrow(() -> new NotFoundException(
                        "LUGAR_NO_ENCONTRADO",
                        "No se encontró el lugar con ID " + lugarId
                ));

        validarSector(request);

        String nombreNormalizado = request.nombre().trim().toLowerCase(Locale.ROOT);

boolean repetido = lugar.getSectores().stream()
        .anyMatch(s -> s.getNombre().trim().toLowerCase(Locale.ROOT)
                .equals(nombreNormalizado));

        if (repetido) {
            throw new BadRequestException(
                    "SECTOR_DUPLICADO",
                    "Ya existe un sector con ese nombre en el lugar."
            );
        }

        Sector sector = convertirSector(request);
        lugar.agregarSector(sector);

        lugarRepository.save(lugar);

        return convertirSectorResponse(sector);
    }

    private void validarSector(SectorRequest request) {
        if (request.tipo() == TipoSector.NUMERADO) {
            if (request.filas() == null || request.asientosPorFila() == null) {
                throw new BadRequestException(
                        "DATOS_SECTOR_INVALIDOS",
                        "Un sector numerado debe indicar filas y asientos por fila."
                );
            }

            if (request.capacidad() != null) {
                throw new BadRequestException(
                        "DATOS_SECTOR_INVALIDOS",
                        "Un sector numerado no debe indicar capacidad."
                );
            }
        } else if (request.tipo() == TipoSector.NO_NUMERADO) {
            if (request.capacidad() == null) {
                throw new BadRequestException(
                        "DATOS_SECTOR_INVALIDOS",
                        "Un sector no numerado debe indicar capacidad."
                );
            }

            if (request.filas() != null || request.asientosPorFila() != null) {
                throw new BadRequestException(
                        "DATOS_SECTOR_INVALIDOS",
                        "Un sector no numerado no debe indicar filas ni asientos por fila."
                );
            }
        }
    }

    private Sector convertirSector(SectorRequest request) {
        Sector sector = new Sector();

        sector.setNombre(request.nombre().trim());
        sector.setTipo(request.tipo());
        sector.setCapacidad(request.capacidad());
        sector.setFilas(request.filas());
        sector.setAsientosPorFila(request.asientosPorFila());

        return sector;
    }

    private LugarResponse convertirLugar(Lugar lugar) {
        List<SectorResponse> sectores = lugar.getSectores()
                .stream()
                .map(this::convertirSectorResponse)
                .toList();

        return new LugarResponse(
                lugar.getId(),
                lugar.getNombre(),
                lugar.getDireccion(),
                lugar.getCiudad(),
                lugar.getLatitud(),
                lugar.getLongitud(),
                sectores
        );
    }

    private SectorResponse convertirSectorResponse(Sector sector) {
        return new SectorResponse(
                sector.getId(),
                sector.getNombre(),
                sector.getTipo(),
                sector.getCapacidad(),
                sector.getFilas(),
                sector.getAsientosPorFila()
        );
    }
}
