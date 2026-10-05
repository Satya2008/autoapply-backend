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

/**
 * A text embedded by a paid model, kept so the same text (a resume chunk, a question) is
 * never paid for twice. Keyed by model and the text's hash, not the text itself. Written with
 * plain JDBC ({@code EmbeddingStore}).
 */
@Entity
@Table(name = "embedding_cache",
		uniqueConstraints = @UniqueConstraint(name = "uk_embedding_cache_model_hash", columnNames = { "model_key", "text_hash" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmbeddingCacheEntry {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "model_key", nullable = false, length = 120)
	private String modelKey;

	@Column(name = "text_hash", nullable = false, length = 64)
	private String textHash;

	@Column(nullable = false)
	private int dimensions;

	@Column(nullable = false, columnDefinition = "MEDIUMBLOB")
	private byte[] vector;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

}
