package com.naukriradar.matching.scoring;

import java.util.List;

import com.naukriradar.matching.client.MatchingProfile;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

/** The jobs here are real titles that used to reach a 3-year Java developer in India. */
class JobRulesTest {

	private final ExclusionFilter javaInIndiaJuniorMid = new ExclusionFilter(profile().experience(3)
			.filters(new MatchingProfile.Filters(List.of("Java", "Spring Boot"), "INDIA", "JUNIOR_MID")).build());

	@Test
	void keepsAJavaJobInAnIndianCityOrRemote() {
		assertThat(javaInIndiaJuniorMid.reason(job().title("Software Engineer - Java").location("Bengaluru, Karnataka, India").build())).isNull();
		assertThat(javaInIndiaJuniorMid.reason(job().title("Backend Developer (Java)").location("Remote").remote(true).build())).isNull();
		assertThat(javaInIndiaJuniorMid.reason(job().title("Java Developer").location("Gurgaon / Remote").build())).isNull();
		assertThat(javaInIndiaJuniorMid.reason(job().title("SDE 2 Backend").location("Pune")
				.description("Build services in Java. Java 17, Spring Boot, Kafka.").build())).isNull();
	}

	@Test
	void dropsJobsOutsideIndiaEvenWhenRemote() {
		assertThat(javaInIndiaJuniorMid.reason(job().title("Software Engineer - Java (w/m/d)").location("Homeoffice").remote(true).build()))
				.isEqualTo("not in India");
		assertThat(javaInIndiaJuniorMid.reason(job().title("Java Developer").location("Bern, Switzerland").build()))
				.isEqualTo("not in India");
		assertThat(javaInIndiaJuniorMid.reason(job().title("Java Developer").location("Remote - US").remote(true).build()))
				.isEqualTo("not in India");
	}

	@Test
	void dropsJobsNotAboutJava() {
		assertThat(javaInIndiaJuniorMid.reason(job().title("Site Reliability Engineer").location("Pune")
				.description("Python, Terraform, Kubernetes. Some Java services.").build()))
				.isEqualTo("not about java or spring boot");
		// JavaScript is not Java
		assertThat(javaInIndiaJuniorMid.reason(job().title("Frontend Engineer").location("Pune")
				.description("JavaScript, React. JavaScript everywhere.").build()))
				.isEqualTo("not about java or spring boot");
	}

	@Test
	void dropsRolesAboveTheChosenLevel() {
		assertThat(javaInIndiaJuniorMid.reason(job().title("Senior Java Engineer").location("Pune").build())).isEqualTo("too senior (senior)");
		assertThat(javaInIndiaJuniorMid.reason(job().title("Staff Software Development Engineer - Java").location("Pune").build()))
				.isEqualTo("too senior (staff)");
		assertThat(javaInIndiaJuniorMid.reason(job().title("Software Engineer III - Java").location("Pune").build()))
				.isEqualTo("too senior (level 3 or above)");
		assertThat(javaInIndiaJuniorMid.reason(job().title("Sr. Java Developer").location("Pune").build())).isEqualTo("too senior (senior)");
		// open at either level
		assertThat(javaInIndiaJuniorMid.reason(job().title("Senior/Software Engineer - Java").location("Pune, IN").build())).isNull();
		assertThat(javaInIndiaJuniorMid.reason(job().title("(Senior) Java Developer").location("Pune").build())).isNull();
		assertThat(javaInIndiaJuniorMid.reason(job().title("Principal/Senior Software Engineer - Java").location("Pune").build()))
				.isEqualTo("too senior (principal)");

		ExclusionFilter upToSenior = new ExclusionFilter(profile()
				.filters(new MatchingProfile.Filters(List.of(), "ANYWHERE", "UP_TO_SENIOR")).build());
		assertThat(upToSenior.reason(job().title("Senior Java Engineer").build())).isNull();
		assertThat(upToSenior.reason(job().title("Lead Software Engineer").build())).isEqualTo("too senior (lead)");
	}

	@Test
	void noFiltersKeepEverything() {
		ExclusionFilter none = new ExclusionFilter(profile().build());

		assertThat(none.reason(job().title("Principal Engineer (m/w/d)").location("Berlin").build())).isNull();
	}

}
