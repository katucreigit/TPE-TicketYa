# Tasks

## 1. Devolución

- [ ] 1.1 Endpoint de devolver entrada con validación de dueño, estado y plazo.
- [ ] 1.2 Registrar Reintegro con restricción UNIQUE(entrada_id, tipo).
- [ ] 1.3 Llamar a ms-reservas para liberar el asiento.

## 2. Cancelación

- [ ] 2.1 Endpoint de cancelar evento en ms-eventos que dispara el proceso.
- [ ] 2.2 Proceso de reintegro masivo con reintento de fallidos.

## 3. Pruebas

- [ ] 3.1 Tests de la regla de 72 h en el límite.
- [ ] 3.2 Test de doble devolución de la misma entrada.
- [ ] 3.3 Test de cancelación con un reintegro fallido y reintento.
