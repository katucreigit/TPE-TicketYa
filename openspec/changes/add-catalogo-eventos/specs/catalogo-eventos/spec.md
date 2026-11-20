# Spec Delta

## Purpose

Define la creación, consulta y edición de eventos por parte de los organizadores, incluyendo la configuración de participantes, precios por sector y límites de entradas por cliente.

## ADDED Requirements

### Requirement: Crear eventos en estado BORRADOR
El sistema SHALL permitir que un usuario con rol ORGANIZADOR cree eventos y SHALL asignar como organizador al usuario identificado en la solicitud.

#### Scenario: Crear un evento con datos válidos
- **WHEN** un usuario con rol ORGANIZADOR envía los datos válidos de un evento
- **THEN** el sistema crea el evento en estado BORRADOR y lo asocia al organizador que realizó la solicitud

#### Scenario: Crear un evento sin rol ORGANIZADOR
- **WHEN** un usuario con otro rol intenta crear un evento
- **THEN** el sistema rechaza la operación con un error 403

#### Scenario: Crear un evento con datos inválidos
- **WHEN** un organizador envía datos obligatorios faltantes o inválidos
- **THEN** el sistema rechaza la solicitud indicando los errores de validación

### Requirement: Configurar el límite de entradas por cliente
El sistema SHALL permitir establecer un máximo de entradas por cliente, cuyo valor predeterminado será 4 y cuyo valor mínimo será 1.

#### Scenario: Crear un evento sin especificar el límite
- **WHEN** un organizador crea un evento sin indicar el máximo de entradas por cliente
- **THEN** el sistema establece el límite en 4

#### Scenario: Crear un evento con un límite válido
- **WHEN** un organizador crea un evento indicando un máximo de entradas por cliente igual o superior a 1
- **THEN** el sistema guarda el límite indicado

#### Scenario: Crear un evento con un límite menor que 1
- **WHEN** un organizador intenta crear un evento indicando un máximo de entradas por cliente menor que 1
- **THEN** el sistema rechaza la solicitud por datos inválidos

### Requirement: Configurar participantes y precios por sector
El sistema SHALL permitir asociar participantes y configurar precios para los sectores del lugar seleccionado mientras el evento se encuentre en estado BORRADOR.

#### Scenario: Configurar precios de sectores de un evento en borrador
- **WHEN** el organizador configura precios para sectores del lugar de un evento en estado BORRADOR
- **THEN** el sistema guarda la configuración de precios asociada a cada sector

#### Scenario: Modificar precios de un evento que no está en borrador
- **WHEN** el organizador intenta modificar los precios de un evento que no está en estado BORRADOR
- **THEN** el sistema rechaza la operación con un error 409

### Requirement: Editar eventos propios en estado BORRADOR
El sistema SHALL permitir que un organizador edite los datos de sus propios eventos únicamente mientras se encuentren en estado BORRADOR.

#### Scenario: Editar un evento propio en borrador
- **WHEN** el organizador dueño modifica los datos de un evento en estado BORRADOR
- **THEN** el sistema guarda los cambios y devuelve el evento actualizado

#### Scenario: Editar un evento de otro organizador
- **WHEN** un organizador intenta editar un evento cuyo dueño es otro organizador
- **THEN** el sistema rechaza la operación con un error 403

#### Scenario: Editar un evento que no está en borrador
- **WHEN** el organizador dueño intenta editar un evento que no está en estado BORRADOR
- **THEN** el sistema rechaza la operación con un error 409

### Requirement: Consultar el detalle de un evento
El sistema SHALL permitir consultar el detalle de un evento existente mediante su identificador.

#### Scenario: Consultar un evento existente
- **WHEN** un usuario solicita el detalle de un evento registrado
- **THEN** el sistema devuelve la información del evento, incluyendo sus datos de configuración

#### Scenario: Consultar un evento inexistente
- **WHEN** un usuario solicita el detalle de un identificador que no corresponde a ningún evento
- **THEN** el sistema devuelve un error 404

### Requirement: Configurar eventos de alta demanda
El sistema SHALL permitir que el organizador establezca o modifique la condición de alta demanda de sus eventos mientras se encuentren en estado BORRADOR.

#### Scenario: Marcar un evento propio como de alta demanda
- **WHEN** el organizador dueño edita un evento en estado BORRADOR y activa la opción de alta demanda
- **THEN** el sistema guarda la condición de alta demanda en el evento

#### Scenario: Desactivar la condición de alta demanda
- **WHEN** el organizador dueño edita un evento en estado BORRADOR y desactiva la opción de alta demanda
- **THEN** el sistema guarda el evento con la condición de alta demanda desactivada
