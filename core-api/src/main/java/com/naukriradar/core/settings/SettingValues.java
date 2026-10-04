package com.naukriradar.core.settings;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.support.CronExpression;

/**
 * Checks and reads setting values. Every value is stored as text; this is the one place
 * that knows how each type is written, so a value that passed {@link #validate} can always
 * be read back.
 */
public final class SettingValues {

	private static final Pattern DOMAIN = Pattern.compile("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");

	private static final int MAX_TEXT = 4000;

	private SettingValues() {
	}

	/**
	 * Returns the value in canonical form (trimmed, domains lower-cased, booleans as
	 * true/false), or throws with a message an admin can act on.
	 */
	public static String validate(SettingDefinition definition, String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("A value is required.");
		}
		String value = raw.strip();
		if (value.length() > MAX_TEXT) {
			throw new IllegalArgumentException("The value is longer than " + MAX_TEXT + " characters.");
		}
		return switch (definition.type()) {
			case STRING, SECRET -> value;
			case INT -> {
				long number;
				try {
					number = Long.parseLong(value);
				}
				catch (NumberFormatException ex) {
					throw new IllegalArgumentException("'" + value + "' is not a whole number.");
				}
				checkRange(definition, number, "");
				yield Long.toString(number);
			}
			case BOOLEAN -> {
				String lower = value.toLowerCase(Locale.ROOT);
				if (!lower.equals("true") && !lower.equals("false")) {
					throw new IllegalArgumentException("Use true or false.");
				}
				yield lower;
			}
			case DURATION -> {
				Duration duration;
				try {
					duration = DurationStyle.detectAndParse(value);
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException("'" + value + "' is not a duration like 30m, 2h or 1d.");
				}
				if (duration.isNegative() || duration.isZero()) {
					throw new IllegalArgumentException("The duration must be more than zero.");
				}
				checkRange(definition, duration.toMinutes(), " minutes");
				yield value;
			}
			case CRON -> {
				try {
					CronExpression.parse(value);
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException("'" + value + "' is not a cron expression (" + ex.getMessage()
							+ "). Use six fields: second minute hour day month weekday.");
				}
				yield value;
			}
			case DOMAIN_LIST -> {
				List<String> domains = domains(value);
				for (String domain : domains) {
					if (!DOMAIN.matcher(domain).matches()) {
						throw new IllegalArgumentException("'" + domain + "' is not a domain like example.com.");
					}
				}
				yield String.join(", ", domains);
			}
		};
	}

	public static int asInt(String value) {
		return Integer.parseInt(value.strip());
	}

	public static boolean asBoolean(String value) {
		return Boolean.parseBoolean(value.strip());
	}

	public static Duration asDuration(String value) {
		return DurationStyle.detectAndParse(value.strip());
	}

	public static List<String> asDomains(String value) {
		return domains(value);
	}

	static List<String> domains(String value) {
		return Arrays.stream(value.split("[,\\s]+"))
				.map(d -> d.strip().toLowerCase(Locale.ROOT))
				.map(d -> d.startsWith("www.") ? d.substring(4) : d)
				.filter(d -> !d.isEmpty())
				.distinct()
				.toList();
	}

	private static void checkRange(SettingDefinition definition, long value, String unit) {
		if (definition.min() != null && value < definition.min()) {
			throw new IllegalArgumentException("Must be at least " + definition.min() + unit + ".");
		}
		if (definition.max() != null && value > definition.max()) {
			throw new IllegalArgumentException("Must be at most " + definition.max() + unit + ".");
		}
	}

}
