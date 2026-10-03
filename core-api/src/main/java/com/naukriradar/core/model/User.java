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

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public User(String email) {
		this.email = Objects.requireNonNull(email, "email");
		this.status = UserStatus.ACTIVE;
	}

}
