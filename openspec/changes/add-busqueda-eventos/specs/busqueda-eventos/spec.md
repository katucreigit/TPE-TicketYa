# Spec Delta

## Purpose
Permite a cualquier usuario encontrar eventos combinando criterios, con orden estable y paginación.

## ADDED Requirements

### Requirement: Eventos visibles
Cuando se ejecuta una búsqueda, el sistema SHALL devolver únicamente eventos PUBLICADO o AGOTADO, nunca borradores, suspendidos ni cancelados.

#### Scenario: Excluye otros estados
- **WHEN** existen eventos en todos los estados
- **THEN** solo aparecen los PUBLICADO y AGOTADO

### Requirement: Filtros combinados
Cuando se envían varios filtros entre tipo, organizadorId, participante, ciudad, fechaDesde, fechaHasta y q, el sistema SHALL devolver solo los eventos que cumplen todos a la vez.

#### Scenario: Tipo y ciudad
- **WHEN** se filtra por tipo y ciudad
- **THEN** solo se devuelven eventos que cumplen ambos

### Requirement: Texto libre
Cuando se envía q, el sistema SHALL compararlo sin distinguir mayúsculas contra nombre y descripción del evento, tipo y nombres de participantes.

#### Scenario: Búsqueda por participante
- **WHEN** q coincide con el nombre de un participante
- **THEN** el evento aparece en los resultados

### Requirement: Rango de fechas
Si fechaDesde es posterior a fechaHasta, entonces el sistema SHALL responder 400.

#### Scenario: Fechas invertidas
- **WHEN** fechaDesde es mayor que fechaHasta
- **THEN** el sistema responde 400

### Requirement: Orden
Cuando se indica orden por fecha, precio o relevancia y dirección asc o desc, el sistema SHALL ordenar en consecuencia, usando el precio mínimo de los sectores para el precio.

#### Scenario: Orden por precio
- **WHEN** se ordena por precio ascendente
- **THEN** se usa el menor precio de cada evento

#### Scenario: Relevancia
- **WHEN** se ordena por relevancia con q
- **THEN** los eventos con coincidencia en el nombre van primero

### Requirement: Orden estable
Si dos eventos tienen el mismo valor de ordenamiento, el sistema SHALL desempatar por fecha y luego por id.

#### Scenario: Empate
- **WHEN** dos eventos tienen el mismo precio mínimo
- **THEN** el orden entre ellos es siempre el mismo

### Requirement: Paginación y vacío
El sistema SHALL paginar con page y size e incluir el total; si nada coincide, SHALL responder 200 con lista vacía.

#### Scenario: Sin resultados
- **WHEN** ningún evento coincide
- **THEN** el sistema responde 200 con lista vacía
