package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.naukriradar.common.crypto.CryptoService;
import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.audit.Audited;
import com.naukriradar.core.dto.response.SettingResponse;
import com.naukriradar.core.model.Setting;
import com.naukriradar.core.repository.SettingRepository;
import com.naukriradar.core.settings.SettingChangedEvent;
import com.naukriradar.core.settings.SettingDefinition;
import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.settings.SettingValues;
import com.naukriradar.core.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runtime settings: a database row when someone changed the value, the definition's default
 * otherwise. Reads go through a Caffeine cache, so hot paths don't query the database on
 * every call.
 *
 * <p>The cache entry is dropped only after the change commits. Dropping it earlier would let
 * a reader reload the old value before the commit and keep it cached. Other instances hear
 * about the change through Redis ({@link com.naukriradar.core.settings.SettingsSync}).
 * Entries also expire after five minutes, in case such a message is lost.
 */
@Service
public class SettingsService implements Settings {

	private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

	static final String MASK = "****";

	private final SettingRepository repository;
	private final CryptoService crypto;
	private final ApplicationEventPublisher events;
	private final Clock clock = Clock.systemUTC();

	/** Stored text per key (still encrypted for secrets); empty when not overridden. */
	private final Cache<String, Optional<String>> cache = Caffeine.newBuilder()
			.maximumSize(500)
			.expireAfterWrite(Duration.ofMinutes(5))
			.build();

	public SettingsService(SettingRepository repository, CryptoService crypto, ApplicationEventPublisher events) {
		this.repository = repository;
		this.crypto = crypto;
		this.events = events;
	}

	@Override
	public String getString(String key) {
		return effective(key);
	}

	@Override
	public int getInt(String key) {
		return read(key, SettingValues::asInt);
	}

	@Override
	public boolean getBoolean(String key) {
		return read(key, SettingValues::asBoolean);
	}

	@Override
	public Duration getDuration(String key) {
		return read(key, SettingValues::asDuration);
	}

	@Override
	public List<String> getDomains(String key) {
		return read(key, SettingValues::asDomains);
	}

	@Transactional(readOnly = true)
	public List<SettingResponse> list(String category) {
		return SettingDefinitions.all().stream()
				.filter(d -> category == null || category.isBlank() || d.category().equalsIgnoreCase(category.strip()))
				.map(d -> view(d, repository.findById(d.key())))
				.toList();
	}

	@Transactional(readOnly = true)
	public SettingResponse get(String key) {
		SettingDefinition definition = definition(key);
		return view(definition, repository.findById(key));
	}

	@Transactional
	@Audited(action = "SETTING_UPDATE", targetType = "setting", targetId = "#key",
			detail = "T(com.naukriradar.core.audit.AuditDetails).settingChange(#key, #value)")
	public SettingResponse update(String key, String value, String actor) {
		SettingDefinition definition = definition(key);
		String canonical;
		try {
			canonical = SettingValues.validate(definition, value);
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException(key + ": " + ex.getMessage());
		}
		String stored = definition.secret() ? crypto.encrypt(canonical, key) : canonical;
		Setting setting = repository.findById(key).orElseGet(() -> new Setting(key));
		setting.change(stored, actor, clock.instant());
		repository.save(setting);
		events.publishEvent(new SettingChangedEvent(key));
		return view(definition, Optional.of(setting));
	}

	@Transactional
	@Audited(action = "SETTING_RESET", targetType = "setting", targetId = "#key")
	public SettingResponse reset(String key, String actor) {
		SettingDefinition definition = definition(key);
		repository.findById(key).ifPresent(repository::delete);
		events.publishEvent(new SettingChangedEvent(key));
		return view(definition, Optional.empty());
	}

	/**
	 * Runs before other listeners, so they read the new value. A change from another
	 * instance arrives outside any transaction, hence {@code fallbackExecution}.
	 */
	@TransactionalEventListener(fallbackExecution = true)
	@Order(0)
	public void forget(SettingChangedEvent event) {
		cache.invalidate(event.key());
	}

	private <T> T read(String key, Function<String, T> parser) {
		String value = effective(key);
		try {
			return parser.apply(value);
		}
		catch (RuntimeException ex) {
			// only possible if someone edited the table by hand; keep the app working
			log.warn("Setting {} has an unreadable value; using its default", key);
			return parser.apply(definition(key).defaultValue());
		}
	}

	private String effective(String key) {
		SettingDefinition definition = definition(key);
		Optional<String> stored = cache.get(key, k -> repository.findById(k).map(Setting::getValue));
		if (stored.isEmpty()) {
			return definition.defaultValue();
		}
		if (definition.secret()) {
			try {
				return crypto.decrypt(stored.get(), key);
			}
			catch (IllegalStateException ex) {
				log.error("Secret setting {} can't be decrypted (wrong key or tampered); treating it as unset", key);
				return definition.defaultValue();
			}
		}
		return stored.get();
	}

	private SettingResponse view(SettingDefinition definition, Optional<Setting> stored) {
		String value;
		if (definition.secret()) {
			value = stored.isPresent() ? MASK : null;
		}
		else {
			value = stored.map(Setting::getValue).orElse(definition.defaultValue());
		}
		return new SettingResponse(definition.key(), definition.category(), definition.type(), definition.description(),
				value, definition.secret() ? null : definition.defaultValue(), stored.isPresent(),
				stored.map(Setting::getUpdatedBy).orElse(null), stored.map(Setting::getUpdatedAt).orElse(null));
	}

	private static SettingDefinition definition(String key) {
		return SettingDefinitions.find(key).orElseThrow(() -> new NotFoundException("No setting " + key + "."));
	}

}
