package com.ticketya.reservas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita los jobs programados. Los tests lo desactivan e invocan los metodos directamente. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ticketya.jobs.habilitados", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
