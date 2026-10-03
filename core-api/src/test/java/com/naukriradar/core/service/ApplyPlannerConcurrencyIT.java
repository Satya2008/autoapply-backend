package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.naukriradar.core.client.MatchForApply;
import com.naukriradar.core.dto.request.CreateUserRequest;
import com.naukriradar.core.dto.request.ReplaceSkillsRequest;
import com.naukriradar.core.dto.request.SkillRequest;
import com.naukriradar.core.dto.request.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two planners for the same user at the same moment: the profile row lock must make the
 * second wait and then see the first one's work, so no job gets two applications.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplyPlannerConcurrencyIT {

	@Autowired
	private ApplyPlanner planner;

	@Autowired
	private UserService userService;

	@Autowired
	private ProfileService profileService;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void twoPlansAtOnceCreateEachApplicationOnce() throws Exception {
		String userId = userService.createUser(new CreateUserRequest("race-" + UUID.randomUUID() + "@example.com")).id();
		profileService.replaceSkills(userId, new ReplaceSkillsRequest(List.of(
				new SkillRequest("java", 3), new SkillRequest("sql", 2), new SkillRequest("docker", 1))));
		profileService.updateProfile(userId, new UpdateProfileRequest("Racer", null, null, null, 3, null, null, null,
				null, null, Set.of("Backend Engineer"), Set.of(), Set.of(), Set.of(), true, 0, 50, true));

		List<MatchForApply> matches = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			String host = i % 3 == 0 ? "boards.greenhouse.io" : i % 3 == 1 ? "www.linkedin.com" : "careers.acme.com";
			matches.add(new MatchForApply("job-" + i, 90 - i, "Role " + i, "Company " + i, "Pune",
					"https://" + host + "/jobs/" + i));
		}

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<ApplyPlanner.PlanResult>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return planner.plan(userId, matches);
				}));
			}
			start.countDown();
			ApplyPlanner.PlanResult first = results.get(0).get(30, TimeUnit.SECONDS);
			ApplyPlanner.PlanResult second = results.get(1).get(30, TimeUnit.SECONDS);

			int created = first.queued().size() + first.needsYou() + second.queued().size() + second.needsYou();
			assertThat(created).isEqualTo(30);
			assertThat(first.alreadyApplied() + second.alreadyApplied()).isEqualTo(30);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM applications WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(30);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM applications WHERE user_id = ? AND status = 'QUEUED' AND risk_band <> 'LOW'",
				Integer.class, userId)).isZero();
	}

}
