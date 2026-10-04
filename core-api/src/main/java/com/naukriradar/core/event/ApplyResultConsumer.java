package com.naukriradar.core.event;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.Topics;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.SubmittedVia;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.service.ApplicationStateMachine;
import com.naukriradar.core.service.ApplyExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * apply.completed from the apply worker -> move the application on. Only an application still
 * SENDING is touched, so a late or repeated result can't undo what happened since (the
 * candidate skipping it, a newer attempt's result).
 */
@Component
public class ApplyResultConsumer {

	static final String CONSUMER = "core-api.apply-results";

	static final String TYPE = "ApplicationAttemptFinished";

	private final IdempotentConsumer idempotent;
	private final ApplicationRepository applications;
	private final ApplicationStateMachine stateMachine;
	private final ApplyExecutor executor;

	public ApplyResultConsumer(IdempotentConsumer idempotent, ApplicationRepository applications,
			ApplicationStateMachine stateMachine, ApplyExecutor executor) {
		this.idempotent = idempotent;
		this.applications = applications;
		this.stateMachine = stateMachine;
		this.executor = executor;
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.APPLY_COMPLETED + "')}",
			groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onApplyCompleted(String message) {
		idempotent.handle(CONSUMER, message, event -> {
			if (!TYPE.equals(event.type())) {
				return; // apply-run summaries share the topic; they are for the live screen
			}
			JsonNode result = event.payload();
			Application application = applications.findById(result.path("applicationId").asString()).orElse(null);
			if (application == null || application.getStatus() != ApplicationStatus.SENDING) {
				return;
			}
			String note = result.path("note").asString();
			switch (result.path("outcome").asString()) {
				case "SUBMITTED" -> {
					stateMachine.move(application, ApplicationStatus.SUBMITTED, note);
					application.submittedVia(SubmittedVia.BROWSER);
				}
				case "FAILED" -> executor.failed(application, note);
				default -> {
					// NEEDS_YOU or UNKNOWN: the candidate takes over, with the reason
					stateMachine.move(application, ApplicationStatus.NEEDS_YOU, note);
					application.needsYouBecause(note);
				}
			}
		});
	}

}
