-- Activa la fila o actualiza sus parametros si ya estaba activa (design.md D2 y D4).
-- KEYS: 1 config, 2 espera, 3 habilitados, 4 secuencia
-- ARGV: 1 tamanoTanda, 2 segundosEntreTandas, 3 minutosVentana, 4 ahora (epoch ms), 5 ttl (segundos)
-- Devuelve: {estabaActiva, tamanoTanda, segundosEntreTandas, minutosVentana, proximaTandaEn, enEspera, habilitados}
local estaba = redis.call('HGET', KEYS[1], 'activa') == '1'
redis.call('HSET', KEYS[1], 'activa', '1', 'tamanoTanda', ARGV[1], 'segundosEntreTandas', ARGV[2], 'ttl', ARGV[5])
if not estaba then
  -- La primera tanda sale segundosEntreTandas despues de abrir, para que entren los que llegan en la apertura.
  local proxima = tonumber(ARGV[4]) + tonumber(ARGV[2]) * 1000
  redis.call('HSET', KEYS[1], 'minutosVentana', ARGV[3], 'proximaTandaEn', string.format('%.0f', proxima))
end
for i = 1, #KEYS do
  redis.call('EXPIRE', KEYS[i], ARGV[5])
end
return {
  estaba and 1 or 0,
  tonumber(redis.call('HGET', KEYS[1], 'tamanoTanda')),
  tonumber(redis.call('HGET', KEYS[1], 'segundosEntreTandas')),
  tonumber(redis.call('HGET', KEYS[1], 'minutosVentana')),
  redis.call('HGET', KEYS[1], 'proximaTandaEn'),
  redis.call('ZCARD', KEYS[2]),
  redis.call('ZCARD', KEYS[3])
}
