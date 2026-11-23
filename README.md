# TicketYa

Plataforma de venta de entradas para eventos, construida como microservicios con Spring Boot.
Trabajo Práctico Especial de Arquitectura Web (TUDAI), **Entrega 2**.

## Integrantes

- Katrina Creimerman
- Facundo Napp
- Geronimo Moroni
- Fernando Barragán
- Benjamin Herrera

## Alcance de la Entrega 2 (sistema completo)

Incluye todo lo de la Entrega 1 más:

- Autenticación y autorización con JWT y roles (ms-auth).
- Fila virtual para eventos de alta demanda (US-21 y US-22, ms-fila con Redis).
- Control de acceso con QR (US-23, `POST /entradas/validar` en ms-ventas).
- Preferencias y recomendaciones de eventos (US-24 y US-25, ms-recomendaciones con MongoDB).
- Notificaciones por email simulado (US-26 y US-27, ms-notificaciones con MongoDB; ver [Estado y limitaciones](#estado-y-limitaciones-conocidas)).
- Reporte de ventas por evento y sector (US-29, endpoint interno de ms-ventas).
- Bases NoSQL: MongoDB para notificaciones y recomendaciones, y Redis para la fila virtual.
- Caché del catálogo de eventos, resiliencia (circuit breaker en el gateway, fail-open de la fila, reconciliación de pagos inciertos), trazabilidad con `X-Correlation-Id` y envío asíncrono de notificaciones con reintentos.
- Contenedores Docker para todos los servicios con `docker-compose.yml`.

## Arquitectura

| Componente | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | - | Punto de entrada único; valida JWT y enruta |
| `ms-auth` | 8084 | `db_auth` (5436) | Registro, login y emisión de tokens JWT |
| `ms-eventos` | 8081 | `db_eventos` (5433) | Lugares, sectores, eventos, estados y búsqueda |
| `ms-reservas` | 8082 | `db_reservas` (5434) | Inventario, reservas temporales y control de doble venta |
| `ms-ventas` | 8083 | `db_ventas` (5435) | Compras, pagos, entradas con QR, devoluciones y reintegros |
| `ms-notificaciones` | 8085 | `db_notificaciones` (MongoDB, 27017) | Notificaciones a clientes (email simulado) con reintentos y deduplicación |
| `ms-fila` | 8086 | Redis (6379) | Fila virtual para eventos de alta demanda: posición y habilitación por tandas |
| `ms-recomendaciones` | 8087 | `db_recomendaciones` (MongoDB, 27017) | Preferencias de clientes y recomendaciones de eventos por puntaje y "también compraron" |
| `frontend` | 4200 | - | Interfaz web en Angular 19 que consume la API del gateway |

Cada microservicio tiene su propia base (PostgreSQL, MongoDB en el caso de `ms-notificaciones` y `ms-recomendaciones`, o Redis en el de `ms-fila`) y no comparte datos con los demás.
Los endpoints `/internal/**` se usan solo entre servicios y el gateway no los expone.
El detalle de diseño está en [`docs/arquitectura.md`](docs/arquitectura.md).

## Frontend

Interfaz web en Angular 19 con Bootstrap 5. Consume la API del gateway en `localhost:8080`.

Páginas:

| Ruta | Descripción |
|---|---|
| `/login` | Inicio de sesión |
| `/register` | Registro con selección de rol (CLIENTE / ORGANIZADOR) |
| `/events` | Listado de eventos con filtros por nombre y ciudad |
| `/events/:id` | Detalle del evento, sectores y precios |
| `/events/:id/checkout` | Reserva y pago (requiere sesión como CLIENTE) |
| `/my-tickets` | Entradas compradas, QR y solicitud de devolución |

El JWT se adjunta automáticamente en cada request con un interceptor. Las rutas de checkout y mis entradas tienen un guard que redirige al login si no hay sesión activa.

### Levantar el frontend

Requiere Node.js. El Angular CLI viene como dependencia del proyecto, así que no hace falta instalarlo aparte:

```
cd frontend
npm install
npm start
```

`npm start` ejecuta `ng serve`. Si el Angular CLI está instalado globalmente, también funciona `ng serve`.

Disponible en `http://localhost:4200`. El frontend llama directamente a `http://localhost:8080/api`, así que el gateway tiene que estar levantado, con Compose o desde el IDE. El gateway acepta CORS solo desde `http://localhost:4200`.

## Requisitos

- Java 21
- Maven 3.9 o superior
- Docker y Docker Compose

## Cómo ejecutar

Hay dos formas: todo el sistema en contenedores, o solo las bases en contenedores y los servicios desde el IDE.

### Todo el sistema con Docker Compose

Desde la raíz del repositorio:

```
docker compose up -d --build
```

Levanta 14 contenedores. Cada servicio se construye con su propio `Dockerfile` (build con Maven y ejecución con JRE 21), así que no hace falta tener Maven ni Java instalados.

| Contenedor | Imagen | Puerto en el anfitrión | Espera a |
|---|---|---|---|
| `api-gateway` | `ticketya/api-gateway` | 8080 | `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-fila` y `ms-recomendaciones` (iniciados) |
| `ms-eventos` | `ticketya/ms-eventos` | 8081 | `db-eventos` |
| `ms-reservas` | `ticketya/ms-reservas` | 8082 | `db-reservas` |
| `ms-ventas` | `ticketya/ms-ventas` | 8083 | `db-ventas` |
| `ms-auth` | `ticketya/ms-auth` | 8084 | `db-auth` |
| `ms-notificaciones` | `ticketya/ms-notificaciones` | 8085 | `mongo` |
| `ms-fila` | `ticketya/ms-fila` | 8086 | `redis` |
| `ms-recomendaciones` | `ticketya/ms-recomendaciones` | 8087 | `mongo` |
| `db-eventos` | `postgres:16-alpine` | 5433 | - |
| `db-reservas` | `postgres:16-alpine` | 5434 | - |
| `db-ventas` | `postgres:16-alpine` | 5435 | - |
| `db-auth` | `postgres:16-alpine` | 5436 | - |
| `mongo` | `mongo:7` | 27017 | - |
| `redis` | `redis:7-alpine` | 6379 | - |

- Antes de levantarlo, detener los servicios que estén corriendo desde el IDE para que no choquen los puertos.
- Cada microservicio espera a que su base esté saludable (healthcheck), y el gateway arranca al final. `ms-reservas` no espera a `ms-fila`, ni `ms-ventas` a `ms-recomendaciones`: si no responden, las llamadas a esos servicios no frenan la reserva ni la compra.
- Los servicios tardan en iniciar: `ms-auth` alrededor de 90 segundos, y más en una máquina cargada. Conviene esperar a que `docker compose logs -f` muestre `Started` en los ocho servicios Spring antes de usar la API. Las primeras peticiones después del arranque también son más lentas, porque la JVM todavía no está optimizada y las cachés y conexiones están vacías.
- La primera vez, `--build` compila las ocho imágenes con Maven en paralelo y necesita bastante disco y memoria. En una máquina justa se pueden construir de a una con `COMPOSE_PARALLEL_LIMIT=1` (en PowerShell: `$env:COMPOSE_PARALLEL_LIMIT=1; docker compose build`).
- Dentro de compose los servicios se comunican por nombre de servicio (`http://ms-eventos:8081`, `db-eventos:5432`, etc.), configurado con las variables de la tabla de [Configuración](#configuración).
- `docker compose down` detiene todo y conserva los datos; `docker compose down -v` borra también las bases, para empezar de cero.

Cada imagen también se puede construir por separado, por ejemplo:

```
docker build -t ticketya/ms-eventos ms-eventos
```

### Servicios desde el IDE

1. Levantar solo las bases de datos (una por servicio):

   ```
   docker compose up -d db-eventos db-reservas db-ventas db-auth mongo redis
   ```

   Esto crea cuatro bases PostgreSQL 16 en los puertos 5433, 5434, 5435 y 5436, un MongoDB 7 en el 27017 (usuario y contraseña `ticketya` en todas) y un Redis 7 sin contraseña en el 6379.

2. Iniciar cada servicio en una terminal distinta, desde la raíz del repositorio:

   ```
   cd ms-eventos   && mvn spring-boot:run
   cd ms-reservas  && mvn spring-boot:run
   cd ms-ventas    && mvn spring-boot:run
   cd ms-auth      && mvn spring-boot:run
   cd ms-notificaciones && mvn spring-boot:run
   cd ms-fila      && mvn spring-boot:run
   cd ms-recomendaciones && mvn spring-boot:run
   cd api-gateway  && mvn spring-boot:run
   ```

   Conviene iniciar el gateway al final. También se puede ejecutar cada proyecto desde el IDE.

3. Usar la API a través del gateway: `http://localhost:8080/api`.

Las tablas (y la colección e índices de MongoDB) se crean automáticamente al iniciar cada servicio.

### Configuración

Los valores por defecto sirven para ejecución local desde el IDE. Se pueden cambiar con variables de entorno; `docker-compose.yml` las define para que apunten a los nombres de servicio:

| Variable | Servicio | Valor por defecto |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | los ms con PostgreSQL | `localhost` en el puerto de su base, usuario `ticketya` |
| `MONGO_URI` | `ms-notificaciones`, `ms-recomendaciones` | `mongodb://ticketya:ticketya@localhost:27017/<base>?authSource=admin` (`db_notificaciones` o `db_recomendaciones`) |
| `MS_AUTH_URL` | `api-gateway` | `http://localhost:8084` |
| `JWT_SECRET` | `api-gateway`, `ms-auth` | Clave de desarrollo (Base64) de sus `application.yml`; tiene que ser la misma en los dos. Para otro entorno, definirla en el shell o en un `.env` sin versionar |
| `MS_EVENTOS_URL` | `api-gateway`, `ms-reservas`, `ms-ventas`, `ms-recomendaciones` | `http://localhost:8081` |
| `MS_RESERVAS_URL` | `api-gateway`, `ms-eventos`, `ms-ventas` | `http://localhost:8082` |
| `MS_VENTAS_URL` | `api-gateway`, `ms-eventos` | `http://localhost:8083` |
| `MS_FILA_URL` | `api-gateway`, `ms-reservas` | `http://localhost:8086` |
| `MS_RECOMENDACIONES_URL` | `api-gateway`, `ms-ventas` | `http://localhost:8087` |
| `REDIS_HOST`, `REDIS_PORT` | `ms-fila` | `localhost`, `6379` |

### Documentación interactiva

Cada servicio publica su documentación Swagger una vez iniciado:

- ms-auth: <http://localhost:8084/swagger-ui.html>
- ms-eventos: <http://localhost:8081/swagger-ui.html>
- ms-reservas: <http://localhost:8082/swagger-ui.html>
- ms-ventas: <http://localhost:8083/swagger-ui.html>
- ms-notificaciones: <http://localhost:8085/swagger-ui.html>
- ms-fila: <http://localhost:8086/swagger-ui.html>
- ms-recomendaciones: <http://localhost:8087/swagger-ui.html>

El contrato completo de la API pública está en [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.0).

## Autenticación con JWT

Todos los endpoints protegidos requieren un token JWT en el header `Authorization`.

### 1. Registrar un usuario

```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "email": "cliente@example.com",
  "password": "miPassword123",
  "rol": "CLIENTE"
}
```

Roles disponibles: `CLIENTE`, `ORGANIZADOR`, `ADMIN`, `PERSONAL_ACCESO`.

Respuesta:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "rol": "CLIENTE"
}
```

### 2. Iniciar sesión

```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "cliente@example.com",
  "password": "miPassword123"
}
```

Respuesta igual a la del registro.

### 3. Usar el token

Incluir el token en el header `Authorization` de cada request:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

El gateway valida el token y propaga automáticamente los headers `X-User-Id` y `X-User-Rol` a los microservicios. No es necesario enviarlos manualmente.

### Rutas públicas (no requieren token)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/register` | Registro de usuario |
| `POST` | `/api/auth/login` | Login |
| `GET` | `/api/eventos/**` | Buscar y ver eventos |
| `GET` | `/api/lugares/**` | Ver lugares |

Cualquier otra ruta sin token devuelve `401 Unauthorized`. El token vence a las 24 horas (`jwt.expiration-ms` en `ms-auth`).

### Roles

| Rol | Puede |
|---|---|
| `CLIENTE` | Reservar y comprar, ver sus compras y entradas, pedir devoluciones, entrar a la fila virtual, guardar preferencias y ver recomendaciones |
| `ORGANIZADOR` | Crear lugares, crear y editar sus eventos, configurar precios y publicarlos, y activar o desactivar la fila virtual |
| `ADMIN` | Crear lugares, suspender, reanudar y cancelar eventos, aprobar o rechazar devoluciones, ver el detalle de cualquier compra o entrada, y activar o desactivar la fila virtual |
| `PERSONAL_ACCESO` | Validar entradas por QR en el ingreso (`POST /api/entradas/validar`) |

"También compraron" (`GET /api/recomendaciones/eventos/{id}/tambien-compraron`) acepta cualquiera de los cuatro roles. Con un rol que no corresponde, el servicio responde `403 ACCESO_DENEGADO`.

### Headers

| Header | Quién lo pone | Para qué |
|---|---|---|
| `Authorization: Bearer <token>` | el cliente | Identidad. El gateway valida el JWT y lo convierte en `X-User-Id` y `X-User-Rol`, reemplazando lo que haya mandado el cliente |
| `X-Correlation-Id` | el cliente (opcional) o el gateway | Identifica una operación de punta a punta. Si no viene, el gateway genera un UUID. Se devuelve en la respuesta y se propaga en las llamadas entre servicios. Cada servicio que lo maneja lo incluye en sus registros (`HTTP_REQUEST`, `OPERACION_CRITICA` y advertencias como `FILA_NO_DISPONIBLE`), así que con un mismo valor se sigue una compra por todos los logs |
| `Idempotency-Key` | el cliente | Obligatorio en `POST /api/compras` (si falta, `400 IDEMPOTENCY_KEY_REQUERIDA`). Un reintento con la misma clave devuelve la misma compra sin cobrar de nuevo, incluso después de un timeout de la pasarela |

Los servicios que manejan el `X-Correlation-Id` son el gateway, `ms-reservas`, `ms-ventas`, `ms-notificaciones`, `ms-fila` y `ms-recomendaciones`. `ms-eventos` y `ms-auth` todavía no (ver [Estado y limitaciones](#estado-y-limitaciones-conocidas)).

## Pasarela de pago simulada

La pasarela es un mock que decide según el token del medio de pago:

| Token | Resultado |
|---|---|
| `tok_ok` | Pago aprobado |
| `tok_rechazado` | Pago rechazado (`402`) |
| `tok_timeout` | La pasarela cobra pero no responde a tiempo (`504`); al reintentar con la misma `Idempotency-Key` devuelve el cobro original, sin cobrar de nuevo |

## Notificaciones (email simulado)

`ms-notificaciones` recibe los pedidos en `POST http://localhost:8085/internal/notificaciones` (solo entre servicios), responde `202` y los envía en segundo plano escribiendo el email en el log. Si un envío falla, lo reintenta con espera creciente (2 s y 4 s) y, después de 3 intentos, la notificación queda `FALLIDA`. Si se repite un pedido con la misma `claveDeduplicacion`, devuelve la notificación existente en lugar de crear otra. Un administrador las consulta con `GET http://localhost:8085/notificaciones` (filtros `usuarioId`, `tipo` y `estado`) y `GET /notificaciones/{id}`, con `X-User-Rol: ADMIN`.

Para probar los reintentos, el envío simulado falla según el email del destinatario (se cambia en `ticketya.notificaciones.simulacion`):

| Email | Resultado |
|---|---|
| `falla@ticketya.test` | Falla siempre; queda `FALLIDA` con 3 intentos |
| `falla-una-vez@ticketya.test` | Falla el primer intento y se envía en el segundo |
| cualquier otro | Se envía en el primer intento |

Hoy solo `ms-ventas` pide notificaciones, en dos casos: compra confirmada y cancelación de un evento con entradas vendidas. En los dos, el pedido es best-effort y una falla nunca afecta la compra ni la cancelación. `ms-eventos` todavía no avisa los cambios de un evento ni la publicación de un artista favorito, y la consulta administrativa no tiene ruta en el gateway: se usa directo en el puerto 8085.

## Fila virtual

Para eventos de alta demanda, `ms-fila` ordena el acceso a la compra. Mientras la fila de un evento está activa, solo pueden reservar los clientes habilitados. Todas las rutas pasan por el gateway y llevan el token correspondiente:

1. Un `ADMIN` u `ORGANIZADOR` activa la fila del evento:

   ```
   POST http://localhost:8080/api/fila/eventos/42/activar
   Content-Type: application/json

   { "tamanoTanda": 50, "segundosEntreTandas": 30 }
   ```

   El cuerpo es opcional (por defecto 100 clientes cada 60 segundos). La primera tanda sale `segundosEntreTandas` después de activar.

2. Cada `CLIENTE` ingresa con `POST /api/fila/eventos/42/ingresar` y recibe su `posicion`. Si repite el ingreso, conserva su lugar.
3. Consulta su estado con `GET /api/fila/eventos/42/posicion`: `EN_ESPERA` (con `posicion`), `HABILITADO` (con `habilitadoHasta`), `FUERA_DE_FILA` o `SIN_FILA` si el evento no tiene fila.
4. Cada `segundosEntreTandas`, los primeros `tamanoTanda` clientes en espera quedan habilitados por 10 minutos. Un cliente habilitado reserva normalmente con `POST /api/reservas`; si no está habilitado, la reserva responde `403 FILA_NO_HABILITADO`. Si no reserva dentro de la ventana, pierde el turno y tiene que volver a ingresar.
5. `POST /api/fila/eventos/42/desactivar` termina la fila y cualquier cliente vuelve a poder reservar.

Si `ms-fila` no responde en 1 segundo, `ms-reservas` deja pasar la reserva y registra una advertencia `FILA_NO_DISPONIBLE` (fail-open): la fila nunca frena las ventas, y la garantía anti doble venta sigue en la base de `ms-reservas`. El diseño está en `docs/arquitectura.md` y en `openspec/changes/archive/2026-11-23-add-fila-virtual/design.md`.

## Preferencias y recomendaciones

`ms-recomendaciones` guarda las preferencias de cada cliente y le sugiere eventos publicados. Todas las rutas pasan por el gateway con JWT:

- `PUT /api/preferencias` guarda los tipos de evento, los artistas favoritos y la ciudad, reemplazando las anteriores. `GET /api/preferencias` las devuelve; si nunca las guardaste, vienen vacías.

  ```
  PUT http://localhost:8080/api/preferencias
  Content-Type: application/json

  { "tipos": ["RECITAL"], "artistas": ["Los Andariegos"], "ciudad": "Tandil" }
  ```

- `GET /api/recomendaciones?limite=10` (rol `CLIENTE`) lista los eventos publicados que todavía no compraste, ordenados por puntaje. Cada uno trae sus `motivos`:
  - `TIPO` +3, `ARTISTA` +3, `CIUDAD` +2: coincidencia con tus preferencias.
  - `TAMBIEN_COMPRARON`, hasta +2: lo compraron otros clientes con compras parecidas a las tuyas.
  - `POPULAR`, hasta +1: cantidad de compradores.

  Sin preferencias ni compras, devuelve los eventos más populares.
- `GET /api/recomendaciones/eventos/{id}/tambien-compraron` devuelve qué otros eventos compraron los clientes que compraron ese evento.

Cada compra confirmada en `ms-ventas` se avisa a `ms-recomendaciones` (`POST /internal/compras`) en modo best-effort. Si el aviso falla, la compra sigue igual y queda la advertencia `RECOMENDACIONES_NO_DISPONIBLE`. Solo cuentan las compras confirmadas desde que el servicio está desplegado.

Si `ms-eventos` no responde, las recomendaciones usan la última copia del catálogo o una lista vacía, y responden `200` con `"degradado": true`. El diseño está en `docs/arquitectura.md` y en `openspec/changes/archive/2026-11-23-add-preferencias-recomendaciones/design.md`.

## Pruebas

Cada servicio se prueba por separado:

```
cd ms-auth     && mvn test
cd ms-eventos  && mvn test
cd ms-reservas && mvn test
cd ms-ventas   && mvn test
cd ms-notificaciones && mvn test
cd ms-fila     && mvn test
cd ms-recomendaciones && mvn test
cd api-gateway && mvn test
```

La prueba de concurrencia de `ms-reservas` lanza solicitudes simultáneas por el mismo lugar y verifica que exactamente una resulte exitosa. Las de `ms-fila` lanzan 200 ingresos simultáneos (posiciones exactamente del 1 al 200) y varios procesos de habilitación a la vez (nadie queda habilitado dos veces). Las pruebas de `ms-reservas`, `ms-ventas`, `ms-notificaciones`, `ms-fila` y `ms-recomendaciones` usan Testcontainers (PostgreSQL 16, MongoDB 7 y Redis 7), así que necesitan Docker en ejecución.

## Documentación del proyecto

| Documento | Contenido |
|---|---|
| [`docs/user-stories.md`](docs/user-stories.md) | Historias de usuario, criterios de aceptación y reglas generales |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Microservicios, gateway, anti doble venta, saga, estados y decisiones |
| [`docs/modelo-datos.md`](docs/modelo-datos.md) | Modelo de datos por servicio y datos compartidos |
| [`docs/secuencia-compra.md`](docs/secuencia-compra.md) | Diagramas de secuencia de la compra y el vencimiento |
| [`docs/diagramas.md`](docs/diagramas.md) | Diagramas en Mermaid: arquitectura, modelo de datos, estados y secuencias |
| [`docs/openapi.yaml`](docs/openapi.yaml) | Contrato de la API en OpenAPI 3.0 |

## Datos de prueba

- `ms-eventos` carga al iniciar un conjunto inicial: organizadores (ids 1 y 2), lugares con sus sectores y algunos eventos.
- Para pagar se usan los tokens de la pasarela simulada: `tok_ok`, `tok_rechazado` y `tok_timeout`.
- Para probar sin registrarse a mano, la carpeta de preparación de la colección Postman crea un usuario por rol (contraseña `pass123`) y hace login de cada uno.

## Colección de requests (Postman)

En [`postman/TicketYa.postman_collection.json`](postman/TicketYa.postman_collection.json) hay una colección agrupada por recurso. La autenticación es siempre con JWT: cada request protegido manda `Authorization: Bearer {{token...}}` con el token del rol que corresponde, y las rutas públicas (como `GET /eventos`) van sin token.

Las carpetas se ejecutan en este orden con el Runner:

1. **0. Preparación (auth)**: registra un ORGANIZADOR, un CLIENTE, un segundo CLIENTE, un ADMIN y un usuario de acceso (si ya existen, el `409` se acepta) y hace login de cada uno, guardando `tokenOrganizador`, `tokenCliente`, `tokenCliente2`, `tokenAdmin` y `tokenAcceso` en variables de la colección. 10 pruebas. El ORGANIZADOR se registra primero a propósito: `ms-eventos` exige que el id del token exista en su tabla de organizadores, que tiene los ids 1 y 2 de los datos de prueba, y con `db-auth` vacía el primer usuario recibe el id 1. Si `db-auth` ya tenía otros usuarios, el login del organizador lo avisa; en ese caso, `docker compose down -v` y volver a levantar.
2. **Flujo completo (en orden)**: crear lugar y evento, configurar precios, publicar, ver disponibilidad, reservar, pagar, ver entradas, solicitar y aprobar una devolución. 15 pruebas.
3. **Casos de error**: asiento ya reservado por otro cliente (`409`), pago rechazado (`402`), pago sin `Idempotency-Key` (`400`), token inválido y sin token (`401`) y un CLIENTE intentando una operación de ORGANIZADOR (`403`). 7 pruebas.

El resto de las carpetas son requests sueltos por recurso, y la de internos va directo a cada servicio sin pasar por el gateway. La colección [`postman/TicketYa-Auth.postman_collection.json`](postman/TicketYa-Auth.postman_collection.json) tiene requests sueltos de registro y login.

Para usarla: levantar el sistema (con `docker compose up -d --build` o desde el IDE) y ejecutar las tres carpetas en orden. `ms-auth` tarda alrededor de 90 segundos en arrancar (más en una máquina cargada); hasta que `docker compose logs ms-auth` muestre `Started MsAuthApplication`, la preparación falla. Funciona igual contra los contenedores, porque el gateway se publica en el mismo puerto 8080. Para repetir el flujo desde cero con compose: `docker compose down -v` y volver a levantar.

Desde la línea de comandos, con [newman](https://www.npmjs.com/package/newman):

```
npx newman run postman/TicketYa.postman_collection.json --folder "0. Preparación (auth)" --folder "Flujo completo (en orden)" --folder "Casos de error"
```

newman corre las carpetas en el orden en que están en la colección, que ya es el correcto, y `npx` lo descarga si no está instalado. La colección no tiene requests de la fila virtual, las recomendaciones, la validación por QR ni el reporte de ventas; esos flujos se prueban con las pruebas de cada servicio y con el Swagger.

## Uso de inteligencia artificial

Usamos Claude (Anthropic) como asistente durante el trabajo:

- Claude Code, en la terminal, para implementar los microservicios y sus pruebas siguiendo las especificaciones de OpenSpec.
- El chat de Claude para planificar, revisar el código, diagramas y redactar la documentación.

Las especificaciones se escribieron antes del código y guiaron lo que generó la IA. El equipo revisó el código, corrió las pruebas y verificó el flujo completo contra bases PostgreSQL reales.

## Estado y limitaciones conocidas

- **Organizador:** `ms-eventos` exige que el organizador exista en su tabla `organizador`, que solo tiene los ids 1 y 2 de los datos de prueba. Un organizador recién registrado en `ms-auth` con otro id recibe `404 ORGANIZADOR_NO_ENCONTRADO` al crear un evento. No hay sincronización entre `ms-auth` y `ms-eventos`.
- **Cancelación masiva:** la cancelación de un evento no trata las entradas con devolución solicitada ni las compras con pago en curso al momento de cancelar.
- **Recomendaciones:** solo cuentan las compras confirmadas desde que `ms-recomendaciones` está desplegado; el historial anterior de `ms-ventas` no se importa. Las devoluciones y las entradas reintegradas no restan compras: un cliente que devolvió su entrada sigue contando como comprador.
- **Aviso de compras:** si `ms-recomendaciones` está caído al confirmar una compra, esa compra no se registra nunca. Queda la advertencia `RECOMENDACIONES_NO_DISPONIBLE` con el `compraId`.
- **Redis:** corre en un solo nodo, con AOF sobre un volumen, sin réplicas. Si se cae, la fila virtual deja pasar las reservas (fail-open) hasta que vuelva.
- **Notificaciones:** solo `ms-ventas` las pide (compra confirmada y cancelación). Faltan el aviso de evento modificado (parte de US-27) y el de artista favorito en la ciudad (US-28). La consulta administrativa no tiene ruta en el gateway.
- **Reporte de ventas:** existe solo como endpoint interno de `ms-ventas` (`GET /internal/eventos/{id}/reporte-ventas`, puerto 8083, sin JWT ni control de rol). Todavía no hay una ruta pública para que el organizador lo consulte por el gateway.
- **Trazabilidad:** `ms-eventos` y `ms-auth` no leen ni propagan el `X-Correlation-Id`. Los registros salen por la salida estándar de cada contenedor y no están centralizados en una herramienta de búsqueda.
- **ms-auth:** `GET /api/auth/usuarios` es público, porque el gateway deja pasar todo `/api/auth/**` sin token, y devuelve el id, el email y el rol de todos los usuarios. Además, los errores de `ms-auth` (`409` email ya registrado, `401` credenciales inválidas) usan el formato por defecto de Spring y no el `Error` estándar del resto de los servicios.
- **Postman:** la colección cubre el flujo de la Entrega 1 con JWT, pero no las funcionalidades nuevas de la Entrega 2 (ver [Colección de requests](#colección-de-requests-postman)).

## Metodología: desarrollo guiado por especificaciones

El comportamiento del sistema se especifica antes de implementarlo, con [OpenSpec](https://github.com/Fission-AI/OpenSpec).
Los requisitos se escriben en notación EARS y cada uno tiene escenarios `WHEN` / `THEN` verificables.

```
openspec/
  config.yaml          contexto del proyecto y reglas de redacción
  specs/               especificaciones vigentes
  changes/             un cambio por bloque de trabajo
```

Para validar el formato de las especificaciones (opcional, requiere Node.js):

```
npm install -g @fission-ai/openspec
openspec validate --all --strict
```

## Estructura del repositorio

```
api-gateway/       API Gateway (Spring Cloud Gateway, validación JWT)
ms-auth/           Microservicio de autenticación (registro, login, JWT)
ms-eventos/        Microservicio de eventos
ms-reservas/       Microservicio de reservas
ms-ventas/         Microservicio de ventas
ms-notificaciones/ Microservicio de notificaciones (MongoDB)
ms-fila/           Microservicio de fila virtual (Redis)
ms-recomendaciones/ Microservicio de preferencias y recomendaciones (MongoDB)
frontend/          Interfaz web Angular 19
docs/              Historias de usuario, arquitectura, modelo de datos, diagramas y OpenAPI
openspec/          Especificaciones y cambios
postman/           Colección de requests
docker-compose.yml Sistema completo: bases PostgreSQL, MongoDB y Redis, microservicios y gateway
AGENTS.md          Convenciones del proyecto
```
