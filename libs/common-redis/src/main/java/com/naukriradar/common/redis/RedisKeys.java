package com.naukriradar.common.redis;

/**
 * Builds the Redis keys of one service. Every key starts with the same prefix
 * ({@code naukriradar:<service>:}), so services sharing a Redis never see each other's data
 * and one service's keys can be found with a single pattern.
 */
public final class RedisKeys {

	private final String prefix;

	public RedisKeys(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			throw new IllegalArgumentException("Redis key prefix must not be blank");
		}
		this.prefix = prefix.endsWith(":") ? prefix : prefix + ":";
	}

	public String prefix() {
		return prefix;
	}

	public String key(String... parts) {
		return prefix + String.join(":", parts);
	}

	/** Escapes glob characters, so a key part is matched literally by SCAN MATCH. */
	public static String escapeGlob(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
				out.append('\\');
			}
			out.append(c);
		}
		return out.toString();
	}

}
