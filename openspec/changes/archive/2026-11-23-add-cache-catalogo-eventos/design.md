# Design: Caché del catálogo y detalle de eventos

## Contexto

El microservicio `ms-eventos` permite consultar el catálogo público de eventos y el detalle de cada evento. Estas consultas pueden repetirse y generar carga innecesaria sobre la base de datos.

La Entrega 2 requiere incorporar caché con tiempo de vida e invalidación, evitando almacenar información dinámica relacionada con disponibilidad.

## Objetivos

- Reducir consultas repetidas a la base de datos.
- Cachear las búsquedas del catálogo y los detalles públicos.
- Establecer un TTL configurable.
- Mantener la información actualizada mediante invalidaciones.
- No cachear disponibilidad, inventario, sectores ni estados de asientos.

## Tecnología elegida

Se utilizará **Spring Cache con Caffeine**.

Caffeine permite mantener una caché en memoria dentro de `ms-eventos`, sin incorporar un servicio externo ni modificar Docker Compose.

La configuración se realizará mediante un `CaffeineCacheManager` y un `TransactionAwareCacheManagerProxy`, de manera que las operaciones de escritura e invalidación de caché respeten las transacciones de Spring.

La solución está pensada para una instancia del microservicio. Si en el futuro se despliegan varias instancias, deberá evaluarse una caché distribuida como Redis.

## Cachés

Se definirán dos cachés:

| Nombre | Contenido | TTL inicial |
|---|---|---|
| `catalogoEventos` | Resultados de búsquedas públicas del catálogo | 5 minutos |
| `detalleEvento` | Respuestas de detalle de eventos públicos | 5 minutos |

El TTL será configurable desde `application.yml`, sin necesidad de modificar código Java.

### Catálogo

Se cachearán los resultados de `buscarEventos()`.

La clave deberá contemplar todos los parámetros de búsqueda, incluyendo:

- Tipo de evento.
- Organizador.
- Participante.
- Ciudad.
- Rango de fechas.
- Texto de búsqueda.
- Orden y dirección.
- Número de página y tamaño.

De esta manera, búsquedas con filtros o paginaciones diferentes no compartirán incorrectamente una misma entrada.

### Detalle

Se cachearán los resultados de `obtenerEvento()` únicamente cuando el evento sea visible públicamente.

Los eventos en estado `BORRADOR` no deberán almacenarse en caché, ya que su consulta puede depender del organizador propietario o de un administrador.

La clave será el identificador del evento. Las respuestas públicas podrán compartirse entre usuarios porque no contienen información privada dependiente del solicitante.

## Información excluida

No se utilizará caché para:

- Disponibilidad de entradas o cupos.
- Inventario de reservas.
- Sectores y capacidades.
- Mapas de asientos.
- Estados individuales de asientos.

Estos datos pueden cambiar durante el proceso de reserva y compra, por lo que deben consultarse mediante los mecanismos actuales.

## Política de invalidación

Las invalidaciones se realizarán después de que la transacción de base de datos correspondiente se confirme.

Se utilizará el administrador de caché consciente de transacciones para evitar invalidaciones prematuras ante un rollback.

### Operaciones de eventos

| Operación | Invalidación |
|---|---|
| Crear evento en borrador | Ninguna |
| Modificar evento en borrador | Ninguna |
| Configurar precios de un borrador | Ninguna |
| Publicar evento | Catálogo completo y detalle del evento |
| Suspender evento | Catálogo completo y detalle del evento |
| Reanudar evento | Catálogo completo y detalle del evento |
| Cancelar evento | Catálogo completo y detalle del evento |
| Actualizar disponibilidad cuando cambia el estado | Catálogo completo y detalle del evento |

Las operaciones sobre borradores no requieren invalidación porque los borradores no forman parte del catálogo público ni se almacenan en la caché de detalle.

### Finalización automática

`EventoFinalizacionJob` modifica directamente el estado de los eventos cuando su fecha y hora ya pasaron.

Si el job finaliza uno o más eventos, deberá invalidar el catálogo completo y la caché completa de detalles.

Si no encuentra eventos para finalizar, no deberá realizar invalidaciones.

La invalidación deberá quedar vinculada a la confirmación de la transacción del job.

## Configuración

Se incorporará la dependencia de Caffeine y se habilitará Spring Cache en `ms-eventos`.

Los tiempos de expiración se definirán mediante propiedades configurables en `application.yml`.

No será necesario incorporar Redis ni modificar `docker-compose.yml`.

## Pruebas

Se deberán verificar los siguientes comportamientos:

1. Una consulta repetida del catálogo reutiliza el resultado cacheado.
2. Búsquedas con parámetros diferentes utilizan claves diferentes.
3. Una consulta repetida del detalle público reutiliza el resultado cacheado.
4. Los eventos en borrador no quedan almacenados en caché.
5. Las entradas expiran al cumplirse el TTL configurado.
6. Publicar, suspender, reanudar o cancelar un evento invalida las entradas correspondientes.
7. Un cambio de disponibilidad que modifica el estado invalida las entradas correspondientes.
8. La finalización automática invalida ambas cachés cuando modifica eventos.
9. Si una transacción se revierte, no se debe invalidar prematuramente la caché.
10. Los endpoints de sectores y disponibilidad no utilizan caché.

## Fuera de alcance

- Cachear disponibilidad o inventario.
- Cachear sectores, mapas o estados de asientos.
- Incorporar Redis u otra caché distribuida.
- Modificar contratos HTTP existentes.
- Incorporar caché en `api-gateway`.
