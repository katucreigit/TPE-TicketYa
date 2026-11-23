-- Quita los turnos vencidos y devuelve a quienes quito (design.md D5). Como la busqueda y el ZREM van en el mismo
-- script, cada vencimiento lo devuelve una sola instancia y se registra una sola vez.
-- KEYS: 1 habilitados
-- ARGV: 1 ahora (epoch ms), 2 maximo por llamada
local vencidos = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
if #vencidos > 0 then
  redis.call('ZREM', KEYS[1], unpack(vencidos))
end
return vencidos
