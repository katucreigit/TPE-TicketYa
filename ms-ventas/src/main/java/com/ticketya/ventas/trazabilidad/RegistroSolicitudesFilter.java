package com.ticketya.ventas.trazabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Un registro por solicitud HTTP con metodo, ruta, usuario, rol, codigo de respuesta, tiempo en ms y correlationId,
 * con el mismo formato clave=valor que el HTTP_REQUEST del gateway y ms-reservas. Corre despues de
 * {@link CorrelationIdFilter}, asi que el MDC ya tiene el correlationId. Si una excepcion sale de la cadena sin
 * manejar, registra 500.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RegistroSolicitudesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RegistroSolicitudesFilter.class);
    private static final String ANONIMO = "ANONIMO";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        boolean fallo = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            fallo = true;
            throw ex;
        } finally {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            int status = fallo ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            log.info("HTTP_REQUEST method={} endpoint={} user={} rol={} status={} durationMs={} correlationId={}",
                    request.getMethod(),
                    Correlacion.limpiar(request.getRequestURI()),
                    valorO(MDC.get(Correlacion.MDC_USUARIO)),
                    valorO(MDC.get(Correlacion.MDC_ROL)),
                    status,
                    duracionMs,
                    Correlacion.actual());
        }
    }

    private static String valorO(String valor) {
        return valor == null ? ANONIMO : valor;
    }
}
