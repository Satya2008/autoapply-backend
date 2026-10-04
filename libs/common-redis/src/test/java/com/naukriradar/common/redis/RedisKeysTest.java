package com.naukriradar.common.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisKeysTest {

	@Test
	void keysShareTheServicePrefix() {
		assertThat(new RedisKeys("naukriradar:job-service").key("lock", "fetch-run"))
				.isEqualTo("naukriradar:job-service:lock:fetch-run");
		assertThat(new RedisKeys("p:").key("a")).isEqualTo("p:a");
		assertThatThrownBy(() -> new RedisKeys(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void globCharactersAreEscapedForScanMatch() {
		assertThat(RedisKeys.escapeGlob("user-1:*?[x]\\")).isEqualTo("user-1:\\*\\?\\[x\\]\\\\");
		assertThat(RedisKeys.escapeGlob("plain")).isEqualTo("plain");
	}

}
