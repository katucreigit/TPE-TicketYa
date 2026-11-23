package com.ticketya.ventas.trazabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Primer filtro de cada solicitud (incluidas las /internal/**): conserva el X-Correlation-Id recibido o genera uno,
 * lo deja en el MDC junto con el usuario y el rol, y lo devuelve en la respuesta. Al terminar limpia el MDC para
 * que el hilo de Tomcat no lo arrastre a la solicitud siguiente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Correlacion.normalizar(request.getHeader(Correlacion.HEADER));
        if (correlationId == null) {
            correlationId = Correlacion.nuevo();
        }
        MDC.put(Correlacion.MDC_CORRELATION_ID, correlationId);
        ponerSiHay(Correlacion.MDC_USUARIO, Correlacion.limpiar(request.getHeader(Correlacion.HEADER_USUARIO)));
        ponerSiHay(Correlacion.MDC_ROL, Correlacion.limpiar(request.getHeader(Correlacion.HEADER_ROL)));
        // Antes de seguir, para que el header salga aunque la respuesta se confirme dentro de la cadena.
        response.setHeader(Correlacion.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Correlacion.MDC_CORRELATION_ID);
            MDC.remove(Correlacion.MDC_USUARIO);
            MDC.remove(Correlacion.MDC_ROL);
        }
    }

    private static void ponerSiHay(String clave, String valor) {
        if (valor != null) {
            MDC.put(clave, valor);
        }
    }
}
