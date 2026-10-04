package com.naukriradar.gateway.ratelimit;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate limits applied at the gateway. The first rule whose paths and method match a request
 * decides; a request no rule matches is not limited.
 *
 * @param trustForwardedFor take the client IP from X-Forwarded-For. Only behind a proxy that
 *     sets it: otherwise a client can put any address there and get a fresh bucket each time
 * @param timeout how long to wait for Redis before letting the request through anyway
 */
@ConfigurationProperties("naukriradar.rate-limit")
public record RateLimitProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("naukriradar:gateway:rate") String keyPrefix,
		@DefaultValue("false") boolean trustForwardedFor,
		@DefaultValue("500ms") Duration timeout,
		@DefaultValue List<Rule> rules) {

	public RateLimitProperties {
		Set<String> names = new HashSet<>();
		for (Rule rule : rules) {
			if (!names.add(rule.name())) {
				throw new IllegalArgumentException("Two rate limit rules named " + rule.name());
			}
		}
	}

	/**
	 * @param methods HTTP methods the rule covers; empty means all
	 * @param capacity requests allowed in a burst
	 * @param refillEvery time to earn back one request; the steady rate is one per this
	 * @param by whose bucket a request takes from
	 */
	public record Rule(String name, List<String> paths, @DefaultValue List<String> methods, int capacity,
			Duration refillEvery, @DefaultValue("USER_OR_IP") KeyType by) {

		public Rule {
			if (name == null || !name.matches("[a-z0-9-]+")) {
				throw new IllegalArgumentException("Rate limit rule name must be lowercase letters, digits and dashes: " + name);
			}
			if (paths == null || paths.isEmpty()) {
				throw new IllegalArgumentException("Rate limit rule " + name + " needs at least one path");
			}
			if (capacity < 1) {
				throw new IllegalArgumentException("Rate limit rule " + name + " needs a capacity of at least 1");
			}
			if (refillEvery == null || refillEvery.toMillis() < 1) {
				throw new IllegalArgumentException("Rate limit rule " + name + " needs a refill time of at least 1ms");
			}
			methods = methods.stream().map(m -> m.strip().toUpperCase()).toList();
		}

	}

	public enum KeyType {

		/** The signed-in user if the request has one, else the client IP. */
		USER_OR_IP,

		/** Always the client IP, for endpoints used before signing in. */
		IP

	}

}
