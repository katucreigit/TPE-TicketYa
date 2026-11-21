# Tasks

> La implementación de los grupos 1 y 2 ya está en el working tree desde la resolución del merge con `origin/main`; cada tarea se marca cuando su verificación se ejecute en la fase de apply.

## 1. Imagen y configuración de ms-auth

- [ ] 1.1 `ms-auth/Dockerfile` multi-stage (D1, `EXPOSE 8084`) y `ms-auth/.dockerignore` iguales a los demás; verificar que la imagen `ticketya/ms-auth` existe tras `docker compose up --build -d` y que `docker run --rm --entrypoint sh ticketya/ms-auth -c "java -version 2>&1 | head -1; which mvn || echo sin mvn; ls /app"` informa Java 21, sin Maven y solo `app.jar`.
- [ ] 1.2 `ms-auth/application.yml` con `${DB_URL:...}`, `${DB_USER:...}`, `${DB_PASSWORD:...}` y `${JWT_SECRET:...}` (D2, D3), y `api-gateway/application.yml` con `${JWT_SECRET:...}`, todos con los valores por defecto anteriores; verificar con `git diff origin/main -- ms-auth/src/main/resources/application.yml api-gateway/src/main/resources/application.yml` que solo cambian esas líneas y que la clave por defecto es idéntica en los dos.

## 2. Compose y documentación

- [ ] 2.1 `docker-compose.yml` con `db-auth` (de main), `ms-auth` (`depends_on` `db-auth` healthy, `DB_URL` por nombre de servicio, `JWT_SECRET`, puerto 8084) y el gateway con `JWT_SECRET`, `MS_AUTH_URL=http://ms-auth:8084` y `depends_on` de los cuatro microservicios; verificar con `docker compose config` sin errores y 9 servicios listados.
- [ ] 2.2 README: 9 contenedores, comando de solo bases con `db-auth`, "Started" en los cinco servicios y la fila `JWT_SECRET` en la tabla de configuración; verificar que no quedan marcadores de conflicto (`<<<<<<<`, `=======`, `>>>>>>>`) y que los comandos documentados coinciden con el compose.
- [ ] 2.3 Actualizar la línea "Solo bases de datos" de `AGENTS.md` a `docker compose up -d db-eventos db-reservas db-ventas db-auth` y la del sistema completo para incluir 8084 y 5436; verificar leyendo el archivo.

## 3. Verificación integral

- [ ] 3.1 Con `docker compose up --build -d`, verificar con `docker compose ps` 9 contenedores en ejecución, las 4 bases healthy, y por `StartedAt` que `ms-auth` arrancó después de `db-auth` y el gateway después de los cuatro microservicios.
- [ ] 3.2 Verificar puertos: `localhost:8084` responde (p. ej. `POST /auth/login` con credenciales inválidas devuelve 401) y `localhost:5436` acepta conexión.
- [ ] 3.3 A través del gateway: `POST /api/auth/register` devuelve 201, `POST /api/auth/login` devuelve 200 con token y con contraseña incorrecta 401; `GET /api/entradas` devuelve 401 sin token y con token inválido, y 200 con el token emitido.
- [ ] 3.4 Verificar la sobreescritura del secreto sin reconstruir imágenes: con `JWT_SECRET` definido en el shell, `docker compose config` muestra ese valor en `ms-auth` y en `api-gateway`.
- [ ] 3.5 Ejecutar `openspec validate --all --strict` sin errores.

## Workflow follow-up

- Fuera de alcance, para el cambio de JWT: la colección de Postman de main no tiene pruebas, los logins no guardan el token y el flujo necesita usuarios registrados antes; el requisito "Flujo completo contra los contenedores" no se puede verificar hasta que eso se resuelva.
- Fuera de alcance, para el cambio de JWT: `ApiGatewayApplicationTests.debeTenerConfiguradasLasCuatroRutas` espera 4 rutas y ahora hay 5.
- Archivar con `/opsx:archive add-auth-docker-compose` después de cerrar el merge.
