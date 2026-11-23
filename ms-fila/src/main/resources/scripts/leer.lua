-- Lectura consistente del estado de un cliente: activa, fin de su ventana (si esta habilitado) y posicion.
-- KEYS: 1 config, 2 espera, 3 habilitados
-- ARGV: 1 clienteId
-- Devuelve {activa, habilitadoHasta (texto o ''), posicion (0 si no esta en espera)}
if redis.call('HGET', KEYS[1], 'activa') ~= '1' then
  return {0, '', 0}
end
local hasta = redis.call('ZSCORE', KEYS[3], ARGV[1]) or ''
local rango = redis.call('ZRANK', KEYS[2], ARGV[1])
local posicion = 0
if rango then
  posicion = rango + 1
end
return {1, hasta, posicion}
