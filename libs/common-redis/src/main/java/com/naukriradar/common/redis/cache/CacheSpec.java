package com.naukriradar.common.redis.cache;

import java.lang.reflect.Type;
import java.time.Duration;

/**
 * One two-level cache. Each service declares its caches as beans of this type.
 *
 * @param valueType what the cached method returns; values are stored in Redis as JSON of
 *     exactly this type, never with class names inside, so a value in Redis can't make us
 *     instantiate an arbitrary class
 * @param localTtl how long a value lives in this instance's memory. Kept short: it is also
 *     how long another instance may serve a stale value if an eviction message is lost
 * @param sharedTtl how long a value lives in Redis
 */
public record CacheSpec(String name, Type valueType, Duration localTtl, long localMaxSize, Duration sharedTtl) {

	public CacheSpec {
		if (name == null || !name.matches("[a-z0-9][a-z0-9-]*")) {
			throw new IllegalArgumentException("Cache name must be lowercase letters, digits and dashes: " + name);
		}
		if (valueType == null) {
			throw new IllegalArgumentException("Cache " + name + " needs a value type");
		}
		if (localTtl == null || localTtl.isNegative() || localTtl.isZero()) {
			throw new IllegalArgumentException("Cache " + name + " needs a positive local TTL");
		}
		if (sharedTtl == null || sharedTtl.compareTo(localTtl) < 0) {
			throw new IllegalArgumentException("Cache " + name + ": the shared TTL can't be shorter than the local one");
		}
		if (localMaxSize < 1) {
			throw new IllegalArgumentException("Cache " + name + " needs room for at least one local entry");
		}
	}

}
