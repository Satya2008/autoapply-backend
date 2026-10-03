package com.naukriradar.core.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The user's current resume. One per user: a new upload replaces the old one in place.
 * The file itself lives in {@code FileStorage} under {@link #storageKey}.
 */
@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private DocumentType documentType;

	@Column(nullable = false)
	private long sizeBytes;

	/** Hex SHA-256 of the file, to spot re-uploads of the same document. */
	@Column(nullable = false, length = 64)
	private String sha256;

	@Column(nullable = false, length = 500)
	private String storageKey;

	@Column(columnDefinition = "mediumtext")
	private String extractedText;

	@Column(nullable = false)
	private Instant uploadedAt;

	@Version
	private long version;

	public Resume(User user) {
		this.user = user;
	}

	public void replaceFile(String fileName, DocumentType documentType, long sizeBytes, String sha256,
			String storageKey, String extractedText, Instant uploadedAt) {
		this.fileName = fileName;
		this.documentType = documentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storageKey = storageKey;
		this.extractedText = extractedText;
		this.uploadedAt = uploadedAt;
	}

	public boolean hasText() {
		return extractedText != null && !extractedText.isBlank();
	}

}
