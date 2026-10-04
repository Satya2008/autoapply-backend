package com.naukriradar.core.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts secret settings with AES-256-GCM before they reach the database.
 *
 * <ul>
 * <li>A fresh random 12-byte IV for every encryption. Reusing an IV with the same key in GCM
 * leaks the XOR of two plaintexts and lets an attacker forge messages.</li>
 * <li>The setting key is bound in as associated data, so an encrypted value copied from one
 * setting into another fails to decrypt instead of quietly being accepted.</li>
 * <li>The 256-bit key comes from configuration (an environment variable in production). The
 * service won't start without a valid one.</li>
 * </ul>
 *
 * Stored form: {@code v1:} + base64(iv + ciphertext + tag).
 */
@Service
public class CryptoService {

	private static final String PREFIX = "v1:";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public CryptoService(@Value("${naukriradar.security.encryption-key:}") String base64Key) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64Key.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("naukriradar.security.encryption-key is not valid base64", ex);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("naukriradar.security.encryption-key must be 32 bytes (256 bits), base64-encoded. "
					+ "Set NAUKRIRADAR_SECURITY_ENCRYPTION_KEY.");
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	public String encrypt(String plaintext, String associatedData) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	/**
	 * @throws IllegalStateException if the value was tampered with, encrypted for another
	 * setting, or under a different key
	 */
	public String decrypt(String stored, String associatedData) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			throw new IllegalStateException("Not an encrypted value");
		}
		try {
			byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			if (all.length <= IV_BYTES) {
				throw new IllegalStateException("Encrypted value is too short");
			}
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
			byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("Could not decrypt the value; it was changed or encrypted with another key", ex);
		}
	}

}
