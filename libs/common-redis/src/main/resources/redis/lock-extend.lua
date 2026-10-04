-- Sets a new expiry (ms) only if we still own the lock. Returns 0 when it was lost.
if redis.call('GET', KEYS[1]) == ARGV[1] then
	return redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
return 0
