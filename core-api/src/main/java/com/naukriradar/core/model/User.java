package com.naukriradar.core.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	/** BCrypt hash; null for accounts made before sign-in existed, until their owner sets one. */
	@Column(length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private UserRole role;

	/** Set for a password someone else chose (first admin, a reset): the user picks their own. */
	private boolean mustChangePassword;

	/** Wrong passwords in a row; five lock sign-in for a while. */
	private int failedLogins;

	private Instant lockedUntil;

	private Instant lastLoginAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public User(String email) {
		this.email = Objects.requireNonNull(email, "email");
		this.status = UserStatus.ACTIVE;
		this.role = UserRole.USER;
	}

	public UserRole getRole() {
		return role == null ? UserRole.USER : role;
	}

	public boolean hasPassword() {
		return passwordHash != null;
	}

	public void password(String hash, boolean mustChange) {
		this.passwordHash = hash;
		this.mustChangePassword = mustChange;
		this.failedLogins = 0;
		this.lockedUntil = null;
	}

	public void role(UserRole role) {
		this.role = Objects.requireNonNull(role);
	}

	public void status(UserStatus status) {
		this.status = Objects.requireNonNull(status);
	}

	public boolean lockedAt(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/** A wrong password; the {@code limit}th in a row locks sign-in until {@code lockUntil}. */
	public void failedLogin(int limit, Instant lockUntil) {
		failedLogins++;
		if (failedLogins >= limit) {
			lockedUntil = lockUntil;
			failedLogins = 0;
		}
	}

	public void loggedIn(Instant at) {
		failedLogins = 0;
		lockedUntil = null;
		lastLoginAt = at;
	}

}
