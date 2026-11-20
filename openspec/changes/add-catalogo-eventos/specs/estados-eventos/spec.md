# Spec Delta

## Purpose

Define las operaciones que permiten publicar, suspender, reanudar y cancelar eventos, así como actualizar su disponibilidad y finalizarlos automáticamente según su fecha.

## ADDED Requirements

### Requirement: Publicar eventos
El sistema SHALL permitir publicar eventos en estado BORRADOR cuando tengan precios configurados para todos los sectores del lugar.

#### Scenario: Publicar un evento con todos los precios configurados
- **WHEN** un organizador solicita publicar un evento en BORRADOR y todos los sectores del lugar tienen precio configurado
- **THEN** el sistema inicializa el inventario en ms-reservas y cambia el evento a PUBLICADO

#### Scenario: Publicar un evento con sectores sin precio
- **WHEN** un organizador solicita publicar un evento y existe al menos un sector del lugar sin precio configurado
- **THEN** el sistema rechaza la publicación e informa que faltan precios

#### Scenario: Publicar un evento con inventario ya inicializado
- **WHEN** ms-reservas informa que el inventario del evento ya existe
- **THEN** el sistema considera la inicialización como realizada y completa la publicación

#### Scenario: Publicar un evento cuando reservas no está disponible
- **WHEN** el sistema intenta inicializar el inventario y ms-reservas no responde correctamente
- **THEN** el sistema no completa la publicación y devuelve un error 502

### Requirement: Suspender eventos
El sistema SHALL permitir suspender eventos publicados o agotados y cerrar temporalmente su inventario.

#### Scenario: Suspender un evento publicado
- **WHEN** se solicita suspender un evento en estado PUBLICADO
- **THEN** el sistema cierra el inventario en ms-reservas y cambia el estado del evento a SUSPENDIDO

#### Scenario: Suspender un evento agotado
- **WHEN** se solicita suspender un evento en estado AGOTADO
- **THEN** el sistema cierra el inventario en ms-reservas y cambia el estado del evento a SUSPENDIDO

### Requirement: Reanudar eventos suspendidos
El sistema SHALL permitir reanudar eventos en estado SUSPENDIDO y volver a habilitar su inventario.

#### Scenario: Reanudar un evento suspendido
- **WHEN** se solicita reanudar un evento en estado SUSPENDIDO
- **THEN** el sistema vuelve a habilitar el inventario en ms-reservas y cambia el estado a PUBLICADO

### Requirement: Cancelar eventos
El sistema SHALL permitir que un usuario con rol ADMIN cancele un evento indicando obligatoriamente un motivo y notifique la cancelación a ms-ventas.

#### Scenario: Cancelar un evento con motivo
- **WHEN** un usuario ADMIN solicita cancelar un evento e informa el motivo
- **THEN** el sistema cambia el estado a CANCELADO, cierra el inventario y notifica la cancelación a ms-ventas

#### Scenario: Cancelar un evento sin motivo
- **WHEN** un usuario ADMIN solicita cancelar un evento sin informar el motivo
- **THEN** el sistema rechaza la solicitud por datos inválidos

#### Scenario: Cancelar un evento sin permisos
- **WHEN** un usuario que no tiene rol ADMIN intenta cancelar un evento
- **THEN** el sistema rechaza la operación con un error 403

### Requirement: Actualizar la disponibilidad del evento
El sistema SHALL permitir que ms-reservas informe internamente cuando un evento queda agotado o vuelve a tener disponibilidad.

#### Scenario: Informar que un evento está agotado
- **WHEN** ms-reservas informa que un evento publicado no tiene disponibilidad
- **THEN** el sistema cambia el estado del evento a AGOTADO

#### Scenario: Informar que un evento vuelve a tener disponibilidad
- **WHEN** ms-reservas informa que un evento agotado vuelve a tener disponibilidad
- **THEN** el sistema cambia el estado del evento a PUBLICADO

### Requirement: Finalizar automáticamente eventos pasados
El sistema SHALL cambiar automáticamente a FINALIZADO todo evento en estado PUBLICADO o AGOTADO cuya fecha y hora ya hayan pasado.

#### Scenario: Finalizar un evento publicado cuya fecha pasó
- **WHEN** el proceso periódico detecta un evento PUBLICADO cuya fecha y hora ya pasaron
- **THEN** el sistema cambia su estado a FINALIZADO

#### Scenario: Finalizar un evento agotado cuya fecha pasó
- **WHEN** el proceso periódico detecta un evento AGOTADO cuya fecha y hora ya pasaron
- **THEN** el sistema cambia su estado a FINALIZADO

#### Scenario: No finalizar un evento cuya fecha todavía no pasó
- **WHEN** el proceso periódico detecta un evento PUBLICADO o AGOTADO cuya fecha y hora son futuras
- **THEN** el sistema mantiene el estado actual del evento
