package com.naukriradar.matching.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * An AI account an admin added: which API, where, with which key and model. Providers are
 * tried in priority order (lowest first) until one answers; the first is the "primary".
 */
@Entity
@Table(name = "ai_providers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProvider {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AiProviderType type;

	@Column(name = "base_url", nullable = false, length = 255)
	private String baseUrl;

	/** Encrypted with the provider name as associated data; never returned by the API. */
	@Column(name = "api_key", columnDefinition = "TEXT")
	private String apiKey;

	/** Last four characters, so an admin can tell which key is set. */
	@Column(name = "api_key_hint", length = 10)
	private String apiKeyHint;

	/** The everyday model: parsing, scoring. */
	@Column(nullable = false, length = 100)
	private String model;

	/** Used for writing (cover letters) when set; else {@link #model}. */
	@Column(name = "strong_model", length = 100)
	private String strongModel;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private int priority;

	@Column(name = "timeout_seconds", nullable = false)
	private int timeoutSeconds;

	/** USD per million tokens; null means "use the configured price list". */
	@Column(name = "input_price", precision = 10, scale = 4)
	private BigDecimal inputPrice;

	@Column(name = "output_price", precision = 10, scale = 4)
	private BigDecimal outputPrice;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", length = 64)
	private String updatedBy;

	public AiProvider(String name, AiProviderType type, int priority) {
		this.name = name;
		this.type = type;
		this.priority = priority;
		this.baseUrl = type.defaultBaseUrl();
		this.timeoutSeconds = 60;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setApiKey(String encrypted, String hint) {
		this.apiKey = encrypted;
		this.apiKeyHint = hint;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setStrongModel(String strongModel) {
		this.strongModel = strongModel;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public void setPrices(BigDecimal inputPrice, BigDecimal outputPrice) {
		this.inputPrice = inputPrice;
		this.outputPrice = outputPrice;
	}

	public void touch(String actor, Instant at) {
		this.updatedBy = actor;
		this.updatedAt = at;
	}

	/** Has what it needs to be called: a model, and a key unless the type needs none. */
	public boolean isReady() {
		return enabled && model != null && !model.isBlank() && (!type.needsApiKey() || apiKey != null);
	}

}
