# Tasks

## 1. Inventario y disponibilidad

- [x] 1.1 Las entidades ya traen UNIQUE(evento_id, sector_id, fila, numero) en Asiento, CHECK (disponibles >= 0) en InventarioSector y @Version; no rediseñarlas. Agregar a los repositorios (hoy vacíos) las consultas nativas y los @Modifying que pide el design, y reutilizar ApiException, ErrorResponse y GlobalExceptionHandler ya existentes.
- [x] 1.2 Dependencias de test en el pom: Testcontainers (postgresql y junit-jupiter) y spring-boot-testcontainers.
- [x] 1.3 POST /internal/inventarios: crear InventarioEvento (abierto, maxPorCliente con 4 por defecto), InventarioSector y un Asiento LIBRE por cada fila y número; responder 409 sin modificar nada si el inventario ya existe.
- [x] 1.4 Validaciones de la inicialización (filas, asientosPorFila, capacidad, sectores repetidos, maxPorCliente) con la operación completa en una sola transacción.
- [x] 1.5 GET /eventos/{eventoId}/disponibilidad con libres por sector.
- [x] 1.6 GET /eventos/{eventoId}/sectores/{sectorId}/asientos con filtro estado y orden por fila y número; 400 en sectores no numerados.
- [x] 1.7 POST /internal/eventos/{eventoId}/cerrar (abierto=false y liberar reservas PENDIENTE) y /abrir.
- [x] 1.8 Cliente HTTP a ms-eventos para el aviso de agotado y su reintento desde el job.
- [x] 1.9 Manejo de errores con los códigos del contrato (VALIDACION, NO_ENCONTRADO, ASIENTO_NO_DISPONIBLE, CUPO_INSUFICIENTE, EVENTO_NO_DISPONIBLE, LIMITE_POR_CLIENTE, RESERVA_CONFIRMADA, RESERVA_VENCIDA, ACCESO_DENEGADO).

## 2. Reservas

- [x] 2.1 Entidad Reserva con estados PENDIENTE, CONFIRMADA, LIBERADA y VENCIDA, e ítems con asiento o cantidad.
- [x] 2.2 POST /reservas para sectores numerados: validar pedido, tomar el advisory lock, comprobar el límite y ejecutar los UPDATE condicionales todo o nada.
- [x] 2.3 POST /reservas para sectores no numerados con la resta atómica del cupo.
- [x] 2.4 Verificar rol CLIENTE y headers de identidad en los endpoints públicos.
- [x] 2.5 GET /reservas/{id} solo para el dueño.
- [x] 2.6 DELETE /reservas/{id}: 204, idempotente para reservas ya liberadas o vencidas, 409 RESERVA_CONFIRMADA si está confirmada.
- [x] 2.7 POST /internal/reservas/{id}/confirmar (200 también si ya estaba CONFIRMADA, 409 si fue liberada, 410 si venció) y /liberar idempotente.
- [x] 2.8 POST /internal/reservas/{id}/devolver para sectores numerados y no numerados.
- [x] 2.9 Job @Scheduled de vencimiento con FOR UPDATE SKIP LOCKED.

## 3. Pruebas

- [ ] 3.1 Tests unitarios de las reglas de reserva, del límite y de los códigos de error.
- [ ] 3.2 Test de concurrencia con Testcontainers: 50 hilos reservan el mismo asiento y exactamente uno recibe 201; el resto, 409.
- [ ] 3.3 Test de concurrencia: 20 hilos piden el último cupo de un sector no numerado; exactamente uno lo obtiene y disponibles nunca es negativo.
- [ ] 3.4 Test de dos reservas simultáneas del mismo cliente que juntas superan el límite: a lo sumo una tiene éxito.
- [ ] 3.5 Test de carrera entre confirmar y vencer sobre la misma reserva: solo una operación se aplica.
- [ ] 3.6 Test de todo o nada: una reserva con un asiento ocupado no deja ningún asiento reservado.
- [ ] 3.7 Test del job de vencimiento invocado directamente, incluida la devolución del cupo en sectores no numerados.
- [ ] 3.8 Tests de controlador para 201, 204, 400, 403, 404, 409, 410 y 422.
