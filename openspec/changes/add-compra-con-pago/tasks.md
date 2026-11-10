# Tasks

## 1. Modelo

- [ ] 1.1 Entidades Compra, Entrada y Pago con estados y snapshots de precio y fecha.
- [ ] 1.2 Tabla de idempotencia con restricción única y hash del cuerpo.

## 2. Integraciones

- [x] 2.1 Cliente HTTP a ms-reservas (validar, confirmar, liberar).
- [ ] 2.2 Cliente HTTP a ms-eventos para estado, precios y fecha del evento.
- [x] 2.3 Servicio de pasarela mock con los tres tokens, que recibe una clave de cobro y devuelve el resultado original si se repite.

## 3. Compra

- [x] 3.1 Endpoint POST /compras con Idempotency-Key obligatoria.
- [ ] 3.2 Orquestación de la saga con compensaciones y reembolso.
- [ ] 3.3 Derivar la clave de cobro del id de la compra y mantener la compra PENDIENTE tras un 502 o 504.
- [ ] 3.4 Emisión de entradas con QR único.
- [ ] 3.5 Endpoints de consulta de compras y entradas del cliente.

## 4. Pruebas

- [ ] 4.1 Tests de saga: pago aprobado, rechazado, timeout, reserva vencida antes y durante el cobro.
- [ ] 4.2 Tests de idempotencia: repetición, cuerpo distinto, sin clave y solicitudes simultáneas.
- [ ] 4.3 Test de tok_timeout: el reintento con la misma clave no cobra dos veces.
- [ ] 4.4 Test de flujo completo exitoso.
- [ ] 4.5 Test de que otro cliente recibe 403.
