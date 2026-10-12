package com.naukriradar.core.model;

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

/**
 * A signed-in browser. Only the token's SHA-256 is kept: a leaked table can't sign anyone in.
 */
@Entity
@Table(name = "sessions", indexes = @Index(name = "idx_sessions_user", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false, length = 36)
	private String userId;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant revokedAt;

	public Session(String tokenHash, String userId, Instant createdAt, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean activeAt(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke(Instant at) {
		if (revokedAt == null) {
			revokedAt = at;
		}
	}

}
