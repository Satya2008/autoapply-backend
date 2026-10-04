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

/** One admin action: who did what to which thing, from where, and whether it worked. Never updated. */
@Entity
@Table(name = "audit_log", indexes = {
		@Index(name = "idx_audit_actor", columnList = "actor, id"),
		@Index(name = "idx_audit_action", columnList = "action, id") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 100, updatable = false)
	private String actor;

	@Column(nullable = false, length = 60, updatable = false)
	private String action;

	@Column(length = 40, updatable = false)
	private String targetType;

	@Column(length = 200, updatable = false)
	private String targetId;

	@Column(length = 1000, updatable = false)
	private String detail;

	@Column(length = 45, updatable = false)
	private String ip;

	@Column(length = 300, updatable = false)
	private String userAgent;

	@Column(updatable = false)
	private boolean success;

	@Column(length = 500, updatable = false)
	private String error;

	@Column(nullable = false, updatable = false)
	private Instant at;

	public AuditEntry(String actor, String action, String targetType, String targetId, String detail, String ip,
			String userAgent, boolean success, String error, Instant at) {
		this.actor = cut(actor, 100);
		this.action = cut(action, 60);
		this.targetType = cut(targetType, 40);
		this.targetId = cut(targetId, 200);
		this.detail = cut(detail, 1000);
		this.ip = cut(ip, 45);
		this.userAgent = cut(userAgent, 300);
		this.success = success;
		this.error = cut(error, 500);
		this.at = at;
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
