package com.naukriradar.matching.event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.Topics;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.matching.service.MatchRunService;
import com.naukriradar.matching.service.MatchRunStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * jobs.parsed -> rematch everyone who used matching in the last 30 days, so new jobs reach
 * them without anyone pressing a button. Each run ends with match.created.
 */
@Component
public class RematchConsumer {

	private static final Logger log = LoggerFactory.getLogger(RematchConsumer.class);

	static final String CONSUMER = "matching-service.rematch";

	static final Duration ACTIVE_WITHIN = Duration.ofDays(30);

	/** How long one rematch waits in total for room in a full match pool before giving up. */
	static final Duration MAX_WAIT = Duration.ofMinutes(3);

	private final IdempotentConsumer idempotent;
	private final MatchRunStore runs;
	private final MatchRunService runService;

	public RematchConsumer(IdempotentConsumer idempotent, MatchRunStore runs, MatchRunService runService) {
		this.idempotent = idempotent;
		this.runs = runs;
		this.runService = runService;
	}

	/**
	 * A full pool means wait a moment, not skip the user: this consumer is the backpressure.
	 *
	 * @return whether a run was started
	 */
	private boolean startWhenThereIsRoom(String user, Instant giveUpAt) {
		while (true) {
			try {
				runService.start(user);
				return true;
			}
			catch (ConflictException ex) {
				// already running for this user; that run sees the new jobs too
				return false;
			}
			catch (ServiceUnavailableException ex) {
				if (Instant.now().isAfter(giveUpAt)) {
					log.warn("Rematch gave up on {}: the match pool stayed full", user);
					return false;
				}
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.JOBS_PARSED + "')}", groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onJobsParsed(String message) {
		// starting a run twice is harmless: a user has at most one running run
		idempotent.handleRepeatable(CONSUMER, message, event -> {
			List<String> users = runs.activeUsers(ACTIVE_WITHIN);
			Instant giveUpAt = Instant.now().plus(MAX_WAIT);
			int started = 0;
			for (String user : users) {
				if (startWhenThereIsRoom(user, giveUpAt)) {
					started++;
				}
			}
			log.info("New jobs parsed: rematching {} of {} active user(s)", started, users.size());
		});
	}

}
