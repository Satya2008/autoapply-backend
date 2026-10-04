package com.naukriradar.core.event;

import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.Topics;
import com.naukriradar.core.service.ApplyProgressStreams;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * apply.completed -> the user's open live streams on this instance. Every instance has its
 * own consumer group (a random id), so every instance sees every event: whichever one holds
 * the user's stream passes it on. Only new events matter for a live screen, so it starts
 * from the latest offset.
 */
@Component
public class ApplyProgressListener {

	private final ApplyProgressStreams streams;
	private final JsonMapper json;

	public ApplyProgressListener(ApplyProgressStreams streams, JsonMapper json) {
		this.streams = streams;
		this.json = json;
	}

	public static final String LISTENER_ID = "apply-progress";

	@KafkaListener(id = LISTENER_ID, topics = "#{@eventTopics.name('" + Topics.APPLY_COMPLETED + "')}",
			groupId = "#{@eventTopics.group('core-api.live-')}#{T(java.util.UUID).randomUUID()}",
			properties = "auto.offset.reset=latest")
	public void onApplyCompleted(String message) {
		EventEnvelope event = json.readValue(message, EventEnvelope.class);
		String name = "ApplicationAttemptFinished".equals(event.type()) ? "application-updated" : "apply-run-completed";
		streams.send(event.key(), name, event.payload());
	}

}
