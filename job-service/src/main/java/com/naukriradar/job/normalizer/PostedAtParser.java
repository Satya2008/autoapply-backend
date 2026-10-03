package com.naukriradar.job.normalizer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the many ways boards write a posting date into an {@link Instant}. Dates without a
 * zone are taken as UTC. Anything unreadable, before 2000 or more than a day in the future
 * gives null: a wrong date is worse than no date for "newest first" sorting.
 */
final class PostedAtParser {

	/** Epoch values above this are milliseconds (this is year 5138 in seconds). */
	private static final long MILLIS_THRESHOLD = 100_000_000_000L;

	private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");

	private static final Pattern RELATIVE = Pattern.compile("(\\d+)\\+?\\s*(minute|min|hour|hr|day|week|month)s?\\s+ago");

	private static final List<DateTimeFormatter> LOCAL_DATE_TIMES = List.of(
			DateTimeFormatter.ISO_LOCAL_DATE_TIME,
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT));

	private PostedAtParser() {
	}

	static Instant parse(Object value, Instant now) {
		Instant parsed = parseUnchecked(value, now);
		if (parsed == null || parsed.isBefore(EARLIEST) || parsed.isAfter(now.plus(Duration.ofDays(1)))) {
			return null;
		}
		return parsed;
	}

	private static Instant parseUnchecked(Object value, Instant now) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return fromEpoch(number.longValue());
		}
		String text = value.toString().strip();
		if (text.isEmpty()) {
			return null;
		}
		if (text.matches("\\d{9,13}(\\.\\d+)?")) {
			return fromEpoch(new BigDecimal(text).longValue());
		}
		Instant relative = parseRelative(text.toLowerCase(Locale.ROOT), now);
		if (relative != null) {
			return relative;
		}
		return parseAbsolute(text);
	}

	private static Instant fromEpoch(long epoch) {
		if (epoch <= 0) {
			return null;
		}
		return epoch >= MILLIS_THRESHOLD ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
	}

	private static Instant parseRelative(String text, Instant now) {
		if (text.equals("today") || text.equals("just now") || text.equals("just posted")) {
			return now;
		}
		if (text.equals("yesterday")) {
			return now.minus(1, ChronoUnit.DAYS);
		}
		Matcher matcher = RELATIVE.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		long amount = Long.parseLong(matcher.group(1));
		return switch (matcher.group(2)) {
			case "minute", "min" -> now.minus(amount, ChronoUnit.MINUTES);
			case "hour", "hr" -> now.minus(amount, ChronoUnit.HOURS);
			case "day" -> now.minus(amount, ChronoUnit.DAYS);
			case "week" -> now.minus(amount * 7, ChronoUnit.DAYS);
			default -> now.minus(amount * 30, ChronoUnit.DAYS);
		};
	}

	private static Instant parseAbsolute(String text) {
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException ignored) {
			// try the next format
		}
		try {
			return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
		}
		catch (DateTimeParseException ignored) {
			// try the next format
		}
		for (DateTimeFormatter formatter : LOCAL_DATE_TIMES) {
			try {
				return LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC);
			}
			catch (DateTimeParseException ignored) {
				// try the next format
			}
		}
		try {
			return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
			return null;
		}
	}

}
