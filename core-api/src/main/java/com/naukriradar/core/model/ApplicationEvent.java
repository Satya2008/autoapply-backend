package com.naukriradar.core.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One status change of an application. Append-only: together they are its timeline. */
@Entity
@Table(name = "application_events", indexes = @Index(name = "idx_application_events_app", columnList = "application_id, at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "application_id", nullable = false, length = 36, updatable = false)
	private String applicationId;

	/** Null for the event that created the application. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 12, updatable = false)
	private ApplicationStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 12, updatable = false)
	private ApplicationStatus toStatus;

	@Column(length = 500, updatable = false)
	private String note;

	@Column(nullable = false, updatable = false)
	private Instant at;

	public ApplicationEvent(String applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus, String note,
			Instant at) {
		this.applicationId = applicationId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.note = note == null || note.length() <= 500 ? note : note.substring(0, 497) + "...";
		this.at = at;
	}

}
