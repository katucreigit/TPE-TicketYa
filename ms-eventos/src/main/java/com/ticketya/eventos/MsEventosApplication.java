package com.ticketya.eventos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MsEventosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsEventosApplication.class, args);
    }
}
