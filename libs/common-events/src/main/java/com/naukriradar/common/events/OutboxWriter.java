package com.naukriradar.common.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records an event in the {@code outbox} table, in the same transaction as the change it
 * describes. Writing to the database and to Kafka separately can't be atomic: the commit
 * may succeed and the send fail (event lost) or the other way round (event about a change
 * that never happened). Here both commit together or neither does, and {@link OutboxRelay}
 * sends the row to Kafka afterwards.
 */
public class OutboxWriter {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final String source;
	private final EventTopics topics;
	private final Clock clock = Clock.systemUTC();

	public OutboxWriter(JdbcTemplate jdbc, JsonMapper json, String source, EventTopics topics) {
		this.jdbc = jdbc;
		this.json = json;
		this.source = source;
		this.topics = topics;
	}

	/**
	 * Must run inside the caller's transaction; without one there is nothing to be atomic
	 * with, so it refuses.
	 *
	 * @return the event id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public String publish(String topic, String key, String type, Object payload) {
		return publish(topic, key, type, 1, payload);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String publish(String topic, String key, String type, int version, Object payload) {
		String id = UUID.randomUUID().toString();
		EventEnvelope envelope = new EventEnvelope(id, type, version, clock.instant(), source, key, json.valueToTree(payload));
		insert(UUID.randomUUID().toString(), topics.name(topic), envelope);
		return id;
	}

	/** Queues an event again as it was, same event id included (replaying a dead letter). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void resend(String topic, EventEnvelope envelope) {
		insert(UUID.randomUUID().toString(), topic, envelope);
	}

	private void insert(String rowId, String topic, EventEnvelope envelope) {
		String body = json.writeValueAsString(envelope);
		Instant now = clock.instant();
		jdbc.update(con -> {
			var ps = con.prepareStatement("INSERT INTO outbox (id, topic, event_key, event_type, payload, created_at)"
					+ " VALUES (?, ?, ?, ?, ?, ?)");
			ps.setString(1, rowId);
			ps.setString(2, topic);
			ps.setString(3, envelope.key());
			ps.setString(4, envelope.type());
			ps.setString(5, body);
			ps.setTimestamp(6, Timestamp.from(now), (Calendar) UTC.clone());
			return ps;
		});
	}

}
