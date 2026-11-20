# Tasks

## 1. Imágenes por servicio

- [x] 1.1 Crear `ms-eventos/Dockerfile` multi-stage (D1, D3) y `ms-eventos/.dockerignore` (D8); verificar con `docker build -t ticketya/ms-eventos ms-eventos` que termina sin errores y que `docker run --rm --entrypoint java ticketya/ms-eventos -version` informa Java 21.
- [x] 1.2 Crear `ms-reservas/Dockerfile` y `ms-reservas/.dockerignore` con la misma estructura (puerto 8082); verificar con `docker build -t ticketya/ms-reservas ms-reservas`.
- [x] 1.3 Crear `ms-ventas/Dockerfile` y `ms-ventas/.dockerignore` (puerto 8083); verificar con `docker build -t ticketya/ms-ventas ms-ventas`.
- [x] 1.4 Crear `api-gateway/Dockerfile` y `api-gateway/.dockerignore` (puerto 8080); verificar con `docker build -t ticketya/api-gateway api-gateway`.
- [x] 1.5 Verificar que la imagen final no trae Maven ni fuentes: `docker run --rm --entrypoint sh ticketya/ms-eventos -c "which mvn; ls /app"` no encuentra `mvn` y lista solo `app.jar`.
- 1.6 (Omitida, sin checkbox a propósito.) Verificar ejecución por separado con `docker run` de una imagen sola. **Motivo:** el equipo decidió no probarla; se quitó el ejemplo de `docker run` del README para no documentar algo sin verificar. Cada imagen sí se verificó ejecutándose dentro de compose (2.4, 2.5, 3.1), pero el escenario "Ejecutar la imagen de un servicio por separado" de la spec queda sin verificación manual.
- [x] 1.7 Confirmar que `mvn test` sigue pasando en los cuatro servicios (sin modificar tests), dado que el build de imagen usa `-DskipTests` (D2).

## 2. Docker Compose

- [x] 2.1 Agregar `ms-eventos`, `ms-reservas` y `ms-ventas` a `docker-compose.yml` con `build`, `image`, `container_name`, puertos 8081–8083, variables de D6 y `depends_on` de su base con `service_healthy` (D5); actualizar el comentario de cabecera del archivo. Verificar con `docker compose config` sin errores.
- [x] 2.2 Agregar `api-gateway` con puerto 8080, `MS_*_URL` por nombre de servicio y `depends_on` de los tres microservicios con `service_started`; verificar con `docker compose config`.
- [x] 2.3 Verificar que `docker compose up -d db-eventos db-reservas db-ventas` levanta solo las bases y que un servicio ejecutado desde el IDE sin variables se conecta a `localhost` como antes.
- [x] 2.4 Verificar con `docker compose down -v; docker compose up -d --build` que quedan 7 contenedores en ejecución (`docker compose ps`), que los microservicios arrancaron después de que sus bases quedaron healthy y el gateway después de los microservicios (orden en `docker compose logs`).
- [x] 2.5 Verificar puertos publicados: `GET http://localhost:8080/api/eventos`, `GET http://localhost:8081/eventos`, `8082` y `8083` responden, y las bases aceptan conexión en 5433–5435.
- [x] 2.6 Actualizar `README.md`: sección de despliegue con `docker compose up -d --build`, levantar solo las bases para desarrollo local, detener servicios del IDE antes del levantamiento completo, `docker compose down -v` para empezar con bases vacías, y estructura del repo (`docker-compose.yml` ya no es solo bases). Verificar que los comandos documentados corren tal cual.

## 3. Verificación integral

- [x] 3.1 Con bases vacías (`docker compose down -v; docker compose up -d --build`) y los servicios ya iniciados, ejecutar la carpeta "Flujo completo (en orden)" de `postman/TicketYa.postman_collection.json` con `baseUrl=http://localhost:8080/api` (Runner o `newman`) y verificar 15/15 pruebas aprobadas.
- [x] 3.2 Verificar una llamada interna entre contenedores: tras publicar un evento en el flujo, `docker compose logs ms-eventos` no muestra errores de conexión a `localhost:8082` y el inventario existe en `ms-reservas`.
- [x] 3.3 Ejecutar `openspec validate --all --strict` sin errores.

## Workflow follow-up

- Línea de alcance de `AGENTS.md` actualizada a "Entrega 2 (sistema completo)".
- Archivar el cambio con `/opsx:archive` una vez revisado.
