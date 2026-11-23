-- Ingreso idempotente a la fila, en un solo paso atomico (design.md D3).
-- KEYS: 1 config, 2 espera, 3 habilitados, 4 secuencia
-- ARGV: 1 clienteId, 2 ahora (epoch ms)
-- Devuelve {resultado, posicion, nuevo, habilitadoHasta, vencido}:
--   resultado -1 fila no activa; 1 en espera; 2 habilitado (habilitadoHasta en epoch ms, como texto)
--   nuevo 1 si se le asigno un lugar en esta llamada; vencido 1 si se le quito un turno vencido
if redis.call('HGET', KEYS[1], 'activa') ~= '1' then
  return {-1, 0, 0, '', 0}
end
local ahora = tonumber(ARGV[2])
local vencido = 0
local hasta = redis.call('ZSCORE', KEYS[3], ARGV[1])
if hasta then
  if tonumber(hasta) > ahora then
    return {2, 0, 0, hasta, 0}
  end
  -- Turno vencido que el proceso todavia no quito: se quita aca (una sola vez) y el cliente entra de nuevo al final.
  redis.call('ZREM', KEYS[3], ARGV[1])
  vencido = 1
end
local rango = redis.call('ZRANK', KEYS[2], ARGV[1])
if rango then
  return {1, rango + 1, 0, '', vencido}
end
local numero = redis.call('INCR', KEYS[4])
redis.call('ZADD', KEYS[2], numero, ARGV[1])
local ttl = redis.call('HGET', KEYS[1], 'ttl')
if ttl then
  redis.call('EXPIRE', KEYS[2], ttl)
  redis.call('EXPIRE', KEYS[4], ttl)
end
return {1, redis.call('ZRANK', KEYS[2], ARGV[1]) + 1, 1, '', vencido}
