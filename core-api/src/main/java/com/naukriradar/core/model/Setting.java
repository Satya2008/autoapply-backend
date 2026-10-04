package com.naukriradar.core.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A setting someone has changed. Settings left at their default have no row; resetting one
 * deletes its row. Secret values are stored encrypted.
 */
@Entity
@Table(name = "settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {

	/** "key" is a reserved word in MySQL, hence the column name. */
	@Id
	@Column(name = "setting_key", length = 100)
	private String key;

	@Column(name = "setting_value", nullable = false, columnDefinition = "text")
	private String value;

	@Column(length = 100)
	private String updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public Setting(String key) {
		this.key = key;
	}

	public void change(String value, String updatedBy, Instant at) {
		this.value = value;
		this.updatedBy = updatedBy;
		this.updatedAt = at;
	}

}
