package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One version of a prompt. Versions are never edited: a change is a new version, and
 * switching back is activating the old one. Exactly one version per code is active.
 */
@Entity
@Table(name = "prompts", uniqueConstraints = @UniqueConstraint(name = "uk_prompts_code_version", columnNames = { "code", "version" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prompt {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 50)
	private String code;

	@Column(nullable = false)
	private int version;

	@Column(name = "system_text", columnDefinition = "TEXT")
	private String system;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String template;

	/** JSON Schema the answer must match; null for free text. */
	@Column(name = "output_schema", columnDefinition = "TEXT")
	private String outputSchema;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** When this version first went live; null if it never has. */
	@Column(name = "activated_at")
	private Instant activatedAt;

	public Prompt(String code, int version, String system, String template, String outputSchema, Instant createdAt) {
		this.code = code;
		this.version = version;
		this.system = system;
		this.template = template;
		this.outputSchema = outputSchema;
		this.createdAt = createdAt;
	}

	/** The first time a version goes live is remembered: going back to it later needs no new eval. */
	public void activate(Instant at) {
		this.active = true;
		if (activatedAt == null) {
			activatedAt = at;
		}
	}

	public void deactivate() {
		this.active = false;
	}

}
