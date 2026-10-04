package com.naukriradar.core.event;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.Topics;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.service.ApplyRunService;
import com.naukriradar.core.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * match.created -> if the user has auto apply on, plan applications from the fresh matches.
 * The topic is keyed by user id, so one user's events arrive in order on one partition and
 * two runs for the same user never race; different users are handled in parallel.
 */
@Component
public class AutoApplyConsumer {

	private static final Logger log = LoggerFactory.getLogger(AutoApplyConsumer.class);

	static final String CONSUMER = "core-api.auto-apply";

	private final IdempotentConsumer idempotent;
	private final ProfileService profiles;
	private final ApplyRunService applyRuns;

	public AutoApplyConsumer(IdempotentConsumer idempotent, ProfileService profiles, ApplyRunService applyRuns) {
		this.idempotent = idempotent;
		this.profiles = profiles;
		this.applyRuns = applyRuns;
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.MATCH_CREATED + "')}", groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onMatchCreated(String message) {
		// repeating is safe: one application per user and job, one running apply run per user
		idempotent.handleRepeatable(CONSUMER, message, event -> {
			String userId = event.key();
			boolean autoApply;
			try {
				autoApply = profiles.getProfile(userId).autoApplyEnabled();
			}
			catch (NotFoundException ex) {
				return;
			}
			if (!autoApply) {
				return;
			}
			try {
				applyRuns.runNow(userId);
			}
			catch (ConflictException ex) {
				log.debug("Apply run already going for {}; it picks up the new matches", userId);
			}
		});
	}

}
