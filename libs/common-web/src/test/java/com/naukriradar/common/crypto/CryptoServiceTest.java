package com.naukriradar.common.crypto;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private final CryptoService crypto = new CryptoService(KEY);

	@Test
	void roundTrips() {
		String sealed = crypto.encrypt("sk-live-123 ₹ ✓", "ai.api-key");

		assertThat(sealed).startsWith("v1:").doesNotContain("sk-live");
		assertThat(crypto.decrypt(sealed, "ai.api-key")).isEqualTo("sk-live-123 ₹ ✓");
	}

	@Test
	void sameSecretEncryptsDifferentlyEachTime() {
		assertThat(crypto.encrypt("same", "k")).isNotEqualTo(crypto.encrypt("same", "k"));
	}

	@Test
	void aValueCopiedToAnotherSettingDoesNotDecrypt() {
		String sealed = crypto.encrypt("secret", "ai.api-key");

		assertThatThrownBy(() -> crypto.decrypt(sealed, "smtp.password")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void tamperingIsDetected() {
		String sealed = crypto.encrypt("secret", "k");
		byte[] bytes = Base64.getDecoder().decode(sealed.substring(3));
		bytes[bytes.length - 1] ^= 1;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> crypto.decrypt(tampered, "k")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> crypto.decrypt("plain text", "k")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> crypto.decrypt("v1:AAAA", "k")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anotherKeyCannotDecrypt() {
		String sealed = crypto.encrypt("secret", "k");
		byte[] other = new byte[32];
		other[0] = 1;

		assertThatThrownBy(() -> new CryptoService(Base64.getEncoder().encodeToString(other)).decrypt(sealed, "k"))
				.isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not base64!", "AAAA" })
	void refusesToStartWithoutAProperKey(String key) {
		assertThatThrownBy(() -> new CryptoService(key)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("encryption-key");
	}

}
