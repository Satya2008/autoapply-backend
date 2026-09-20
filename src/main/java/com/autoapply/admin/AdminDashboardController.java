package com.autoapply.admin;

import com.autoapply.analytics.AnalyticsService;
import com.autoapply.audit.AuditLog;
import com.autoapply.audit.AuditLogRepository;
import com.autoapply.common.ApiResponse;
import com.autoapply.matching.ai.AiService;
import com.autoapply.notification.NotificationService;
import com.autoapply.scheduler.DynamicSchedulerService;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Dashboard")
public class AdminDashboardController {

    private final AnalyticsService analyticsService;
    private final AuditLogRepository auditLogRepository;
    private final DynamicSchedulerService schedulerService;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final SettingsService settings;
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/overview")
    @Operation(summary = "Headline counters for the dashboard")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(analyticsService.overview());
    }

    @GetMapping("/analytics/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(analyticsService.applicationsTimeline(Math.min(365, Math.max(1, days))));
    }

    @GetMapping("/analytics/status")
    public ApiResponse<List<Map<String, Object>>> byStatus() {
        return ApiResponse.ok(analyticsService.applicationsByStatus());
    }

    @GetMapping("/analytics/sources")
    public ApiResponse<List<Map<String, Object>>> bySource() {
        return ApiResponse.ok(analyticsService.jobsBySource());
    }

    @GetMapping("/activity")
    @Operation(summary = "The twenty most recent audit entries")
    public ApiResponse<List<AuditLog>> activity() {
        return ApiResponse.ok(auditLogRepository.findTop20ByOrderByCreatedAtDesc());
    }

    @GetMapping("/health")
    @Operation(summary = "Live status of every dependency and provider")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();

        Map<String, Object> database = new LinkedHashMap<>();
        try (var connection = dataSource.getConnection()) {
            database.put("up", connection.isValid(3));
            database.put("product", connection.getMetaData().getDatabaseProductName());
            database.put("url", connection.getMetaData().getURL());
        } catch (Exception e) {
            database.put("up", false);
            database.put("error", e.getMessage());
        }
        health.put("database", database);

        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            redis.put("up", "PONG".equalsIgnoreCase(
                    redisTemplate.getConnectionFactory().getConnection().ping()));
        } catch (Exception e) {
            redis.put("up", false);
            redis.put("error", e.getMessage());
        }
        health.put("redis", redis);

        health.put("aiProviders", aiService.providerStatus());
        health.put("aiActive", aiService.isEnabled());
        health.put("notificationChannels", notificationService.channelStatus());
        health.put("scheduler", schedulerService.status());
        health.put("maintenanceMode", settings.getBoolean(SettingKeys.APP_MAINTENANCE_MODE, false));
        health.put("applyMode", settings.getString(SettingKeys.APPLY_MODE, "SIMULATE"));
        health.put("browserAutomation", settings.getBoolean(SettingKeys.SELENIUM_ENABLED, false));

        Runtime runtime = Runtime.getRuntime();
        health.put("jvm", Map.of(
                "usedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
                "maxMb", runtime.maxMemory() / 1024 / 1024,
                "processors", runtime.availableProcessors()));

        return ApiResponse.ok(health);
    }
}
