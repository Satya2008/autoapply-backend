package com.autoapply.settings;

import com.autoapply.common.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Inserts any catalogue entry that is missing from the database, and keeps the stored
 * metadata (description, type, category) in sync with the code. User-set values are
 * never overwritten.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class SettingsSeeder {

    private final AppSettingRepository repository;
    private final SettingsService settingsService;
    private final CryptoService cryptoService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        List<AppSetting> toSave = new ArrayList<>();
        int created = 0;
        int refreshed = 0;

        for (SettingDefinition definition : SettingCatalog.all()) {
            AppSetting existing = repository.findById(definition.key()).orElse(null);
            if (existing == null) {
                AppSetting entity = definition.toEntity();
                if (definition.secret() && definition.defaultValue() != null && !definition.defaultValue().isBlank()) {
                    entity.setDefaultValue(cryptoService.encrypt(definition.defaultValue()));
                }
                toSave.add(entity);
                created++;
            } else {
                // keep metadata aligned with the catalogue without touching the value
                existing.setType(definition.type());
                existing.setCategory(definition.category());
                existing.setDescription(definition.description());
                existing.setAllowedValues(definition.allowedValues());
                existing.setSecret(definition.secret());
                existing.setDisplayOrder(definition.displayOrder());
                if (!definition.secret()) {
                    existing.setDefaultValue(definition.defaultValue());
                }
                toSave.add(existing);
                refreshed++;
            }
        }

        repository.saveAll(toSave);
        settingsService.reload();
        log.info("Settings catalogue synced: {} created, {} refreshed, {} total", created, refreshed, toSave.size());
    }
}
