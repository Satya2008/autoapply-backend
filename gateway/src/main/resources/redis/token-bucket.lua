-- Token bucket, one hash per caller and rule: {tokens, at}.
-- KEYS[1] the bucket; ARGV[1] capacity; ARGV[2] milliseconds to refill one token.
-- Returns {allowed (1/0), tokens left, milliseconds until the next token when refused}.
--
-- Runs as one script, so reading, refilling and taking a token is atomic: two gateway
-- instances can never both take the last token. Time comes from Redis itself, so gateways
-- with different clocks still agree.
local capacity = tonumber(ARGV[1])
local refill_ms = tonumber(ARGV[2])

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'at')
local tokens = tonumber(bucket[1])
local at = tonumber(bucket[2])
if tokens == nil or at == nil then
	tokens = capacity
	at = now
end

if now > at then
	tokens = math.min(capacity, tokens + (now - at) / refill_ms)
	at = now
end

local allowed = 0
local retry_after = 0
if tokens >= 1 then
	tokens = tokens - 1
	allowed = 1
else
	retry_after = math.ceil((1 - tokens) * refill_ms)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', tostring(at))
-- an idle bucket is full again after capacity * refill_ms; no need to keep it longer
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * refill_ms) + 1000)
return {allowed, math.floor(tokens), retry_after}
