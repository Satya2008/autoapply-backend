package com.naukriradar.matching.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rematches everyone who used matching in the last 30 days: after new jobs are parsed, and in
 * the nightly batch. A full match pool means wait a moment, not skip the user: this loop is
 * the backpressure.
 */
@Service
public class RematchCoordinator {

	private static final Logger log = LoggerFactory.getLogger(RematchCoordinator.class);

	static final Duration ACTIVE_WITHIN = Duration.ofDays(30);

	/** How long one round waits in total for room in a full match pool before giving up. */
	static final Duration MAX_WAIT = Duration.ofMinutes(3);

	private final MatchRunStore runs;
	private final MatchRunService runService;

	public RematchCoordinator(MatchRunStore runs, MatchRunService runService) {
		this.runs = runs;
		this.runService = runService;
	}

	/** @return runs started, out of the active users */
	public Round rematchActiveUsers(String why) {
		List<String> users = runs.activeUsers(ACTIVE_WITHIN);
		Instant giveUpAt = Instant.now().plus(MAX_WAIT);
		int started = 0;
		for (String user : users) {
			if (startWhenThereIsRoom(user, giveUpAt)) {
				started++;
			}
		}
		log.info("{}: rematching {} of {} active user(s)", why, started, users.size());
		return new Round(started, users.size());
	}

	/** @return whether a run was started */
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

	public record Round(int started, int activeUsers) {
	}

}
