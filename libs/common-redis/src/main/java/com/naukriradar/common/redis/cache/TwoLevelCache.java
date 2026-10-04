package com.naukriradar.common.redis.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.common.redis.cache.CacheEvictionBus.Eviction;
import com.naukriradar.common.redis.cache.CacheEvictionBus.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside in two levels: this instance's memory (Caffeine) first, then Redis, then the
 * real method. A Redis hit is copied into memory, a load is written to both.
 *
 * <p>Redis is an optimisation here, never a dependency: if a Redis call fails it counts as a
 * miss and the method runs. A value in Redis that no longer reads as the expected type (the
 * class changed between deploys) is deleted and reloaded.
 *
 * <p>Null results are not cached. Evictions go to Redis and to the other instances, which
 * drop the entry from memory.
 */
public class TwoLevelCache implements Cache {

	private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);

	private static final int DELETE_BATCH = 500;

	private final CacheSpec spec;
	private final com.github.benmanes.caffeine.cache.Cache<String, Object> local;
	private final StringRedisTemplate redis;
	private final RedisKeys keys;
	private final JsonMapper json;
	private final JavaType type;
	private final CacheStampedeGuard guard;
	private final CacheEvictionBus bus;

	private final LongAdder localHits = new LongAdder();
	private final LongAdder sharedHits = new LongAdder();
	private final LongAdder misses = new LongAdder();
	private final LongAdder loads = new LongAdder();
	private final LongAdder loadNanos = new LongAdder();
	private final LongAdder waits = new LongAdder();
	private final LongAdder errors = new LongAdder();

	public TwoLevelCache(CacheSpec spec, StringRedisTemplate redis, RedisKeys keys, JsonMapper json,
			CacheStampedeGuard guard, CacheEvictionBus bus) {
		this.spec = spec;
		this.redis = redis;
		this.keys = keys;
		this.json = json;
		this.type = json.getTypeFactory().constructType(spec.valueType());
		this.guard = guard;
		this.bus = bus;
		this.local = Caffeine.newBuilder()
				.expireAfterWrite(spec.localTtl())
				.maximumSize(spec.localMaxSize())
				.build();
	}

	@Override
	public String getName() {
		return spec.name();
	}

	@Override
	public Object getNativeCache() {
		return local;
	}

	@Override
	public ValueWrapper get(Object key) {
		Object value = lookup(String.valueOf(key));
		return value == null ? null : new SimpleValueWrapper(value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Object key, Class<T> type) {
		Object value = lookup(String.valueOf(key));
		if (value != null && type != null && !type.isInstance(value)) {
			throw new IllegalStateException("Cached value in " + spec.name() + " is not a " + type.getName());
		}
		return (T) value;
	}

	/** Used by {@code @Cacheable(sync = true)}: the stampede-safe path. */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Object key, Callable<T> valueLoader) {
		String k = String.valueOf(key);
		Object value = lookup(k);
		if (value != null) {
			return (T) value;
		}
		try {
			return (T) guard.load(keys.key("cache-load", spec.name(), k), () -> readShared(k).map(v -> {
				local.put(k, v);
				return v;
			}), () -> loadAndStore(k, valueLoader), waits::increment);
		}
		catch (Exception ex) {
			// Spring unwraps this and rethrows what the method threw
			throw new ValueRetrievalException(key, valueLoader, ex);
		}
	}

	@Override
	public void put(Object key, Object value) {
		String k = String.valueOf(key);
		if (value == null) {
			evict(k);
			return;
		}
		local.put(k, value);
		writeShared(k, value);
	}

	@Override
	public void evict(Object key) {
		String k = String.valueOf(key);
		local.invalidate(k);
		try {
			redis.delete(entryKey(k));
		}
		catch (RuntimeException ex) {
			failed("evict", ex);
		}
		bus.publish(new Eviction(spec.name(), Kind.KEY, k));
	}

	/** Drops every entry whose key starts with {@code prefix}, here, in Redis and on the other instances. */
	public void evictByPrefix(String prefix) {
		if (prefix == null || prefix.isEmpty()) {
			throw new IllegalArgumentException("Use clear() to drop everything");
		}
		evictLocalByPrefix(prefix);
		deleteShared(RedisKeys.escapeGlob(entryKey(prefix)) + "*");
		bus.publish(new Eviction(spec.name(), Kind.PREFIX, prefix));
	}

	@Override
	public void clear() {
		local.invalidateAll();
		deleteShared(RedisKeys.escapeGlob(entryKey("")) + "*");
		bus.publish(new Eviction(spec.name(), Kind.ALL, ""));
	}

	/** An eviction made by another instance: only our memory is affected, Redis is already done. */
	void evictLocally(Kind kind, String key) {
		switch (kind) {
			case KEY -> local.invalidate(key);
			case PREFIX -> evictLocalByPrefix(key);
			case ALL -> local.invalidateAll();
		}
	}

	public CacheStats stats() {
		long localHitCount = localHits.sum();
		long sharedHitCount = sharedHits.sum();
		long missCount = misses.sum();
		long loadCount = loads.sum();
		long lookups = localHitCount + sharedHitCount + missCount;
		double ratio = lookups == 0 ? 0 : (double) (localHitCount + sharedHitCount) / lookups;
		double averageLoad = loadCount == 0 ? 0 : loadNanos.sum() / 1_000_000.0 / loadCount;
		return new CacheStats(spec.name(), local.estimatedSize(), localHitCount, sharedHitCount, missCount, loadCount,
				waits.sum(), errors.sum(), Math.round(ratio * 1000) / 1000.0, Math.round(averageLoad * 100) / 100.0,
				spec.localTtl(), spec.sharedTtl());
	}

	private Object lookup(String key) {
		Object value = local.getIfPresent(key);
		if (value != null) {
			localHits.increment();
			return value;
		}
		Optional<Object> shared = readShared(key);
		if (shared.isPresent()) {
			sharedHits.increment();
			local.put(key, shared.get());
			return shared.get();
		}
		misses.increment();
		return null;
	}

	private Object loadAndStore(String key, Callable<?> loader) throws Exception {
		long started = System.nanoTime();
		Object value = loader.call();
		loads.increment();
		loadNanos.add(System.nanoTime() - started);
		if (value != null) {
			local.put(key, value);
			writeShared(key, value);
		}
		return value;
	}

	private Optional<Object> readShared(String key) {
		String text;
		try {
			text = redis.opsForValue().get(entryKey(key));
		}
		catch (RuntimeException ex) {
			failed("read", ex);
			return Optional.empty();
		}
		if (text == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(json.readValue(text, type));
		}
		catch (RuntimeException ex) {
			log.warn("Dropping an unreadable entry {} from cache {}: {}", key, spec.name(), ex.getMessage());
			try {
				redis.delete(entryKey(key));
			}
			catch (RuntimeException ignored) {
				// it expires anyway
			}
			return Optional.empty();
		}
	}

	private void writeShared(String key, Object value) {
		try {
			redis.opsForValue().set(entryKey(key), json.writeValueAsString(value), spec.sharedTtl());
		}
		catch (RuntimeException ex) {
			failed("write", ex);
		}
	}

	/** SCAN, not KEYS: KEYS blocks Redis while it walks the whole keyspace. */
	private void deleteShared(String pattern) {
		try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(DELETE_BATCH).build())) {
			List<String> batch = new ArrayList<>(DELETE_BATCH);
			while (cursor.hasNext()) {
				batch.add(cursor.next());
				if (batch.size() == DELETE_BATCH) {
					redis.delete(batch);
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				redis.delete(batch);
			}
		}
		catch (RuntimeException ex) {
			failed("clear", ex);
		}
	}

	private void evictLocalByPrefix(String prefix) {
		local.asMap().keySet().removeIf(k -> k.startsWith(prefix));
	}

	private String entryKey(String key) {
		return keys.key("cache", spec.name(), key);
	}

	private void failed(String operation, RuntimeException ex) {
		errors.increment();
		log.warn("Cache {} could not {} in Redis: {}", spec.name(), operation, ex.getMessage());
	}

}
