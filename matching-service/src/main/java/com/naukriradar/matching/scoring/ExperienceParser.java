package com.naukriradar.matching.scoring;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the experience a posting asks for: "3-5 years", "5+ yrs", "minimum 4 years", or,
 * failing that, the seniority in the title ("Senior" means 5 years or more).
 */
final class ExperienceParser {

	/** Years above this are noise ("since 1998", "100 years of history"). */
	private static final int MAX_YEARS = 30;

	private static final int WINDOW_BEFORE = 40;

	private static final int WINDOW_AFTER = 50;

	private static final Pattern RANGE = Pattern.compile(
			"(\\d{1,2})\\s*(?:\\+\\s*)?(?:-|–|—|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b");

	private static final Pattern MINIMUM = Pattern.compile(
			"(?:(?:at\\s+least|minimum(?:\\s+of)?|min\\.?)\\s*)?(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)(?:'|’)?\\s*(?:of\\s+)?(?:\\w+\\s+){0,3}?(?:experience|exp)\\b");

	private ExperienceParser() {
	}

	static Range parse(String title, String description) {
		String text = yearWindows(description == null ? "" : description.toLowerCase(Locale.ROOT));
		// most postings mention years only once or twice; the regexes run on just those bits
		if (text.isEmpty()) {
			return fromTitle(title == null ? "" : title.toLowerCase(Locale.ROOT));
		}
		Matcher range = RANGE.matcher(text);
		while (range.find()) {
			int min = Integer.parseInt(range.group(1));
			int max = Integer.parseInt(range.group(2));
			if (min <= max && max <= MAX_YEARS) {
				return new Range(min, max);
			}
		}
		Matcher minimum = MINIMUM.matcher(text);
		while (minimum.find()) {
			int min = Integer.parseInt(minimum.group(1));
			if (min <= MAX_YEARS) {
				return new Range(min, null);
			}
		}
		return fromTitle(title == null ? "" : title.toLowerCase(Locale.ROOT));
	}

	/**
	 * The text around each "year"/"yr", joined with line breaks. Long enough on each side for
	 * "minimum of 10 years of relevant experience", short enough to skip the rest of the posting.
	 */
	private static String yearWindows(String text) {
		StringBuilder windows = new StringBuilder();
		int from = 0;
		int lastEnd = 0;
		while (true) {
			int year = text.indexOf("year", from);
			int yr = text.indexOf("yr", from);
			int at = year < 0 ? yr : yr < 0 ? year : Math.min(year, yr);
			if (at < 0) {
				return windows.toString();
			}
			int start = Math.max(lastEnd, at - WINDOW_BEFORE);
			int end = Math.min(text.length(), at + WINDOW_AFTER);
			if (start > lastEnd || windows.isEmpty()) {
				windows.append('\n');
			}
			windows.append(text, start, end);
			lastEnd = end;
			from = at + 2;
		}
	}

	private static Range fromTitle(String title) {
		if (title.matches(".*\\b(intern|internship|trainee|fresher|graduate)\\b.*")) {
			return new Range(0, 1);
		}
		if (title.matches(".*\\b(junior|jr\\.?|entry level)\\b.*")) {
			return new Range(0, 2);
		}
		if (title.matches(".*\\b(principal|staff|architect|head|director)\\b.*")) {
			return new Range(8, null);
		}
		if (title.matches(".*\\b(lead|manager)\\b.*")) {
			return new Range(6, null);
		}
		if (title.matches(".*\\b(senior|sr\\.?)\\b.*")) {
			return new Range(5, null);
		}
		return null;
	}

	/** Years wanted; {@code max} is null when open-ended. */
	record Range(int min, Integer max) {

		String describe() {
			return max == null ? min + "+ years" : min + "-" + max + " years";
		}

	}

}
