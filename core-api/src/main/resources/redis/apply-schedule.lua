-- Puts an application in the delay queue after the user's last scheduled one.
-- KEYS[1] the queue (sorted set), KEYS[2] the user's last slot
-- ARGV[1] application id, ARGV[2] now (ms), ARGV[3] gap (ms). Returns the due time.
local last = tonumber(redis.call('GET', KEYS[2]) or '0')
local now = tonumber(ARGV[2])
local due = math.max(now, last + tonumber(ARGV[3]))
-- the first one of a burst can go now; the next ones wait their gap
if last < now - tonumber(ARGV[3]) then
	due = now
end
redis.call('ZADD', KEYS[1], due, ARGV[1])
redis.call('SET', KEYS[2], tostring(due), 'PX', tonumber(ARGV[3]) * 2 + 60000)
return due
