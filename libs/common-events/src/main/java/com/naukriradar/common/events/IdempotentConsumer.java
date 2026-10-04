package com.naukriradar.common.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a consumer's work at most once per event, even though Kafka may deliver an event more
 * than once (a relay resend, a rebalance before the offset was committed). The event id is
 * recorded in {@code processed_events} in the same transaction as the work: if the work
 * fails, neither is saved and the retry starts clean; if it succeeds, a second delivery finds
 * the id and is skipped.
 */
public class IdempotentConsumer {

	private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

	private static final String MARK = "INSERT INTO processed_events (consumer, event_id, processed_at) VALUES (?, ?, ?)";

	/** For marking after the work: a duplicate means a parallel delivery finished first, which is fine. */
	private static final String MARK_IF_NEW = "INSERT IGNORE INTO processed_events (consumer, event_id, processed_at) VALUES (?, ?, ?)";

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public IdempotentConsumer(JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * For work that takes long or starts its own transactions (runs, remote calls): skip if
	 * already processed, do the work, then mark it. A crash between the work and the mark
	 * means the work runs again on redelivery, so it must be safe to repeat; ours is,
	 * through business keys (one run per user, one application per user and job).
	 *
	 * @return false if this event was already processed
	 */
	public boolean handleRepeatable(String consumer, String message, Consumer<EventEnvelope> work) {
		EventEnvelope event = json.readValue(message, EventEnvelope.class);
		Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM processed_events WHERE consumer = ? AND event_id = ?",
				Integer.class, consumer, event.eventId());
		if (seen != null && seen > 0) {
			log.debug("{} already processed event {}", consumer, event.eventId());
			return false;
		}
		work.accept(event);
		jdbc.update(con -> {
			var ps = con.prepareStatement(MARK_IF_NEW);
			ps.setString(1, consumer);
			ps.setString(2, event.eventId());
			ps.setTimestamp(3, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
			return ps;
		});
		return true;
	}

	/**
	 * For short work that only touches this database: the work and the "processed" mark
	 * commit together.
	 *
	 * @param consumer a stable name for this piece of work, e.g. "matching.rematch"
	 * @param message the Kafka value: an {@link EventEnvelope} as JSON
	 * @return false if this event was already processed
	 * @throws RuntimeException whatever the work threw, so the listener's retry and dead
	 *     letter handling kick in
	 */
	public boolean handle(String consumer, String message, Consumer<EventEnvelope> work) {
		EventEnvelope event = json.readValue(message, EventEnvelope.class);
		Boolean done = transaction.execute(status -> {
			try {
				jdbc.update(con -> {
					var ps = con.prepareStatement(MARK);
					ps.setString(1, consumer);
					ps.setString(2, event.eventId());
					ps.setTimestamp(3, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
					return ps;
				});
			}
			catch (DuplicateKeyException ex) {
				return false;
			}
			work.accept(event);
			return true;
		});
		if (!Boolean.TRUE.equals(done)) {
			log.debug("{} already processed event {}", consumer, event.eventId());
			return false;
		}
		return true;
	}

}
