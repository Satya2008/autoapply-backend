package com.naukriradar.common.redis.cache;

import java.time.Duration;

/**
 * Counters since this instance started.
 *
 * @param localHits served from this instance's memory
 * @param sharedHits not in memory but found in Redis
 * @param misses in neither; the method ran, or another caller's load was awaited
 * @param waits misses that waited for another caller's load instead of loading again
 * @param errors Redis calls that failed; each was treated as a miss
 */
public record CacheStats(String name, long localSize, long localHits, long sharedHits, long misses, long loads,
		long waits, long errors, double hitRatio, double averageLoadMillis, Duration localTtl, Duration sharedTtl) {
}
