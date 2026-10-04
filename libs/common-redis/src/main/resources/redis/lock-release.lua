-- Deletes the lock only if we still own it. A plain DEL could remove a lock that expired
-- and was taken by someone else in the meantime.
if redis.call('GET', KEYS[1]) == ARGV[1] then
	return redis.call('DEL', KEYS[1])
end
return 0
