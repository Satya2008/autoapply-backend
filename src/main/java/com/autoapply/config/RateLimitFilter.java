package com.autoapply.config;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window limiter keyed by client IP. Limits come from settings, so they can be
 * tightened during an incident without redeploying.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final SettingsService settings;
    private final ObjectMapper objectMapper;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!settings.getBoolean(SettingKeys.RATELIMIT_ENABLED, true)) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean authEndpoint = path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register");
        int limit = authEndpoint
                ? settings.getInt(SettingKeys.RATELIMIT_AUTH_PER_MINUTE, 10)
                : settings.getInt(SettingKeys.RATELIMIT_REQUESTS_PER_MINUTE, 120);

        String key = clientKey(request) + (authEndpoint ? ":auth" : ":api");
        long minute = Instant.now().getEpochSecond() / 60;

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != minute) return new Window(minute);
            return existing;
        });

        int used = window.count.incrementAndGet();
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().minute < minute - 2);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - used)));

        if (used > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "errorCode", "RATE_LIMITED",
                    "message", "Too many requests. Please slow down and try again in a minute."));
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
