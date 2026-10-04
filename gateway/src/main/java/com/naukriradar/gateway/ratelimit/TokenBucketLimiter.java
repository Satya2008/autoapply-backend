package com.naukriradar.gateway.ratelimit;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Takes one token from a bucket in Redis, in a single Lua call. */
@Component
public class TokenBucketLimiter {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static final RedisScript<List<Long>> SCRIPT = (RedisScript) RedisScript
			.of(new ClassPathResource("redis/token-bucket.lua"), List.class);

	private final ReactiveStringRedisTemplate redis;

	public TokenBucketLimiter(ReactiveStringRedisTemplate redis) {
		this.redis = redis;
	}

	public Mono<Decision> take(String bucket, RateLimitProperties.Rule rule) {
		return redis.execute(SCRIPT, List.of(bucket),
				List.of(String.valueOf(rule.capacity()), String.valueOf(rule.refillEvery().toMillis())))
				.next()
				.map(result -> new Decision(result.get(0) == 1L, result.get(1), result.get(2)));
	}

	/**
	 * @param remaining requests left in the burst after this one
	 * @param retryAfterMillis when refused, how long until a request would be allowed
	 */
	public record Decision(boolean allowed, long remaining, long retryAfterMillis) {
	}

}
