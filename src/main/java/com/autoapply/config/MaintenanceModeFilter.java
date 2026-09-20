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
import java.util.Map;

/**
 * When maintenance mode is switched on in the dashboard, everything except the admin API,
 * auth and health checks returns 503 with the configured message.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final SettingsService settings;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!settings.getBoolean(SettingKeys.APP_MAINTENANCE_MODE, false) || isAlwaysAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (isAdminRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false,
                "errorCode", "MAINTENANCE",
                "message", settings.getString(SettingKeys.APP_MAINTENANCE_MESSAGE,
                        "We are performing scheduled maintenance. Please try again shortly.")));
    }

    private boolean isAlwaysAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/admin")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        try {
            String token = header.substring(7);
            return jwtService.isValid(token) && jwtService.extractRole(token).contains("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }
}
