-- Habilita la tanda si ya llego su momento (design.md D4). La atomicidad del script es la exclusion entre
-- instancias: la primera que lo ejecuta saca la tanda y corre proximaTandaEn; las demas no hacen nada.
-- KEYS: 1 config, 2 espera, 3 habilitados, 4 secuencia
-- ARGV: 1 ahora (epoch ms)
-- Devuelve {} si no corresponde tanda; si no, {habilitadoHasta, cliente1, cliente2, ...} en orden de llegada.
if redis.call('HGET', KEYS[1], 'activa') ~= '1' then
  return {}
end
local ahora = tonumber(ARGV[1])
local proxima = tonumber(redis.call('HGET', KEYS[1], 'proximaTandaEn'))
if ahora < proxima then
  return {}
end
local tamano = tonumber(redis.call('HGET', KEYS[1], 'tamanoTanda'))
local segundos = tonumber(redis.call('HGET', KEYS[1], 'segundosEntreTandas'))
local ventana = tonumber(redis.call('HGET', KEYS[1], 'minutosVentana')) * 60000
local hasta = string.format('%.0f', ahora + ventana)
local resultado = {hasta}
-- ZPOPMIN saca a cada cliente una sola vez: nadie queda habilitado dos veces.
local sacados = redis.call('ZPOPMIN', KEYS[2], tamano)
for i = 1, #sacados, 2 do
  redis.call('ZADD', KEYS[3], hasta, sacados[i])
  table.insert(resultado, sacados[i])
end
-- Avanza aunque la fila este vacia: el ritmo nunca supera tamanoTanda cada segundosEntreTandas.
redis.call('HSET', KEYS[1], 'proximaTandaEn', string.format('%.0f', ahora + segundos * 1000))
local ttl = redis.call('HGET', KEYS[1], 'ttl')
if ttl then
  for i = 1, #KEYS do
    redis.call('EXPIRE', KEYS[i], ttl)
  end
end
return resultado
