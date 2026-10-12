package com.naukriradar.job.provider;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.naukriradar.job.config.JobsProperties;
import com.naukriradar.job.exception.JobSourceFetchException;
import org.springframework.stereotype.Component;

/**
 * Reads a site's robots.txt before any of its pages is scraped, and refuses pages it disallows.
 * Follows the usual rules: the group for our user agent if there is one, else the one for *;
 * the longest matching rule wins, Allow wins a tie; * and $ work as wildcards. A missing
 * robots.txt (4xx) allows everything; one we can't read (5xx, timeout) allows nothing for now.
 * Each host's rules are kept for an hour.
 */
@Component
public class RobotsTxtPolicy {

	private static final Duration KEEP = Duration.ofHours(1);

	private final PageFetcher fetcher;
	private final String agentToken;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();
	private final Clock clock = Clock.systemUTC();

	public RobotsTxtPolicy(PageFetcher fetcher, JobsProperties properties) {
		this.fetcher = fetcher;
		// "NaukriRadar/1.0 (+job aggregation)" -> "naukriradar"
		this.agentToken = properties.userAgent().split("[/ ]", 2)[0].toLowerCase(Locale.ROOT);
	}

	/** Throws when robots.txt doesn't let us read this URL. */
	public void check(URI uri, int timeoutSeconds) {
		Rules rules = rulesFor(uri, timeoutSeconds);
		String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
				+ (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
		if (!rules.allows(path)) {
			throw new JobSourceFetchException("robots.txt of " + uri.getHost() + " doesn't allow reading " + path
					+ ", so this page is not scraped.");
		}
	}

	private Rules rulesFor(URI uri, int timeoutSeconds) {
		String origin = uri.getScheme() + "://" + uri.getRawAuthority();
		Cached cached = cache.get(origin);
		if (cached != null && cached.until().isAfter(clock.instant())) {
			return cached.rules();
		}
		Rules rules;
		PageFetcher.Page page = fetcher.get(URI.create(origin + "/robots.txt"), "text/plain", Map.of(), timeoutSeconds);
		if (page.ok()) {
			rules = parse(page.body(), agentToken);
		}
		else if (page.status() >= 400 && page.status() < 500) {
			rules = Rules.ALLOW_ALL;
		}
		else {
			throw new JobSourceFetchException("Could not read robots.txt of " + uri.getHost() + " (HTTP " + page.status()
					+ "); not scraping until it can be read.");
		}
		cache.put(origin, new Cached(rules, clock.instant().plus(KEEP)));
		return rules;
	}

	static Rules parse(String robots, String agentToken) {
		List<Rule> ours = null;
		List<Rule> star = null;
		List<String> groupAgents = new ArrayList<>();
		List<Rule> groupRules = new ArrayList<>();
		boolean inRules = false;
		for (String raw : robots.split("\\r?\\n")) {
			String line = raw.replaceFirst("#.*", "").strip();
			int colon = line.indexOf(':');
			if (colon < 0) {
				continue;
			}
			String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).strip();
			if (key.equals("user-agent")) {
				if (inRules) {
					// a user-agent after rules starts a new group
					ours = pick(groupAgents, groupRules, agentToken, ours, true);
					star = pick(groupAgents, groupRules, agentToken, star, false);
					groupAgents = new ArrayList<>();
					groupRules = new ArrayList<>();
					inRules = false;
				}
				groupAgents.add(value.toLowerCase(Locale.ROOT));
			}
			else if (key.equals("allow") || key.equals("disallow")) {
				inRules = true;
				if (!value.isEmpty()) {
					groupRules.add(new Rule(key.equals("allow"), value));
				}
			}
		}
		ours = pick(groupAgents, groupRules, agentToken, ours, true);
		star = pick(groupAgents, groupRules, agentToken, star, false);
		List<Rule> chosen = ours != null ? ours : star;
		return chosen == null ? Rules.ALLOW_ALL : new Rules(chosen);
	}

	private static List<Rule> pick(List<String> agents, List<Rule> rules, String token, List<Rule> current, boolean ours) {
		boolean matches = ours ? agents.stream().anyMatch(a -> !a.isEmpty() && !a.equals("*") && token.contains(a))
				: agents.contains("*");
		if (!matches) {
			return current;
		}
		List<Rule> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
		merged.addAll(rules);
		return merged;
	}

	record Rule(boolean allow, String path, Pattern pattern) {

		Rule(boolean allow, String path) {
			this(allow, path, compile(path));
		}

		private static Pattern compile(String path) {
			StringBuilder regex = new StringBuilder("^");
			for (char c : path.toCharArray()) {
				if (c == '*') {
					regex.append(".*");
				}
				else if (c == '$') {
					regex.append('$');
				}
				else {
					regex.append(Pattern.quote(String.valueOf(c)));
				}
			}
			return Pattern.compile(regex.toString());
		}

	}

	record Rules(List<Rule> rules) {

		static final Rules ALLOW_ALL = new Rules(List.of());

		boolean allows(String path) {
			Rule best = null;
			for (Rule rule : rules) {
				if (rule.pattern().matcher(path).find()
						&& (best == null || rule.path().length() > best.path().length()
								|| (rule.path().length() == best.path().length() && rule.allow()))) {
					best = rule;
				}
			}
			return best == null || best.allow();
		}

	}

	private record Cached(Rules rules, Instant until) {
	}

}
