# Tasks

## 1. Modelo

- [ ] 1.1 Entidades Orden, Entrada y Pago con estados y snapshots.
- [ ] 1.2 Tabla de idempotencia con restricción única.

## 2. Integraciones

- [ ] 2.1 Cliente HTTP a ms-reservas (reservar, confirmar, liberar).
- [ ] 2.2 Cliente HTTP a ms-eventos para datos del evento.
- [ ] 2.3 Servicio de pago mock con los tres tokens.

## 3. Saga

- [ ] 3.1 Orquestador de compra con compensaciones.
- [ ] 3.2 Generación de código QR por entrada.
- [ ] 3.3 Endpoints de consulta de órdenes y entradas.

## 4. Pruebas

- [ ] 4.1 Tests de saga: pago rechazado, timeout y falla al confirmar.
- [ ] 4.2 Test de idempotencia (mismo y distinto cuerpo).
- [ ] 4.3 Test de flujo completo exitoso.
