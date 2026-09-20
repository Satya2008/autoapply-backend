package com.autoapply.settings;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Settings", description = "Runtime configuration. Everything the product does is driven from here.")
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List every setting grouped by category")
    public ApiResponse<Map<String, List<SettingView>>> list() {
        Map<String, List<SettingView>> grouped = settingsService.findAll().stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(
                        SettingView::getCategory, LinkedHashMap::new, Collectors.toList()));
        return ApiResponse.ok(grouped);
    }

    @GetMapping("/flat")
    @Operation(summary = "List every setting as a flat array")
    public ApiResponse<List<SettingView>> flat() {
        return ApiResponse.ok(settingsService.findAll().stream().map(this::toView).toList());
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(settingsService.categories());
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update one setting - takes effect immediately, no restart")
    public ApiResponse<SettingView> update(@PathVariable String key,
                                           @RequestBody UpdateRequest request,
                                           @AuthenticationPrincipal UserDetails principal) {
        AppSetting updated = settingsService.update(key, request.getValue(), principal.getUsername());
        auditService.record(principal.getUsername(), AuditAction.SETTING_UPDATED, "setting", key,
                Boolean.TRUE.equals(updated.getSecret()) ? "***" : request.getValue());
        return ApiResponse.ok(toView(updated), "Setting updated");
    }

    @PutMapping
    @Operation(summary = "Update many settings at once")
    public ApiResponse<Void> updateBulk(@RequestBody Map<String, String> values,
                                        @AuthenticationPrincipal UserDetails principal) {
        settingsService.updateAll(values, principal.getUsername());
        auditService.record(principal.getUsername(), AuditAction.SETTING_UPDATED, "setting",
                String.join(",", values.keySet()), values.size() + " settings updated");
        return ApiResponse.message(values.size() + " settings updated");
    }

    @PostMapping("/{key}/reset")
    @Operation(summary = "Reset one setting back to its shipped default")
    public ApiResponse<SettingView> reset(@PathVariable String key,
                                          @AuthenticationPrincipal UserDetails principal) {
        AppSetting reset = settingsService.resetToDefault(key, principal.getUsername());
        auditService.record(principal.getUsername(), AuditAction.SETTING_RESET, "setting", key, null);
        return ApiResponse.ok(toView(reset), "Setting reset to default");
    }

    @PostMapping("/reload")
    @Operation(summary = "Force a cache reload from the database")
    public ApiResponse<Void> reload() {
        settingsService.reload();
        return ApiResponse.message("Settings cache reloaded");
    }

    private SettingView toView(AppSetting setting) {
        SettingView view = new SettingView();
        view.setKey(setting.getKey());
        view.setValue(settingsService.displayValue(setting));
        view.setDefaultValue(Boolean.TRUE.equals(setting.getSecret()) ? "" : setting.getDefaultValue());
        view.setType(setting.getType() == null ? SettingType.STRING : setting.getType());
        view.setCategory(setting.getCategory() == null ? "General" : setting.getCategory());
        view.setDescription(setting.getDescription());
        view.setAllowedValues(setting.getAllowedValues());
        view.setSecret(Boolean.TRUE.equals(setting.getSecret()));
        view.setEditable(!Boolean.FALSE.equals(setting.getEditable()));
        view.setConfigured(settingsService.isConfigured(setting.getKey()));
        view.setUpdatedBy(setting.getUpdatedBy());
        view.setUpdatedAt(setting.getUpdatedAt() == null ? null : setting.getUpdatedAt().toString());
        return view;
    }

    @Data
    public static class UpdateRequest {
        private String value;
    }

    @Data
    public static class SettingView {
        private String key;
        private String value;
        private String defaultValue;
        private SettingType type;
        private String category;
        private String description;
        private String allowedValues;
        private boolean secret;
        private boolean editable;
        private boolean configured;
        private String updatedBy;
        private String updatedAt;
    }
}
