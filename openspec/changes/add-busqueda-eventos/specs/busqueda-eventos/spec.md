# Spec Delta

## Purpose
Permite a cualquier usuario encontrar eventos publicados combinando criterios, con paginación y orden.

## ADDED Requirements

### Requirement: Solo eventos publicados
Cuando se ejecuta una búsqueda, el sistema SHALL considerar únicamente eventos en estado PUBLICADO.

#### Scenario: Excluye borradores
- **WHEN** se busca sin filtros
- **THEN** no aparecen eventos BORRADOR ni CANCELADO

### Requirement: Filtros combinados
Cuando se envían varios filtros (texto, categoría, fechas, lugar, precio), el sistema SHALL devolver los eventos que cumplen todos a la vez.

#### Scenario: Categoría y fecha
- **WHEN** se filtra por categoría y rango de fechas
- **THEN** solo se devuelven eventos que cumplen ambos

### Requirement: Rango inválido
Si la fecha desde es posterior a la fecha hasta, entonces el sistema SHALL responder 400.

#### Scenario: Fechas invertidas
- **WHEN** desde es mayor que hasta
- **THEN** el sistema responde 400

### Requirement: Paginación y orden
El sistema SHALL paginar los resultados con tamaño máximo de página y permitir ordenar por fecha o precio.

#### Scenario: Orden por precio
- **WHEN** se pide orden por precio ascendente
- **THEN** los resultados vienen ordenados por precio

### Requirement: Disponibilidad
Cuando se solicita filtrar por disponibilidad, el sistema SHALL incluir solo eventos con entradas disponibles; si ms-reservas no responde, SHALL indicar disponibilidad desconocida.

#### Scenario: Servicio caído
- **WHEN** ms-reservas no responde en el tiempo límite
- **THEN** la búsqueda responde 200
- **AND** la disponibilidad figura como desconocida

### Requirement: Sin resultados
Si ningún evento cumple los filtros, entonces el sistema SHALL responder 200 con una lista vacía.

#### Scenario: Lista vacía
- **WHEN** ningún evento coincide
- **THEN** el sistema responde 200 con página vacía
