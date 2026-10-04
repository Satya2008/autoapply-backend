package com.naukriradar.common.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Events that failed every retry. Each is kept here (and also sent to the topic's
 * {@code .dlq}) so an admin can see why it failed and, once the cause is fixed, send it
 * again. One bad event never blocks the ones behind it.
 */
public class DeadLetters {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final OutboxWriter outbox;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public DeadLetters(JdbcTemplate jdbc, OutboxWriter outbox, JsonMapper json) {
		this.jdbc = jdbc;
		this.outbox = outbox;
		this.json = json;
	}

	public void record(ConsumerRecord<?, ?> record, Exception error) {
		Throwable cause = error.getCause() != null ? error.getCause() : error;
		String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
		jdbc.update(con -> {
			var ps = con.prepareStatement("INSERT INTO dead_letters (id, topic, event_key, payload, error, failed_at)"
					+ " VALUES (?, ?, ?, ?, ?, ?)");
			ps.setString(1, UUID.randomUUID().toString());
			ps.setString(2, record.topic());
			ps.setString(3, record.key() == null ? null : record.key().toString());
			ps.setString(4, String.valueOf(record.value()));
			ps.setString(5, message.length() > 1000 ? message.substring(0, 1000) : message);
			ps.setTimestamp(6, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
			return ps;
		});
	}

	public List<DeadLetter> list(String topic, int limit) {
		String where = topic == null || topic.isBlank() ? "" : " WHERE topic = ?";
		Object[] args = topic == null || topic.isBlank() ? new Object[] { limit } : new Object[] { topic.strip(), limit };
		return jdbc.query("SELECT id, topic, event_key, payload, error, failed_at, replayed_at FROM dead_letters" + where
				+ " ORDER BY failed_at DESC LIMIT ?", (rs, n) -> new DeadLetter(rs.getString("id"), rs.getString("topic"),
						rs.getString("event_key"), rs.getString("payload"), rs.getString("error"),
						instant(rs.getTimestamp("failed_at", (Calendar) UTC.clone())),
						instant(rs.getTimestamp("replayed_at", (Calendar) UTC.clone()))),
				args);
	}

	/**
	 * Sends the event to its topic again (through the outbox, so it is as reliable as any
	 * other event), with its original event id: a consumer that did manage to process it
	 * before still skips it.
	 */
	@Transactional
	public DeadLetter replay(String id) {
		DeadLetter letter = find(id);
		if (letter.replayedAt() != null) {
			throw new ConflictException("Dead letter " + id + " was already replayed at " + letter.replayedAt() + ".");
		}
		EventEnvelope event;
		try {
			event = json.readValue(letter.payload(), EventEnvelope.class);
		}
		catch (RuntimeException ex) {
			throw new BadRequestException("Dead letter " + id + " is not a readable event, so it can't be replayed.");
		}
		outbox.resend(letter.topic(), event);
		jdbc.update(con -> {
			var ps = con.prepareStatement("UPDATE dead_letters SET replayed_at = ? WHERE id = ?");
			ps.setTimestamp(1, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
			ps.setString(2, id);
			return ps;
		});
		return find(id);
	}

	private DeadLetter find(String id) {
		List<DeadLetter> found = jdbc.query("SELECT id, topic, event_key, payload, error, failed_at, replayed_at"
				+ " FROM dead_letters WHERE id = ?", (rs, n) -> new DeadLetter(rs.getString("id"), rs.getString("topic"),
						rs.getString("event_key"), rs.getString("payload"), rs.getString("error"),
						instant(rs.getTimestamp("failed_at", (Calendar) UTC.clone())),
						instant(rs.getTimestamp("replayed_at", (Calendar) UTC.clone()))),
				id);
		if (found.isEmpty()) {
			throw new NotFoundException("No dead letter " + id + ".");
		}
		return found.get(0);
	}

	private static Instant instant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	public record DeadLetter(String id, String topic, String key, String payload, String error, Instant failedAt,
			Instant replayedAt) {
	}

}
