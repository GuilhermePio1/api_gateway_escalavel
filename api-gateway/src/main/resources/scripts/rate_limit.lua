local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])
local window_ms = tonumber(ARGV[4])

-- 1. Limpeza de dados antigos
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 2. Verificação de volume
local current_count = redis.call('ZCARD', key)
local allowed = current_count < max_requests

if allowed then
    -- Adiciona a requisição atual com identificador único
    redis.call('ZADD', key, now, now .. ':' .. redis.call('INCR', 'gen_id:' .. key))
    current_count = current_count + 1
end

-- 3. Atualiza expiração para durar pelo menos o tempo da janela completa
redis.call('PEXPIRE', key, window_ms)

-- 4. Retorno para o Java (count, ttl em ms, allowed flag)
local ttl = redis.call('PTTL', key)
return {current_count, ttl, allowed and 1 or 0}