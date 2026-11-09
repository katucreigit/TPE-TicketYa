# TicketYa

Plataforma de venta de entradas para eventos, construida como microservicios con Spring Boot.
Trabajo Práctico Especial de Arquitectura Web (TUDAI), **Entrega 1**.

## Integrantes

- Completar con nombre y apellido de los cinco integrantes del grupo.

## Alcance de la Entrega 1

- Gestión de lugares, sectores y eventos, con publicación (US-01 a US-08).
- Búsqueda multicriterio y detalle de eventos con disponibilidad (US-09, US-10).
- Compra con reserva temporal, pago con pasarela simulada y entradas con QR (US-11 a US-16).
- Garantía de que un mismo lugar no se vende dos veces, con prueba de concurrencia (US-17).
- Devoluciones y cancelación de eventos con reintegros (US-18 a US-20).

La autenticación con JWT, la fila virtual, las notificaciones y los reportes corresponden a la Entrega 2 y no están implementados.
En esta entrega el usuario se identifica con los headers `X-User-Id` y `X-User-Rol`.

## Arquitectura

| Componente | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | - | Punto de entrada único; enruta y quita el prefijo `/api` |
| `ms-eventos` | 8081 | `db_eventos` (5433) | Lugares, sectores, eventos, estados y búsqueda |
| `ms-reservas` | 8082 | `db_reservas` (5434) | Inventario, reservas temporales y control de doble venta |
| `ms-ventas` | 8083 | `db_ventas` (5435) | Compras, pagos, entradas con QR, devoluciones y reintegros |

Cada microservicio tiene su propia base PostgreSQL y no comparte tablas con los demás.
Los endpoints `/internal/**` se usan solo entre servicios y el gateway no los expone.
El detalle de diseño está en [`docs/arquitectura.md`](docs/arquitectura.md).

## Requisitos

- Java 21
- Maven 3.9 o superior
- Docker y Docker Compose

## Cómo ejecutar

1. Levantar las bases de datos (una por servicio):

   ```
   docker compose up -d
   ```

   Esto crea tres bases PostgreSQL 16 en los puertos 5433, 5434 y 5435 (usuario y contraseña `ticketya`).

2. Iniciar cada servicio en una terminal distinta, desde la raíz del repositorio:

   ```
   cd ms-eventos   && mvn spring-boot:run
   cd ms-reservas  && mvn spring-boot:run
   cd ms-ventas    && mvn spring-boot:run
   cd api-gateway  && mvn spring-boot:run
   ```

   Conviene iniciar el gateway al final. También se puede ejecutar cada proyecto desde el IDE.

3. Usar la API a través del gateway: `http://localhost:8080/api`.

Las tablas se crean automáticamente al iniciar cada servicio.

### Configuración

Los valores por defecto sirven para ejecución local. Se pueden cambiar con variables de entorno:

| Variable | Servicio | Valor por defecto |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `ms-eventos`, `ms-reservas`, `ms-ventas` | `localhost` en el puerto de su base, usuario `ticketya` |
| `MS_EVENTOS_URL` | `api-gateway`, `ms-reservas`, `ms-ventas` | `http://localhost:8081` |
| `MS_RESERVAS_URL` | `api-gateway`, `ms-eventos`, `ms-ventas` | `http://localhost:8082` |
| `MS_VENTAS_URL` | `api-gateway`, `ms-eventos` | `http://localhost:8083` |

### Documentación interactiva

Cada servicio publica su documentación Swagger una vez iniciado:

- ms-eventos: <http://localhost:8081/swagger-ui.html>
- ms-reservas: <http://localhost:8082/swagger-ui.html>
- ms-ventas: <http://localhost:8083/swagger-ui.html>

El contrato completo de la API pública está en [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.0). Se puede abrir en Swagger Editor o importar en Postman.

## Identificación de usuarios

Todas las operaciones llevan dos headers:

| Header | Contenido |
|---|---|
| `X-User-Id` | Identificador del usuario |
| `X-User-Rol` | `CLIENTE`, `ORGANIZADOR`, `ADMIN` o `ACCESO` |

Si faltan o el rol no es válido, la respuesta es `400`; si el rol no corresponde a la operación, `403`.
El pago (`POST /compras`) exige además el header `Idempotency-Key`.

## Pasarela de pago simulada

La pasarela es un mock que decide según el token del medio de pago:

| Token | Resultado |
|---|---|
| `tok_ok` | Pago aprobado |
| `tok_rechazado` | Pago rechazado (`402`) |
| `tok_timeout` | La pasarela no responde (`504`) |

## Pruebas

Cada servicio se prueba por separado:

```
cd ms-reservas && mvn test
```

La prueba de concurrencia de `ms-reservas` lanza solicitudes simultáneas por el mismo lugar y verifica que exactamente una resulte exitosa.

## Documentación del proyecto

| Documento | Contenido |
|---|---|
| [`docs/user-stories.md`](docs/user-stories.md) | Historias de usuario, criterios de aceptación y reglas generales |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Microservicios, gateway, anti doble venta, saga, estados y decisiones |
| [`docs/modelo-datos.md`](docs/modelo-datos.md) | Modelo de datos por servicio y datos compartidos |
| [`docs/secuencia-compra.md`](docs/secuencia-compra.md) | Diagramas de secuencia de la compra y el vencimiento |
| [`docs/openapi.yaml`](docs/openapi.yaml) | Contrato de la API en OpenAPI 3.0 |

## Metodología: desarrollo guiado por especificaciones

El comportamiento del sistema se especifica antes de implementarlo, con [OpenSpec](https://github.com/Fission-AI/OpenSpec).
Los requisitos se escriben en notación EARS y cada uno tiene escenarios `WHEN` / `THEN` verificables.

```
openspec/
  config.yaml          contexto del proyecto y reglas de redacción
  specs/               especificaciones vigentes (se completan al archivar cada cambio)
  changes/             un cambio por bloque de trabajo
    add-catalogo-eventos/
    add-busqueda-eventos/
    add-reservas-anti-doble-venta/
    add-compra-con-pago/
    add-devoluciones/
    add-api-gateway/
```

Cada cambio contiene:

- `proposal.md`: motivo, alcance y lo que queda fuera.
- `specs/`: requisitos y escenarios.
- `design.md`: decisiones técnicas.
- `tasks.md`: tareas de implementación y de pruebas.

Flujo de trabajo: leer el cambio, implementar siguiendo `tasks.md`, escribir una prueba por escenario, marcar las tareas terminadas y validar.
Las convenciones de código y los principios del proyecto están en [`AGENTS.md`](AGENTS.md).

Para validar el formato de las especificaciones (opcional, requiere Node.js):

```
npm install -g @fission-ai/openspec
openspec validate --all --strict
```

## Estructura del repositorio

```
api-gateway/       API Gateway (Spring Cloud Gateway)
ms-eventos/        Microservicio de eventos
ms-reservas/       Microservicio de reservas
ms-ventas/         Microservicio de ventas
docs/              Historias de usuario, arquitectura, modelo de datos y OpenAPI
openspec/          Especificaciones y cambios
docker-compose.yml Bases de datos PostgreSQL
AGENTS.md          Convenciones del proyecto
```
