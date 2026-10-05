package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A job's text as a vector, from one embedding model. A job has one row per model, so
 * switching models (or falling back to the local one) never mixes vectors from two models.
 * Written with plain JDBC upserts ({@code EmbeddingStore}); the entity defines the table.
 */
@Entity
@Table(name = "job_embeddings",
		uniqueConstraints = @UniqueConstraint(name = "uk_job_embeddings_job_model", columnNames = { "job_id", "model_key" }),
		indexes = @Index(name = "ix_job_embeddings_model_updated", columnList = "model_key, updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEmbedding {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "job_id", nullable = false, length = 36)
	private String jobId;

	/** provider type and model, e.g. "openai:text-embedding-3-small" or "local:hashing-v1" */
	@Column(name = "model_key", nullable = false, length = 120)
	private String modelKey;

	/** SHA-256 of the embedded text: a changed posting gets embedded again. */
	@Column(name = "text_hash", nullable = false, length = 64)
	private String textHash;

	@Column(nullable = false)
	private int dimensions;

	/** float32 little endian, unit length */
	@Column(nullable = false, columnDefinition = "MEDIUMBLOB")
	private byte[] vector;

	@Column(name = "posted_at")
	private Instant postedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

}
