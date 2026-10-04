package com.naukriradar.notify.event;

import java.util.List;

import com.naukriradar.common.events.EventEnvelope;
import com.naukriradar.common.events.Topics;
import com.naukriradar.notify.channel.Channel;
import com.naukriradar.notify.service.Delivery;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * notify.requested -> messages out. When a channel fails (SMTP down), the event fails too, so
 * Kafka's retries and finally the dead letters keep it; the log makes sure a retry never
 * repeats a channel that already worked. Nothing upstream waits for any of this.
 */
@Component
public class NotifyConsumer {

	static final String CONSUMER = "notification-service.deliver";

	private final Delivery delivery;
	private final JsonMapper json;

	public NotifyConsumer(Delivery delivery, JsonMapper json) {
		this.delivery = delivery;
		this.json = json;
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.NOTIFY_REQUESTED + "')}",
			groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onNotifyRequested(String message) {
		EventEnvelope event = json.readValue(message, EventEnvelope.class);
		NotifyRequested request = json.treeToValue(event.payload(), NotifyRequested.class);
		List<Channel> failed = delivery.deliver(event.eventId(), request.template(), request.recipient(),
				request.channels() == null ? List.of() : request.channels(), request.data());
		if (!failed.isEmpty()) {
			throw new IllegalStateException("Not delivered on " + failed + "; will retry");
		}
	}

}
