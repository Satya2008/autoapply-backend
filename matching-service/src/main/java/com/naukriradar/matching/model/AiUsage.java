package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** One call to an AI provider that got an answer, whether or not the answer was usable. */
@Entity
@Table(name = "ai_usage", indexes = {
		@Index(name = "idx_ai_usage_user_at", columnList = "user_id, at"),
		@Index(name = "idx_ai_usage_at", columnList = "at") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsage {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", length = 36)
	private String userId;

	@Column(nullable = false, length = 50)
	private String purpose;

	@Column(nullable = false, length = 50)
	private String provider;

	@Column(nullable = false, length = 100)
	private String model;

	@Column(name = "tokens_in", nullable = false)
	private long tokensIn;

	@Column(name = "tokens_out", nullable = false)
	private long tokensOut;

	/** Millionths of a US dollar. */
	@Column(name = "cost_micros", nullable = false)
	private long costMicros;

	@Column(name = "latency_ms", nullable = false)
	private long latencyMs;

	/** False when the answer failed validation: the tokens were still paid for. */
	@Column(nullable = false)
	private boolean success;

	@Column(nullable = false)
	private Instant at;

	public AiUsage(String userId, String purpose, String provider, String model, long tokensIn, long tokensOut,
			long costMicros, long latencyMs, boolean success, Instant at) {
		this.userId = userId;
		this.purpose = purpose;
		this.provider = provider;
		this.model = model;
		this.tokensIn = tokensIn;
		this.tokensOut = tokensOut;
		this.costMicros = costMicros;
		this.latencyMs = latencyMs;
		this.success = success;
		this.at = at;
	}

}
