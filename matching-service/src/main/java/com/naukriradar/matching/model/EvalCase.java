package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One example of the golden set: a candidate, a job, and the score a careful recruiter would
 * give. The profile and the job are stored in the case itself, not referenced, so the set
 * never changes under an eval when real jobs expire or users edit their profiles.
 */
@Entity
@Table(name = "eval_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalCase {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@Column(name = "profile_json", nullable = false, columnDefinition = "TEXT")
	private String profileJson;

	@Column(name = "job_json", nullable = false, columnDefinition = "MEDIUMTEXT")
	private String jobJson;

	@Column(name = "expected_score", nullable = false)
	private int expectedScore;

	@Column(length = 500)
	private String notes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public EvalCase(String name, String profileJson, String jobJson, int expectedScore, String notes, Instant createdAt) {
		this.name = name;
		this.profileJson = profileJson;
		this.jobJson = jobJson;
		this.expectedScore = expectedScore;
		this.notes = notes;
		this.createdAt = createdAt;
	}

}
