package com.ticketya.ventas.controller;

import com.ticketya.ventas.controller.Identidad.Solicitante;
import com.ticketya.ventas.dto.CompraRequest;
import com.ticketya.ventas.dto.CompraResponse;
import com.ticketya.ventas.dto.PageCompraResponse;
import com.ticketya.ventas.service.CompraService;
import com.ticketya.ventas.service.CompraService.ResultadoCompra;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints publicos de compras (tag Compras de openapi.yaml). */
@RestController
@RequestMapping("/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @PostMapping
    public ResponseEntity<CompraResponse> crear(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestHeader(value = Identidad.HEADER_IDEMPOTENCY, required = false) String idempotencyKey,
            @Valid @RequestBody CompraRequest request) {
        String key = Identidad.idempotencyKey(idempotencyKey);
        String clienteId = Identidad.cliente(userId, rol);
        ResultadoCompra resultado = compraService.crear(clienteId, key, request);
        HttpStatus status = resultado.repeticion() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resultado.compra());
    }

    @GetMapping
    public PageCompraResponse listar(
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String clienteId = Identidad.cliente(userId, rol);
        Identidad.validarPaginacion(page, size);
        return compraService.listarDelCliente(clienteId, page, size);
    }

    @GetMapping("/{id}")
    public CompraResponse obtener(
            @PathVariable UUID id,
            @RequestHeader(value = Identidad.HEADER_ID, required = false) String userId,
            @RequestHeader(value = Identidad.HEADER_ROL, required = false) String rol) {
        Solicitante solicitante = Identidad.clienteOAdmin(userId, rol);
        return compraService.obtener(id, solicitante.userId(), solicitante.esAdmin());
    }
}
