package com.naukriradar.matching.scoring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * First performance number for matching: scoring 5,000 realistic postings for a profile
 * with 15 skills. The bound is loose on purpose (CI machines vary); the printed time is
 * the number to track.
 */
class MatchScorerPerformanceTest {

	@Test
	void scoresFiveThousandJobsQuickly() {
		MatchScorer scorer = new MatchScorer(MatchScorerTest.allFactors(),
				new MatchingProperties(new HashMap<>(MatchScorerTest.defaultWeights()), 20, 300, 60, 2, 20));
		MatchContext context = MatchContext.of(profile()
				.skills("java", "spring boot", "mysql", "kafka", "redis", "docker", "kubernetes", "aws", "rest api",
						"microservices", "hibernate", "junit", "git", "linux", "c#")
				.roles("Backend Engineer", "Java Developer", "Platform Engineer")
				.locations("Pune", "Bengaluru", "Hyderabad")
				.build(), NOW);
		List<CandidateJob> jobs = new ArrayList<>();
		String filler = "We build reliable distributed systems for millions of users. ".repeat(60);
		for (int i = 0; i < 5_000; i++) {
			jobs.add(job().id("job-" + i).title(i % 3 == 0 ? "Senior Backend Engineer" : "Software Developer " + i)
					.location(i % 2 == 0 ? "Pune, India" : "Berlin").salary(800_000L, 1_600_000L, "INR")
					.description(filler + " Java, Kafka and Docker. 3-5 years of experience. " + filler).build());
		}

		long start = System.nanoTime();
		int total = 0;
		for (CandidateJob job : jobs) {
			total += scorer.score(context, job).total();
		}
		Duration took = Duration.ofNanos(System.nanoTime() - start);

		System.out.printf("Scored %d jobs in %d ms (%.2f ms per job), average score %d%n", jobs.size(), took.toMillis(),
				took.toNanos() / 1e6 / jobs.size(), total / jobs.size());
		assertThat(took).isLessThan(Duration.ofSeconds(20));
	}

}
