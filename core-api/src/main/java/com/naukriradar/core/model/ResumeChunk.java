package com.naukriradar.core.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A few paragraphs of a user's resume: the unit RAG retrieves, so a prompt gets the two or
 * three parts of the resume that fit the question instead of the whole document. Rebuilt
 * when the resume file changes (the file hash tells).
 */
@Entity
@Table(name = "resume_chunks",
		uniqueConstraints = @UniqueConstraint(name = "uk_resume_chunks_user_position", columnNames = { "user_id", "position" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeChunk {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36)
	private String userId;

	/** SHA-256 of the resume file these chunks were cut from. */
	@Column(name = "resume_sha256", nullable = false, length = 64)
	private String resumeSha256;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ResumeSection section;

	/** Order within the resume, from 0. */
	@Column(nullable = false)
	private int position;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public ResumeChunk(String userId, String resumeSha256, ResumeSection section, int position, String text,
			Instant createdAt) {
		this.userId = userId;
		this.resumeSha256 = resumeSha256;
		this.section = section;
		this.position = position;
		this.text = text;
		this.createdAt = createdAt;
	}

}
