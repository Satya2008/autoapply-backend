package com.autoapply.settings;

import com.autoapply.common.AppException;
import com.autoapply.common.CryptoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for runtime configuration.
 *
 * Values live in the database, are cached in memory for fast reads, and any write
 * refreshes the cache immediately - so changing a key in the admin dashboard takes
 * effect on the very next call without a restart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final AppSettingRepository repository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void reload() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        for (AppSetting setting : repository.findAll()) {
            String raw = setting.getValue() != null ? setting.getValue() : setting.getDefaultValue();
            if (raw == null) continue;
            fresh.put(setting.getKey(), Boolean.TRUE.equals(setting.getSecret()) ? cryptoService.decrypt(raw) : raw);
        }
        cache.clear();
        cache.putAll(fresh);
        log.info("Loaded {} runtime settings into cache", cache.size());
    }

    // ------------------------------------------------------------------ reads

    public String getString(String key, String fallback) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            SettingDefinition definition = SettingCatalog.find(key);
            if (definition != null && definition.defaultValue() != null && !definition.defaultValue().isBlank()) {
                return definition.defaultValue();
            }
            return fallback;
        }
        return value;
    }

    public String getString(String key) {
        return getString(key, "");
    }

    public int getInt(String key, int fallback) {
        try {
            String value = getString(key, null);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Setting {} is not a valid integer, using fallback {}", key, fallback);
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        try {
            String value = getString(key, null);
            return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback) {
        try {
            String value = getString(key, null);
            return value == null || value.isBlank() ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase()) {
            case "true", "yes", "1", "on", "enabled" -> true;
            case "false", "no", "0", "off", "disabled" -> false;
            default -> fallback;
        };
    }

    /** Splits a comma (or newline) separated setting into a trimmed, non-empty list. */
    public List<String> getList(String key) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public Map<String, String> getMap(String key) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("Setting {} is not valid JSON object: {}", key, e.getMessage());
            return Map.of();
        }
    }

    public <T> T getJson(String key, Class<T> type, T fallback) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) return fallback;
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("Setting {} is not valid JSON for {}: {}", key, type.getSimpleName(), e.getMessage());
            return fallback;
        }
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public AppSetting update(String key, String newValue, String updatedBy) {
        AppSetting setting = repository.findById(key)
                .orElseThrow(() -> AppException.notFound("Setting '" + key + "'"));

        if (Boolean.FALSE.equals(setting.getEditable())) {
            throw AppException.badRequest("Setting '" + key + "' is read-only");
        }
        validate(setting, newValue);

        setting.setValue(Boolean.TRUE.equals(setting.getSecret())
                ? cryptoService.encrypt(newValue)
                : newValue);
        setting.setUpdatedBy(updatedBy);
        repository.save(setting);

        cache.put(key, newValue == null ? "" : newValue);
        eventPublisher.publishEvent(new SettingChangedEvent(key, newValue, updatedBy));
        log.info("Setting '{}' updated by {}", key, updatedBy);
        return setting;
    }

    @Transactional
    public void updateAll(Map<String, String> values, String updatedBy) {
        values.forEach((key, value) -> update(key, value, updatedBy));
    }

    @Transactional
    public AppSetting resetToDefault(String key, String updatedBy) {
        AppSetting setting = repository.findById(key)
                .orElseThrow(() -> AppException.notFound("Setting '" + key + "'"));
        return update(key, setting.getDefaultValue(), updatedBy);
    }

    private void validate(AppSetting setting, String value) {
        if (value == null || value.isBlank()) return;
        SettingType type = setting.getType() == null ? SettingType.STRING : setting.getType();
        try {
            switch (type) {
                case INTEGER -> Integer.parseInt(value.trim());
                case DECIMAL -> Double.parseDouble(value.trim());
                case BOOLEAN -> {
                    String v = value.trim().toLowerCase();
                    if (!List.of("true", "false", "yes", "no", "1", "0", "on", "off").contains(v)) {
                        throw new IllegalArgumentException("expected true or false");
                    }
                }
                case JSON -> objectMapper.readTree(value);
                case URL -> {
                    if (!value.startsWith("http://") && !value.startsWith("https://")) {
                        throw new IllegalArgumentException("must start with http:// or https://");
                    }
                }
                case EMAIL -> {
                    if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                        throw new IllegalArgumentException("not a valid email address");
                    }
                }
                case CRON -> {
                    if (value.trim().split("\\s+").length < 6) {
                        throw new IllegalArgumentException("expected a 6-field Spring cron expression");
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            throw AppException.badRequest("Invalid value for '" + setting.getKey() + "': " + e.getMessage());
        }

        if (setting.getAllowedValues() != null && !setting.getAllowedValues().isBlank()) {
            List<String> allowed = Arrays.stream(setting.getAllowedValues().split(",")).map(String::trim).toList();
            if (!allowed.contains(value.trim())) {
                throw AppException.badRequest(
                        "Invalid value for '" + setting.getKey() + "'. Allowed: " + String.join(", ", allowed));
            }
        }
    }

    // ------------------------------------------------------------------ views

    public List<AppSetting> findAll() {
        return repository.findAllByOrderByCategoryAscDisplayOrderAscKeyAsc();
    }

    public List<String> categories() {
        return findAll().stream().map(AppSetting::getCategory).filter(Objects::nonNull).distinct().sorted().toList();
    }

    public String displayValue(AppSetting setting) {
        String raw = setting.getValue() != null ? setting.getValue() : setting.getDefaultValue();
        if (Boolean.TRUE.equals(setting.getSecret())) {
            return cryptoService.mask(cryptoService.decrypt(raw));
        }
        return raw;
    }

    public boolean isConfigured(String key) {
        String value = getString(key, null);
        return value != null && !value.isBlank() && !value.startsWith("YOUR_") && !value.startsWith("CHANGE_ME");
    }

    public record SettingChangedEvent(String key, String value, String updatedBy) {
    }
}
