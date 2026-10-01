package com.naukriradar.user;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

	@Id
	private UUID userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(length = 100)
	private String fullName;

	@Column(length = 20)
	private String phone;

	@Column(length = 100)
	private String location;

	@Column(length = 100)
	private String currentTitle;

	private Integer experienceYears;

	/** Expected yearly salary in INR. */
	private Long expectedSalary;

	private Integer noticePeriodDays;

	@Column(length = 255)
	private String linkedinUrl;

	@Column(length = 255)
	private String githubUrl;

	@Column(length = 255)
	private String portfolioUrl;

	private boolean remoteOk;

	private int minMatchScore = 50;

	private int dailyApplyLimit = 10;

	private boolean autoApplyEnabled = false;

	@ElementCollection
	@CollectionTable(name = "profile_target_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role", nullable = false, length = 100)
	private Set<String> targetRoles = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "profile_locations", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "location", nullable = false, length = 100)
	private Set<String> preferredLocations = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "profile_excluded_companies", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "company", nullable = false, length = 100)
	private Set<String> excludedCompanies = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "profile_excluded_keywords", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "keyword", nullable = false, length = 100)
	private Set<String> excludedKeywords = new HashSet<>();

	/** Keyed by normalised skill name, which makes (user_id, skill) the table's primary key. */
	@ElementCollection
	@CollectionTable(name = "profile_skills", joinColumns = @JoinColumn(name = "user_id"))
	@MapKeyColumn(name = "skill", length = 50)
	private Map<String, ProfileSkill> skills = new HashMap<>();

	@UpdateTimestamp
	private Instant updatedAt;

	@Version
	private long version;

	public Profile(User user) {
		this.user = user;
	}

}
