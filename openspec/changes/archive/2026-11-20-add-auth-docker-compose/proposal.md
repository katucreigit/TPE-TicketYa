# Proposal: ms-auth y db-auth en el despliegue con Docker Compose

## Why

El merge de `origin/main` en `feat/docker-compose` trajo `ms-auth` (registro, login y emisión de JWT, puerto 8084) con su base `db-auth` (5436), y la validación de JWT en el gateway. La capacidad `despliegue-contenedores` sigue describiendo 7 contenedores y 3 microservicios, por lo que la spec ya no refleja el sistema. Además, para que el JWT funcione en contenedores, el gateway y `ms-auth` tienen que compartir el mismo secreto y el gateway tiene que encontrar a `ms-auth` por nombre de servicio.

Durante la resolución del merge ya se implementó lo necesario; este cambio lo formaliza en la spec y deja tareas para verificarlo.

## What Changes

- `ms-auth` pasa a tener su propia imagen (Dockerfile multi-stage y `.dockerignore`, igual que los demás servicios).
- El compose levanta 9 contenedores: 4 bases (`db-eventos`, `db-reservas`, `db-ventas`, `db-auth`), 4 microservicios y el gateway.
- `ms-auth` espera a que `db-auth` esté saludable y se conecta por nombre de servicio con `DB_URL`, `DB_USER` y `DB_PASSWORD`.
- El gateway recibe `MS_AUTH_URL` y arranca después de los cuatro microservicios.
- El secreto JWT se pasa como `JWT_SECRET` al gateway y a `ms-auth`, con un valor por defecto de desarrollo igual al de sus `application.yml`; un secreto real se define fuera del repositorio.
- `ms-auth/application.yml` y `api-gateway/application.yml` leen esas variables con `localhost` y la clave de desarrollo como valores por defecto.
- Puertos publicados nuevos: 8084 (`ms-auth`) y 5436 (`db-auth`).
- El comando para levantar solo las bases incluye `db-auth` (README y `AGENTS.md`).

## Non-goals

- No se modifica el requisito "Flujo completo contra los contenedores" ni la colección de Postman. La colección que trajo main no tiene pruebas automáticas, sus logins no guardan el token y el flujo necesita usuarios registrados antes; eso corresponde al cambio de JWT (`add-jwt-autorizacion`). Mientras tanto, ese requisito no se puede verificar.
- No se cambia el comportamiento de autenticación ni el filtro JWT del gateway.
- No se actualiza el test `ApiGatewayApplicationTests.debeTenerConfiguradasLasCuatroRutas`, que falla porque main agregó la ruta `auth`; queda para el cambio de JWT.
- No se agregan healthchecks HTTP a los servicios.

## Capabilities

### New Capabilities
- Ninguna.

### Modified Capabilities
- `despliegue-contenedores`: se suma `ms-auth` a las imágenes por servicio, al levantamiento completo, al orden de arranque, a la comunicación por nombre de servicio, a la ejecución local y a los puertos publicados, y se agrega el requisito del secreto JWT compartido.

## Impact

- `docker-compose.yml`: servicios `db-auth` y `ms-auth`, variables nuevas en el gateway.
- Nuevos: `ms-auth/Dockerfile`, `ms-auth/.dockerignore`.
- `ms-auth/src/main/resources/application.yml` y `api-gateway/src/main/resources/application.yml`: placeholders de variables de entorno con los mismos valores por defecto.
- `README.md` y `AGENTS.md`: cantidad de contenedores, comando de solo bases y la variable `JWT_SECRET`.
- Sin cambios en código Java, contratos ni tests.
