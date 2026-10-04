package com.naukriradar.common.redis.controller;

import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.redis.cache.CacheStats;
import com.naukriradar.common.redis.cache.TwoLevelCacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This service's caches. Through the gateway each service has its own path:
 * {@code /api/v1/admin/cache/job-service} reaches {@code /api/v1/admin/cache} here.
 * Hit counts are per instance.
 */
@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheAdminController {

	private final TwoLevelCacheManager caches;

	public CacheAdminController(TwoLevelCacheManager caches) {
		this.caches = caches;
	}

	@GetMapping
	public List<CacheStats> list() {
		return caches.stats();
	}

	/** Empties the cache here, in Redis and on every other instance. */
	@DeleteMapping("/{name}")
	public ResponseEntity<Void> clear(@PathVariable("name") String name) {
		caches.find(name).orElseThrow(() -> new NotFoundException("No cache " + name + ".")).clear();
		return ResponseEntity.noContent().build();
	}

}
