# Tasks: Caché del catálogo y detalle de eventos

## 1. Configuración de la caché

- [x] 1.1. Incorporar la dependencia Caffeine en `ms-eventos/pom.xml`.
- [x] 1.2. Crear la configuración de Spring Cache con dos cachés: `catalogoEventos` y `detalleEvento`.
- [x] 1.3. Configurar el TTL de ambas cachés mediante propiedades de `application.yml`, con un valor inicial de 5 minutos.
- [x] 1.4. Configurar un administrador de caché compatible con las transacciones de Spring.
- [x] 1.5. Verificar que la aplicación arranque correctamente con la nueva configuración.

## 2. Caché del catálogo

- [x] 2.1. Incorporar caché en el método `buscarEventos()`.
- [x] 2.2. Asegurar que la clave contemple todos los filtros, parámetros de orden y paginación.
- [x] 2.3. Verificar que consultas con parámetros diferentes no compartan resultados incorrectamente.
- [x] 2.4. Confirmar que únicamente se cacheen resultados del catálogo público.

## 3. Caché del detalle

- [x] 3.1. Incorporar caché en el método `obtenerEvento()`.
- [x] 3.2. Evitar almacenar eventos en estado `BORRADOR`.
- [x] 3.3. Utilizar el identificador del evento como clave de caché.
- [x] 3.4. Verificar que las respuestas públicas no dependan de información privada del usuario solicitante.
- [x] 3.5. Verificar que una consulta repetida del mismo detalle público reutilice el resultado almacenado.

## 4. Exclusiones

- [x] 4.1. Verificar que los métodos de disponibilidad no utilicen caché.
- [x] 4.2. Verificar que los métodos de consulta de sectores no utilicen caché.
- [x] 4.3. Confirmar que no se almacenen inventarios, capacidades, mapas ni estados individuales de asientos.
## 5. Invalidación

- [x] 5.1. Invalidar catálogo y detalle al publicar un evento.
- [x] 5.2. Invalidar catálogo y detalle al suspender, reanudar o cancelar un evento.
- [x] 5.3. Invalidar catálogo y detalle cuando una actualización de disponibilidad cambie el estado del evento.
- [x] 5.4. Integrar la invalidación con `EventoFinalizacionJob`.
- [x] 5.5. Invalidar el catálogo completo y todos los detalles almacenados cuando el job finalice uno o más eventos.
- [x] 5.6. Evitar invalidaciones innecesarias cuando el job no finalice ningún evento.
- [x] 5.7. Garantizar que las invalidaciones se ejecuten después de confirmar las transacciones.

## 6. Pruebas

- [x] 6.1. Probar que una consulta repetida reutilice el resultado cacheado.
- [x] 6.2. Probar que filtros y paginaciones diferentes generen entradas independientes.
- [x] 6.3. Probar que los eventos en borrador no se almacenen.
- [x] 6.4. Probar la reutilización de una consulta repetida del detalle público.
- [x] 6.5. Probar la expiración de las entradas según el TTL.
- [x] 6.6. Probar la invalidación después de las operaciones que modifican eventos.
- [x] 6.7. Probar que el job invalide ambas cachés cuando finalice eventos.
- [x] 6.8. Probar que el job no invalide las cachés cuando no finalice ningún evento.
- [x] 6.9. Probar que un rollback no provoque una invalidación prematura.
- [x] 6.10. Verificar que disponibilidad y sectores no utilicen caché.

## 7. Validación final

- [x] 7.1. Ejecutar `mvn test` dentro de `ms-eventos`.
- [x] 7.2. Ejecutar `mvn compile` dentro de `ms-eventos`.
- [x] 7.3. Revisar los cambios y comprobar que no se modificaron contratos HTTP existentes.
- [x] 7.4. Ejecutar `openspec validate add-cache-catalogo-eventos --strict`.
