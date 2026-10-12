package com.naukriradar.matching.scoring;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;

/**
 * The candidate's hard rules on which jobs to see at all (their profile's job filters): what
 * the job must be about, where it may be, and how senior it may be. Scoring only ranks; a
 * German "(m/w/d)" posting or a Principal role scored high on skills alone before these.
 */
final class JobRules {

	/** Indian cities and states, as postings write them; longest first, so "new delhi" goes before "delhi". */
	static final List<String> INDIA_PLACES = Arrays.stream("""
			india, bengaluru, bangalore, mumbai, bombay, navi mumbai, thane, pune, hyderabad, secunderabad, chennai, madras,
			new delhi, delhi, delhi ncr, ncr, gurugram, gurgaon, greater noida, noida, faridabad, ghaziabad, kolkata, calcutta,
			ahmedabad, gandhinagar, jaipur, kochi, cochin, chandigarh, mohali, indore, coimbatore, thiruvananthapuram,
			trivandrum, mysore, mysuru, nagpur, lucknow, bhubaneswar, vadodara, baroda, visakhapatnam, vizag, surat, goa,
			mangalore, mangaluru, bhopal, patna, ranchi, dehradun, nashik, kanpur, varanasi, guwahati, vijayawada, madurai,
			karnataka, maharashtra, telangana, tamil nadu, haryana, uttar pradesh, kerala, gujarat, rajasthan, west bengal,
			odisha, punjab, madhya pradesh, andhra pradesh, bihar, jharkhand, assam, uttarakhand
			""".split(","))
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.sorted((a, b) -> b.length() - a.length())
			.toList();

	/** Words a remote job's location may have without naming a place outside India. */
	private static final Set<String> NOT_A_PLACE = Set.of("remote", "anywhere", "worldwide", "global", "globally", "work",
			"from", "home", "wfh", "hybrid", "fully", "only", "distributed", "apac", "asia", "pacific", "south", "and", "or",
			"in", "any", "location", "locations", "multiple", "various", "first", "based", "friendly", "ist", "timezone",
			"time", "zone", "office", "on", "site", "onsite", "the", "of", "within", "across");

	/** "(m/w/d)", "(f/m/d)", "(h/f)", "(all genders)": how German, Austrian, Swiss and French postings mark a title. */
	private static final Pattern EUROPEAN_TITLE = Pattern.compile(
			"\\((?:[mwfhdx]\\s*/\\s*){1,3}[mwfhdx]\\)|\\(all genders\\)|\\(gn\\)|\\(m/w/x\\)", Pattern.CASE_INSENSITIVE);

	private static final List<String> ABOVE_SENIOR = List.of("staff", "principal", "lead", "architect", "manager",
			"director", "head", "vp", "vice president", "chief", "distinguished", "fellow");

	private static final List<String> SENIOR = List.of("senior", "sr");

	/** "Senior/" or "(Senior)" before a title: the role is open at either level. */
	private static final Pattern EITHER_SENIOR = Pattern.compile("\\((?:senior|sr\\.?)\\)|\\b(?:senior|sr\\.?)\\s*/\\s*(?=[a-z])",
			Pattern.CASE_INSENSITIVE);

	/** Third level and up ("SDE 3", "Software Engineer III"): senior by another name. */
	private static final Pattern LEVEL_THREE_UP = Pattern.compile(
			"\\b(?:sde|engineer|developer|swe)\\s*[-]?\\s*(?:iii|iv|v|3|4|5)\\b", Pattern.CASE_INSENSITIVE);

	private final List<String> mustHave;
	private final boolean indiaOnly;
	private final String level;
	private final Integer experienceYears;

	JobRules(MatchingProfile profile) {
		MatchingProfile.Filters filters = profile.filters();
		this.mustHave = filters.mustHave().stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).toList();
		this.indiaOnly = "INDIA".equals(filters.region());
		this.level = filters.level();
		this.experienceYears = profile.experienceYears();
	}

	/** Why the job breaks a rule, or null if it keeps them all. */
	String reason(CandidateJob job) {
		String title = lower(job.title());
		if (!mustHave.isEmpty() && !about(job, title)) {
			return "not about " + String.join(" or ", mustHave);
		}
		if (indiaOnly && !openToIndia(job, title)) {
			return "not in India";
		}
		String tooSenior = tooSenior(job, title);
		if (tooSenior != null) {
			return "too senior (" + tooSenior + ")";
		}
		return null;
	}

	/** In the title or the required skills, or said more than once in the description. */
	private boolean about(CandidateJob job, String title) {
		String description = lower(job.description());
		for (String term : mustHave) {
			if (TextMatcher.containsWord(title, term)) {
				return true;
			}
			if (job.requiredSkills().stream().anyMatch(s -> TextMatcher.containsWord(lower(s), term))) {
				return true;
			}
			if (occurrences(description, term) >= 2) {
				return true;
			}
		}
		return false;
	}

	static boolean openToIndia(CandidateJob job, String title) {
		if (EUROPEAN_TITLE.matcher(title).find()) {
			return false;
		}
		String location = lower(job.location());
		String rest = location;
		boolean india = false;
		for (String place : INDIA_PLACES) {
			if (TextMatcher.containsWord(rest, place)) {
				india = true;
				rest = rest.replace(place, " ");
			}
		}
		if (india) {
			return true;
		}
		boolean remote = job.remote() || TextMatcher.containsWord(location, "remote") || location.contains("anywhere")
				|| location.contains("work from home");
		if (!remote) {
			return false;
		}
		// remote, and naming no country or city: open to anyone, India included
		return Arrays.stream(rest.split("[^\\p{L}]+")).filter(w -> !w.isEmpty()).allMatch(NOT_A_PLACE::contains);
	}

	private String tooSenior(CandidateJob job, String fullTitle) {
		if ("ANY".equals(level)) {
			return null;
		}
		// "Senior/Software Engineer", "(Senior) Software Engineer": open to the level below too
		String title = EITHER_SENIOR.matcher(fullTitle).replaceAll(" ");
		for (String word : ABOVE_SENIOR) {
			if (TextMatcher.containsWord(title, word)) {
				return word;
			}
		}
		if ("JUNIOR_MID".equals(level)) {
			for (String word : SENIOR) {
				if (TextMatcher.containsWord(title, word)) {
					return "senior";
				}
			}
			if (LEVEL_THREE_UP.matcher(title).find()) {
				return "level 3 or above";
			}
			// the posting itself asks for far more years than the candidate has
			int most = experienceYears == null ? 5 : Math.max(experienceYears + 2, 4);
			if (job.minYearsExperience() != null && job.minYearsExperience() > most) {
				return job.minYearsExperience() + "+ years";
			}
		}
		return null;
	}

	private static int occurrences(String text, String term) {
		int count = 0;
		java.util.regex.Matcher m = TextMatcher.wordPattern(term).matcher(text);
		while (m.find() && count < 2) {
			count++;
		}
		return count;
	}

	private static String lower(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT);
	}

}
