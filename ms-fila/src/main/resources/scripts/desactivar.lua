-- Desactiva la fila y descarta a los clientes en espera y a los habilitados.
-- KEYS: 1 config, 2 espera, 3 habilitados, 4 secuencia
-- Devuelve: {estabaActiva, enEspera, habilitados} con los tamaños previos al borrado.
local estaba = redis.call('HGET', KEYS[1], 'activa') == '1'
local enEspera = redis.call('ZCARD', KEYS[2])
local habilitados = redis.call('ZCARD', KEYS[3])
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4])
if estaba then
  return {1, enEspera, habilitados}
end
return {0, 0, 0}
