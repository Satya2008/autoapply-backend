package com.naukriradar.job.normalizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.naukriradar.job.model.JobField;
import com.naukriradar.job.provider.RawJob;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Makes every board's postings look the same: trimmed text, plain-text descriptions, real
 * dates, numeric salaries and a remote flag. Items missing what a job needs are skipped with
 * a reason instead of failing the whole run.
 */
@Component
public class JobNormalizer {

	static final int MAX_EXTERNAL_ID = 200;
	static final int MAX_TITLE = 300;
	static final int MAX_COMPANY = 200;
	static final int MAX_LOCATION = 200;
	static final int MAX_URL = 1000;
	static final int MAX_DESCRIPTION = 20_000;

	private static final Pattern REMOTE_WORD = Pattern.compile("\\b(remote|work from home|wfh|anywhere)\\b");
	private static final Pattern BLOCK_TAGS = Pattern.compile("(?i)<\\s*(br|/p|/div|/h[1-6]|/tr)\\s*/?>");
	private static final Pattern LIST_ITEM = Pattern.compile("(?i)<\\s*li[^>]*>");
	private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
	private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");

	public NormalizationResult normalize(RawJob raw, Instant now) {
		String externalId = text(raw.get(JobField.EXTERNAL_ID));
		if (externalId == null) {
			return NormalizationResult.skip("missing externalId");
		}
		if (externalId.length() > MAX_EXTERNAL_ID) {
			return NormalizationResult.skip("externalId longer than " + MAX_EXTERNAL_ID + " characters");
		}
		String title = truncate(singleLine(htmlToText(text(raw.get(JobField.TITLE)))), MAX_TITLE);
		if (title == null) {
			return NormalizationResult.skip(externalId + ": missing title");
		}
		String company = truncate(singleLine(htmlToText(text(raw.get(JobField.COMPANY)))), MAX_COMPANY);
		if (company == null) {
			return NormalizationResult.skip(externalId + ": missing company");
		}
		String applyUrl = httpUrl(text(raw.get(JobField.APPLY_URL)));
		if (applyUrl == null) {
			return NormalizationResult.skip(externalId + ": missing or invalid applyUrl");
		}

		String location = truncate(singleLine(locationText(raw.get(JobField.LOCATION))), MAX_LOCATION);
		String description = truncate(htmlToText(text(raw.get(JobField.DESCRIPTION))), MAX_DESCRIPTION);
		boolean remote = remote(raw.get(JobField.REMOTE), title, location);

		List<Long> minAmounts = SalaryParser.amounts(raw.get(JobField.SALARY_MIN));
		List<Long> maxAmounts = SalaryParser.amounts(raw.get(JobField.SALARY_MAX));
		Long salaryMin = minAmounts.isEmpty() ? null : minAmounts.get(0);
		// a single field holding "60k - 80k" fills both ends
		Long salaryMax = !maxAmounts.isEmpty() ? maxAmounts.get(maxAmounts.size() - 1)
				: minAmounts.size() > 1 ? minAmounts.get(minAmounts.size() - 1) : null;
		if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
			Long swap = salaryMin;
			salaryMin = salaryMax;
			salaryMax = swap;
		}

		return NormalizationResult.ok(new NormalizedJob(externalId, title, company, location, remote, salaryMin,
				salaryMax, currency(raw.get(JobField.CURRENCY)), PostedAtParser.parse(raw.get(JobField.POSTED_AT), now),
				applyUrl, description));
	}

	/** Boards send ids as strings or numbers; both become trimmed text. Blank means absent. */
	private static String text(Object value) {
		if (value == null || value instanceof Collection<?> || value instanceof Map<?, ?>) {
			return null;
		}
		String text = value.toString().strip();
		return text.isEmpty() ? null : text;
	}

	/** Some boards give a list of locations; we keep them joined. */
	private static String locationText(Object value) {
		if (value instanceof Collection<?> values) {
			String joined = values.stream()
					.filter(v -> v != null && !v.toString().isBlank())
					.map(v -> v.toString().strip())
					.distinct()
					.collect(Collectors.joining(", "));
			return joined.isEmpty() ? null : joined;
		}
		return text(value);
	}

	private static boolean remote(Object flag, String title, String location) {
		if (flag instanceof Boolean bool) {
			return bool;
		}
		String text = text(flag);
		if (text != null) {
			String lower = text.toLowerCase(Locale.ROOT);
			if (lower.equals("true") || lower.equals("yes") || lower.equals("1") || REMOTE_WORD.matcher(lower).find()) {
				return true;
			}
			if (lower.equals("false") || lower.equals("no") || lower.equals("0") || lower.equals("onsite")) {
				return false;
			}
		}
		String hint = ((title == null ? "" : title) + " " + (location == null ? "" : location)).toLowerCase(Locale.ROOT);
		return REMOTE_WORD.matcher(hint).find();
	}

	private static String currency(Object value) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		String upper = text.toUpperCase(Locale.ROOT);
		return upper.matches("[A-Z]{3}") ? upper : null;
	}

	/** Only absolute http(s) links are worth showing a candidate. */
	private static String httpUrl(String value) {
		if (value == null || value.length() > MAX_URL) {
			return null;
		}
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
				return value;
			}
		}
		catch (URISyntaxException ignored) {
			// fall through
		}
		return null;
	}

	static String htmlToText(String html) {
		if (html == null) {
			return null;
		}
		String text = SCRIPT_OR_STYLE.matcher(html).replaceAll(" ");
		text = LIST_ITEM.matcher(text).replaceAll("\n- ");
		text = BLOCK_TAGS.matcher(text).replaceAll("\n");
		text = ANY_TAG.matcher(text).replaceAll(" ");
		text = HtmlUtils.htmlUnescape(text).replace(' ', ' ');
		text = text.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").strip();
		return text.isEmpty() ? null : text;
	}

	private static String singleLine(String text) {
		return text == null ? null : text.replaceAll("\\s+", " ").strip();
	}

	private static String truncate(String text, int max) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		if (text.length() <= max) {
			return text;
		}
		int end = max;
		if (Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end).strip();
	}

}
