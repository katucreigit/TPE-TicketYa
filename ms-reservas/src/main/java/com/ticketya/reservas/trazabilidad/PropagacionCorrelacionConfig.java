package com.ticketya.reservas.trazabilidad;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propaga el correlationId en todas las llamadas HTTP salientes: Spring Boot aplica este customizer a cada
 * RestClient.Builder que inyecta (hoy, el de EventosClient), asi que un cliente nuevo lo hereda sin configurar nada.
 */
@Configuration(proxyBeanMethods = false)
public class PropagacionCorrelacionConfig {

    @Bean
    RestClientCustomizer propagarCorrelationId() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String correlationId = Correlacion.actual();
            if (correlationId != null) {
                request.getHeaders().set(Correlacion.HEADER, correlationId);
            }
            return execution.execute(request, body);
        });
    }
}
