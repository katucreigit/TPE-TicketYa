# Tasks

## 1. Inventario

- [ ] 1.1 Entidades Asiento e InventarioSector con restricciones únicas y CHECK.
- [ ] 1.2 Generación de asientos al recibir la publicación del evento.
- [ ] 1.3 Endpoint de disponibilidad por evento y sector.

## 2. Reservas

- [ ] 2.1 Entidad Reserva con estados PENDIENTE, CONFIRMADA, EXPIRADA y CANCELADA.
- [ ] 2.2 Endpoint interno de crear reserva con UPDATE condicional todo o nada.
- [ ] 2.3 Validación del límite por cliente.
- [ ] 2.4 Endpoints internos de confirmar y liberar.
- [ ] 2.5 Job programado de vencimiento.

## 3. Pruebas

- [ ] 3.1 Tests unitarios de reglas de reserva y límite.
- [ ] 3.2 Test de concurrencia: N hilos reservan el mismo asiento y solo uno tiene éxito.
- [ ] 3.3 Test de concurrencia en sector general sin sobreventa.
- [ ] 3.4 Test de carrera entre confirmar y vencer.
