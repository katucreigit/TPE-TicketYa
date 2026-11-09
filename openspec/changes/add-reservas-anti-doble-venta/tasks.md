# Tasks

## 1. Inventario

- [ ] 1.1 Entidades Asiento e InventarioSector con UNIQUE y CHECK.
- [ ] 1.2 Endpoint interno de inicialización idempotente del inventario.
- [ ] 1.3 Endpoint de disponibilidad por evento y mapa de asientos.
- [ ] 1.4 Endpoint interno de cierre y reapertura del inventario.

## 2. Reservas

- [ ] 2.1 Entidad Reserva con estados PENDIENTE, CONFIRMADA, LIBERADA y VENCIDA.
- [ ] 2.2 Crear reserva con UPDATE condicional todo o nada, guardando el precio de cada lugar.
- [ ] 2.3 Validar cantidad mayor a 0, asientos sin repetir y evento PUBLICADO.
- [ ] 2.4 Validar el límite por cliente en la base, usando el valor que ms-eventos envía al publicar.
- [ ] 2.5 Endpoints de confirmar (interno) y cancelar (DELETE) con 404, 403 y 409.
- [ ] 2.6 Job programado de vencimiento a los 10 minutos.

## 3. Pruebas

- [ ] 3.1 Tests unitarios de las reglas de reserva y del límite.
- [ ] 3.2 Test de concurrencia: N hilos reservan el mismo asiento y exactamente uno tiene éxito.
- [ ] 3.3 Test de concurrencia por el último cupo de un sector no numerado.
- [ ] 3.4 Test de dos reservas simultáneas del mismo cliente contra el límite.
- [ ] 3.5 Test de carrera entre confirmar y vencer.
