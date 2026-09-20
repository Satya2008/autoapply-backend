package com.autoapply.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts secret setting values (API keys, SMTP passwords) at rest.
 * The master key comes from an env var so secrets in the DB are useless on their own.
 */
@Service
@Slf4j
public class CryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    @Value("${app.encryption.key:}")
    private String configuredKey;

    private SecretKey key;

    @PostConstruct
    void init() throws Exception {
        String source = (configuredKey == null || configuredKey.isBlank())
                ? "autoapply-default-development-key-change-me"
                : configuredKey;
        if (source.startsWith("autoapply-default")) {
            log.warn("APP_ENCRYPTION_KEY is not set - using the built-in development key. Set it before production use.");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(digest, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        if (isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw AppException.internal("Unable to encrypt value: " + e.getMessage());
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) return stored;
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Unable to decrypt a stored secret - was the encryption key changed?");
            return "";
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String mask(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        if (plain.length() <= 8) return "*".repeat(plain.length());
        return plain.substring(0, 4) + "*".repeat(Math.min(12, plain.length() - 8)) + plain.substring(plain.length() - 4);
    }
}
