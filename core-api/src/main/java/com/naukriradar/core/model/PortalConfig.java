package com.naukriradar.core.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * An apply site an admin has described: its risk band (overriding the built-in lists) and,
 * from Phase 15, the CSS selectors the browser engine fills in.
 */
@Entity
@Table(name = "portal_configs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortalConfig {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	/** Lower case, no scheme or path, e.g. "careers.acme.com". Also covers its subdomains. */
	@Column(nullable = false, unique = true, length = 200)
	private String domain;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RiskBand riskBand;

	private boolean enabled;

	@ElementCollection
	@CollectionTable(name = "portal_config_selectors", joinColumns = @JoinColumn(name = "portal_id"))
	@MapKeyColumn(name = "field", length = 50)
	@Column(name = "css_selector", nullable = false, length = 500)
	private Map<String, String> selectors = new LinkedHashMap<>();

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public PortalConfig(String domain) {
		this.domain = domain;
	}

}
