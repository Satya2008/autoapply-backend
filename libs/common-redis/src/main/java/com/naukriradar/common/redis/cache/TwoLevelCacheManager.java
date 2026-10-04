package com.naukriradar.common.redis.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.redis.RedisKeys;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds the caches a service declared as {@link CacheSpec} beans. Asking for a cache nobody
 * declared returns null, so a typo in {@code @Cacheable} fails loudly at the first call
 * instead of quietly creating an unbounded cache.
 */
public class TwoLevelCacheManager implements CacheManager {

	private final Map<String, TwoLevelCache> caches;

	public TwoLevelCacheManager(List<CacheSpec> specs, StringRedisTemplate redis, RedisKeys keys, JsonMapper json,
			CacheStampedeGuard guard, CacheEvictionBus bus) {
		Map<String, TwoLevelCache> byName = new LinkedHashMap<>();
		for (CacheSpec spec : specs) {
			if (byName.put(spec.name(), new TwoLevelCache(spec, redis, keys, json, guard, bus)) != null) {
				throw new IllegalStateException("Two caches named " + spec.name());
			}
		}
		this.caches = Collections.unmodifiableMap(byName);
		bus.onEviction(eviction -> {
			TwoLevelCache cache = caches.get(eviction.cache());
			if (cache != null) {
				cache.evictLocally(eviction.kind(), eviction.key());
			}
		});
	}

	@Override
	public Cache getCache(String name) {
		return caches.get(name);
	}

	@Override
	public Collection<String> getCacheNames() {
		return caches.keySet();
	}

	public Optional<TwoLevelCache> find(String name) {
		return Optional.ofNullable(caches.get(name));
	}

	public List<CacheStats> stats() {
		return caches.values().stream().map(TwoLevelCache::stats).toList();
	}

}
