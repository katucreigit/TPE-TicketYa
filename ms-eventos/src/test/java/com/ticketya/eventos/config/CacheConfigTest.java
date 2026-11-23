
package com.ticketya.eventos.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicLong;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CacheConfigTest {

    @Test
    void entradaExpiraCuandoElTtlEsCero() {

        CacheManager cacheManager = new CacheConfig().cacheManager(0);

        Cache cache = cacheManager.getCache("catalogoEventos");

        cache.put("clave-prueba", "valor");

        assertNull(cache.get("clave-prueba"));
    }

    @Test
    void seCreanLasDosCachesConfiguradas() {

        CacheManager cacheManager = new CacheConfig().cacheManager(5);

        assertNotNull(cacheManager.getCache("catalogoEventos"));
        assertNotNull(cacheManager.getCache("detalleEvento"));
    }

    @Test
    void rollbackNoEjecutaLaInvalidacionDeCache() {

        CacheManager cacheManager = new CacheConfig().cacheManager(5);
        Cache cache = cacheManager.getCache("catalogoEventos");

        cache.put("clave-prueba", "valor");

        TransactionTemplate transaction = new TransactionTemplate(
                new TestTransactionManager()
        );

        transaction.execute(status -> {
            cache.clear();

            assertNotNull(cache.get("clave-prueba"));

            status.setRollbackOnly();
            return null;
        });

        assertNotNull(cache.get("clave-prueba"));
    }


    @Test
    void entradaExpiraDespuesDeCincoMinutos() {

        AtomicLong tiempo = new AtomicLong(0);

        Ticker reloj = tiempo::get;

        CacheConfig configuracion = new CacheConfig();

        CacheManager cacheManager =
                configuracion.crearCacheManager(5, reloj);

        Cache cache = cacheManager.getCache("catalogoEventos");

        cache.put("clave-prueba", "valor");

        assertNotNull(cache.get("clave-prueba"));

        tiempo.addAndGet(TimeUnit.MINUTES.toNanos(5));

        assertNull(cache.get("clave-prueba"));
    }


    @Test
    void commitEjecutaLaInvalidacionDeCache() {

        CacheManager cacheManager = new CacheConfig().cacheManager(5);
        Cache cache = cacheManager.getCache("catalogoEventos");

        cache.put("clave-prueba", "valor");

        TransactionTemplate transaction = new TransactionTemplate(
                new TestTransactionManager()
        );

        transaction.execute(status -> {
            cache.clear();

            assertNotNull(cache.get("clave-prueba"));

            return null;
        });

        assertNull(cache.get("clave-prueba"));
    }

    static class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}


