-- Organizadores
INSERT INTO organizador (id, nombre, email) OVERRIDING SYSTEM VALUE VALUES
    (1, 'María García',  'maria@ticketya.com'),
    (2, 'Carlos López',  'carlos@ticketya.com')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('organizador', 'id'), GREATEST((SELECT MAX(id) FROM organizador), 1));

-- Lugares
INSERT INTO lugar (id, nombre, direccion, ciudad, latitud, longitud) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Estadio Ciudad de Tandil',   'Av. Don Bosco 1200',    'Tandil',    -37.3217, -59.1332),
    (2, 'Teatro del Lago',            'Calle Principal 500',   'Bariloche', -41.1335, -71.3103),
    (3, 'Luna Park',                  'Bouchard 465',          'Buenos Aires', -34.6017, -58.3686)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('lugar', 'id'), GREATEST((SELECT MAX(id) FROM lugar), 1));

-- Sectores
INSERT INTO sector (id, lugar_id, nombre, tipo, capacidad, filas, asientos_por_fila) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Campo',       'NO_NUMERADO', 5000, NULL, NULL),
    (2, 1, 'Platea Alta', 'NUMERADO',    NULL, 20,   30),
    (3, 2, 'Platea',      'NUMERADO',    NULL, 15,   25),
    (4, 2, 'Galería',     'NO_NUMERADO', 300,  NULL, NULL),
    (5, 3, 'Campo',       'NO_NUMERADO', 8000, NULL, NULL),
    (6, 3, 'Platea',      'NUMERADO',    NULL, 30,   40)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('sector', 'id'), GREATEST((SELECT MAX(id) FROM sector), 1));

-- Participantes
INSERT INTO participante (id, nombre, tipo) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Los Andariegos',    'ARTISTA'),
    (2, 'La Beriso',         'ARTISTA'),
    (3, 'River Plate',       'EQUIPO'),
    (4, 'Boca Juniors',      'EQUIPO'),
    (5, 'Martín Caparrós',   'DISERTANTE')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('participante', 'id'), GREATEST((SELECT MAX(id) FROM participante), 1));

-- Eventos
INSERT INTO evento (id, nombre, descripcion, tipo, organizador_id, lugar_id, fecha_hora, estado, max_entradas_por_cliente, alta_demanda, motivo_cancelacion, creado_en) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Festival de Verano 2027',   'Dos noches de rock nacional',           'RECITAL',     1, 1, '2027-01-15 21:00:00', 'PUBLICADO', 4, true,  NULL, NOW()),
    (2, 'Clásico de Verano',         'River vs Boca en la pretemporada',      'DEPORTE',     2, 3, '2027-02-20 20:00:00', 'PUBLICADO', 2, false, NULL, NOW()),
    (3, 'Jornada de Innovación 2027','Charlas sobre tecnología y startups',   'CONFERENCIA', 1, 2, '2027-03-10 09:00:00', 'BORRADOR',  4, false, NULL, NOW()),
    (4, 'La Beriso en Bariloche',    'Noche de rock en la Patagonia',         'RECITAL',     2, 2, '2027-04-05 21:00:00', 'AGOTADO',   4, true,  NULL, NOW())
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('evento', 'id'), GREATEST((SELECT MAX(id) FROM evento), 1));

-- Precios por sector (solo eventos no BORRADOR)
INSERT INTO evento_sector (id, evento_id, sector_id, precio) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 28000.00),
    (2, 1, 2, 45000.00),
    (3, 2, 5, 15000.00),
    (4, 2, 6, 25000.00),
    (5, 4, 3, 35000.00),
    (6, 4, 4, 20000.00)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('evento_sector', 'id'), GREATEST((SELECT MAX(id) FROM evento_sector), 1));

-- Participantes de cada evento
INSERT INTO evento_participante (evento_id, participante_id) VALUES
    (1, 1),
    (1, 2),
    (2, 3),
    (2, 4),
    (3, 5),
    (4, 2)
ON CONFLICT DO NOTHING;
