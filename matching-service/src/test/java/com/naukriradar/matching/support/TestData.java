package com.naukriradar.matching.support;

import java.time.Instant;
import java.util.List;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;

/** Builders with sensible defaults; tests change only what they're about. */
public final class TestData {

	public static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

	private TestData() {
	}

	public static ProfileBuilder profile() {
		return new ProfileBuilder();
	}

	public static JobBuilder job() {
		return new JobBuilder();
	}

	public static final class ProfileBuilder {

		private List<String> skills = List.of("java", "spring boot", "mysql");
		private List<String> roles = List.of("Backend Engineer");
		private List<String> locations = List.of("Pune");
		private boolean remoteOk = true;
		private Long expectedSalary = 1_200_000L;
		private Integer experienceYears = 3;
		private List<String> excludedCompanies = List.of();
		private List<String> excludedKeywords = List.of();

		public ProfileBuilder skills(String... skills) {
			this.skills = List.of(skills);
			return this;
		}

		public ProfileBuilder roles(String... roles) {
			this.roles = List.of(roles);
			return this;
		}

		public ProfileBuilder locations(String... locations) {
			this.locations = List.of(locations);
			return this;
		}

		public ProfileBuilder remoteOk(boolean remoteOk) {
			this.remoteOk = remoteOk;
			return this;
		}

		public ProfileBuilder expectedSalary(Long expectedSalary) {
			this.expectedSalary = expectedSalary;
			return this;
		}

		public ProfileBuilder experience(Integer years) {
			this.experienceYears = years;
			return this;
		}

		public ProfileBuilder excludeCompanies(String... companies) {
			this.excludedCompanies = List.of(companies);
			return this;
		}

		public ProfileBuilder excludeKeywords(String... keywords) {
			this.excludedKeywords = List.of(keywords);
			return this;
		}

		public MatchingProfile build() {
			return new MatchingProfile("user-1", skills, roles, locations, remoteOk, expectedSalary, experienceYears,
					excludedCompanies, excludedKeywords, 50);
		}

	}

	public static final class JobBuilder {

		private String id = "job-1";
		private String title = "Backend Engineer";
		private String company = "Acme";
		private String location = "Pune";
		private boolean remote;
		private Long salaryMin;
		private Long salaryMax;
		private String currency;
		private Instant postedAt = NOW.minusSeconds(3600);
		private String description = "We use Java, Spring Boot and MySQL. 2-4 years of experience.";

		public JobBuilder id(String id) {
			this.id = id;
			return this;
		}

		public JobBuilder title(String title) {
			this.title = title;
			return this;
		}

		public JobBuilder company(String company) {
			this.company = company;
			return this;
		}

		public JobBuilder location(String location) {
			this.location = location;
			return this;
		}

		public JobBuilder remote(boolean remote) {
			this.remote = remote;
			return this;
		}

		public JobBuilder salary(Long min, Long max, String currency) {
			this.salaryMin = min;
			this.salaryMax = max;
			this.currency = currency;
			return this;
		}

		public JobBuilder postedAt(Instant postedAt) {
			this.postedAt = postedAt;
			return this;
		}

		public JobBuilder description(String description) {
			this.description = description;
			return this;
		}

		public CandidateJob build() {
			return new CandidateJob(id, title, company, location, remote, salaryMin, salaryMax, currency, postedAt,
					"https://jobs.example.com/" + id, description);
		}

	}

}
