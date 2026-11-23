# Spec Delta

## MODIFIED Requirements

### Requirement: Comunicación por nombre de servicio
Dentro del entorno de Compose, los servicios SHALL comunicarse entre sí y con sus bases usando el nombre de servicio de Compose, configurado mediante `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MONGO_URI`, `MS_EVENTOS_URL`, `MS_RESERVAS_URL`, `MS_VENTAS_URL`, `MS_AUTH_URL` y `MS_NOTIFICACIONES_URL`.

#### Scenario: Gateway enruta a los microservicios en contenedores
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/eventos` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-eventos` por su nombre de servicio y devuelve su respuesta

#### Scenario: Gateway enruta la autenticación a ms-auth
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/auth/login` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-auth` por su nombre de servicio y devuelve su respuesta

#### Scenario: Llamada interna entre microservicios
- **WHEN** un microservicio en Compose invoca un endpoint interno de otro microservicio
- **THEN** la llamada se dirige al nombre de servicio del destino y no a `localhost`

#### Scenario: ms-ventas se comunica con ms-notificaciones en Compose
- **WHEN** `ms-ventas` arranca en Compose y emite una notificación hacia `ms-notificaciones`
- **THEN** la solicitud se dirige al servicio `ms-notificaciones` en el puerto `8085` mediante `MS_NOTIFICACIONES_URL` y no a `localhost`

#### Scenario: Microservicio se conecta a su propia base
- **WHEN** un microservicio arranca en Compose
- **THEN** se conecta a la base de su servicio de Compose por el puerto interno 5432

#### Scenario: ms-notificaciones se conecta a MongoDB
- **WHEN** `ms-notificaciones` arranca en Compose
- **THEN** se conecta por `MONGO_URI` a la base `db_notificaciones` del servicio `mongo` en el puerto interno 27017

