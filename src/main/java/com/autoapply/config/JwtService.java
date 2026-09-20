package com.autoapply.config;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final SettingsService settings;

    public String generateToken(String email, String userId, String role) {
        long minutes = settings.getLong(SettingKeys.SECURITY_JWT_EXPIRY_MINUTES, 1440);
        Date now = new Date();
        return Jwts.builder()
                .setClaims(Map.of("uid", userId, "role", role))
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + minutes * 60_000L))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public String extractUserId(String token) {
        Object uid = parse(token).get("uid");
        return uid == null ? null : uid.toString();
    }

    public String extractRole(String token) {
        Object role = parse(token).get("role");
        return role == null ? "USER" : role.toString();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
    }

    private Key getKey() {
        String secret = settings.getString(SettingKeys.SECURITY_JWT_SECRET,
                "change-me-to-a-long-random-string-at-least-32-chars");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 needs at least 256 bits; pad deterministically rather than failing at runtime
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) padded[i] = (byte) ('x' + (i % 7));
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
