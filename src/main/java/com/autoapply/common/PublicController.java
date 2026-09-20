package com.autoapply.common;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unauthenticated endpoints the frontend reads before anyone signs in, so branding and
 * signup availability are driven from settings rather than a frontend rebuild.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicController {

    private final SettingsService settings;

    @GetMapping("/config")
    @Operation(summary = "Branding and feature flags needed by the login screen")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("appName", settings.getString(SettingKeys.APP_NAME, "AutoApply AI"));
        config.put("registrationEnabled", settings.getBoolean(SettingKeys.APP_REGISTRATION_ENABLED, true));
        config.put("maintenanceMode", settings.getBoolean(SettingKeys.APP_MAINTENANCE_MODE, false));
        config.put("maintenanceMessage", settings.getString(SettingKeys.APP_MAINTENANCE_MESSAGE, ""));
        config.put("supportEmail", settings.getString(SettingKeys.APP_SUPPORT_EMAIL, ""));
        config.put("passwordMinLength", settings.getInt(SettingKeys.SECURITY_PASSWORD_MIN_LENGTH, 8));
        config.put("passwordRequiresSpecial", settings.getBoolean(SettingKeys.SECURITY_PASSWORD_REQUIRE_SPECIAL, false));
        return ApiResponse.ok(config);
    }
}
