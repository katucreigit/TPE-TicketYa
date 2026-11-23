package com.ticketya.recomendaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsRecomendacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsRecomendacionesApplication.class, args);
    }
}
