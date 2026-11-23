package com.ticketya.eventos.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${ticketya.cache.ttl-minutes:5}") long ttlMinutes) {

        CaffeineCacheManager caffeineCacheManager =
                new CaffeineCacheManager("catalogoEventos", "detalleEvento");

        caffeineCacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                        .maximumSize(1000)
        );

        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
}
