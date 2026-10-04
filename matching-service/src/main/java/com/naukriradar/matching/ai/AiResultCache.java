package com.naukriradar.matching.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.naukriradar.common.redis.cache.TwoLevelCache;
import com.naukriradar.common.redis.cache.TwoLevelCacheManager;
import com.naukriradar.matching.config.CacheConfig;
import org.springframework.stereotype.Component;

/**
 * Answers already paid for, shared by all instances through Redis. The key is the prompt
 * version plus a hash of the exact text sent, so a changed profile, job or prompt is a new
 * question, and an unchanged one is never paid for twice.
 */
@Component
public class AiResultCache {

	private final TwoLevelCache cache;

	public AiResultCache(TwoLevelCacheManager caches) {
		this.cache = caches.find(CacheConfig.AI_RESULTS)
				.orElseThrow(() -> new IllegalStateException("Cache " + CacheConfig.AI_RESULTS + " is not declared"));
	}

	public AiResult get(String key) {
		return cache.get(key, AiResult.class);
	}

	public void put(String key, AiResult result) {
		cache.put(key, result);
	}

	static String key(ActivePrompt prompt, AiRequest request) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update((request.system() == null ? "" : request.system()).getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
			sha.update(request.prompt().getBytes(StandardCharsets.UTF_8));
			return prompt.code() + ":v" + prompt.version() + ":" + HexFormat.of().formatHex(sha.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
