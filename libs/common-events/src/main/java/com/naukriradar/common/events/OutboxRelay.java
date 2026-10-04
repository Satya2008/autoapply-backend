package com.naukriradar.common.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Sends outbox rows to Kafka, oldest first, and marks them sent. Delivery is at least once:
 * if the process dies after Kafka took a message but before the row was marked, the row is
 * sent again, which is why every consumer ignores events it has already processed.
 *
 * <p>One instance relays at a time (a Redis lock), which keeps events in the order they were
 * written. A send that fails stops the batch, so a later event never overtakes an earlier one;
 * the next round tries again.
 */
public class OutboxRelay implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final int BATCH = 100;

	private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

	/** Sent rows are kept this long, for looking back; then deleted. */
	private static final Duration KEEP_SENT = Duration.ofDays(7);

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final KafkaTemplate<String, String> kafka;
	private final DistributedLock lock;
	private final Duration every;
	private final Clock clock = Clock.systemUTC();
	private ScheduledExecutorService executor;
	private volatile boolean running;
	private long rounds;

	public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, DistributedLock lock, Duration every) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.lock = lock;
		this.every = every;
	}

	@Override
	public void start() {
		executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").daemon().factory());
		executor.scheduleWithFixedDelay(this::round, every.toMillis(), every.toMillis(), TimeUnit.MILLISECONDS);
		running = true;
	}

	@Override
	public void stop() {
		running = false;
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	/** One relay pass; public so tests and the admin can run it on demand. @return rows sent */
	public int round() {
		Optional<LockHandle> held;
		try {
			held = lock.tryAcquire("outbox-relay");
		}
		catch (LockUnavailableException ex) {
			return 0;
		}
		if (held.isEmpty()) {
			return 0;
		}
		try (LockHandle ignored = held.get()) {
			int sent = sendPending();
			if (++rounds % 1000 == 0) {
				jdbc.update(con -> {
					var ps = con.prepareStatement("DELETE FROM outbox WHERE published_at < ? LIMIT 1000");
					ps.setTimestamp(1, Timestamp.from(clock.instant().minus(KEEP_SENT)), (Calendar) UTC.clone());
					return ps;
				});
			}
			return sent;
		}
		catch (RuntimeException ex) {
			log.warn("Outbox relay round failed: {}", ex.getMessage());
			return 0;
		}
	}

	private int sendPending() {
		List<Row> rows = jdbc.query("SELECT id, topic, event_key, payload FROM outbox WHERE published_at IS NULL"
				+ " ORDER BY created_at, id LIMIT " + BATCH,
				(rs, n) -> new Row(rs.getString("id"), rs.getString("topic"), rs.getString("event_key"), rs.getString("payload")));
		int sent = 0;
		for (Row row : rows) {
			try {
				kafka.send(row.topic(), row.key(), row.payload()).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			}
			catch (Exception ex) {
				if (ex instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
				jdbc.update("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?",
						error.length() > 500 ? error.substring(0, 500) : error, row.id());
				log.warn("Outbox: sending {} to {} failed; will retry", row.id(), row.topic());
				break;
			}
			jdbc.update(con -> {
				var ps = con.prepareStatement("UPDATE outbox SET published_at = ? WHERE id = ?");
				ps.setTimestamp(1, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
				ps.setString(2, row.id());
				return ps;
			});
			sent++;
		}
		return sent;
	}

	private record Row(String id, String topic, String key, String payload) {
	}

}
