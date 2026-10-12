package com.naukriradar.job.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a typed location into what to look for in job locations.
 * <ul>
 * <li>Words match from their start: "ind" finds "Indore" and "India" but not "Minden".</li>
 * <li>Known names expand: "India" means any Indian city or state (boards often write
 * "Mumbai, Maharashtra" with no country), "Bangalore" also finds "Bengaluru", "Delhi NCR"
 * finds Noida and Gurugram.</li>
 * <li>"Remote" also matches jobs flagged remote whatever their location says.</li>
 * </ul>
 */
public final class LocationTerms {

	private static final List<String> INDIA = List.of("india", "bengaluru", "bangalore", "mumbai", "bombay", "delhi", "new delhi",
			"noida", "greater noida", "gurugram", "gurgaon", "ghaziabad", "faridabad", "hyderabad", "secunderabad", "pune", "chennai",
			"madras", "kolkata", "calcutta", "ahmedabad", "gandhinagar", "jaipur", "kochi", "cochin", "chandigarh", "mohali", "indore",
			"bhopal", "lucknow", "kanpur", "varanasi", "patna", "ranchi", "coimbatore", "madurai", "thiruvananthapuram", "trivandrum",
			"mysuru", "mysore", "mangaluru", "mangalore", "nagpur", "nashik", "surat", "vadodara", "bhubaneswar", "visakhapatnam",
			"vizag", "vijayawada", "dehradun", "goa", "navi mumbai", "thane", "karnataka", "maharashtra", "telangana", "tamil nadu",
			"haryana", "uttar pradesh", "kerala", "gujarat", "west bengal", "rajasthan", "andhra pradesh", "madhya pradesh", "punjab",
			"odisha", "bihar", "jharkhand", "uttarakhand");

	private static final List<String> NCR = List.of("delhi", "new delhi", "noida", "greater noida", "gurugram", "gurgaon",
			"ghaziabad", "faridabad");

	static final List<String> REMOTE = List.of("remote", "work from home", "wfh", "anywhere", "homeoffice", "home office");

	/** name typed (lower case) -> what it stands for. Also the names offered as suggestions. */
	static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

	static {
		ALIASES.put("india", INDIA);
		ALIASES.put("delhi ncr", NCR);
		ALIASES.put("ncr", NCR);
		ALIASES.put("remote", REMOTE);
		pair("bengaluru", "bangalore", "blr");
		pair("gurugram", "gurgaon");
		pair("mumbai", "bombay");
		pair("chennai", "madras");
		pair("kolkata", "calcutta");
		pair("thiruvananthapuram", "trivandrum");
		pair("mysuru", "mysore");
		pair("mangaluru", "mangalore");
		pair("kochi", "cochin");
		pair("hyderabad", "secunderabad");
		pair("visakhapatnam", "vizag");
	}

	private static void pair(String... names) {
		for (String name : names) {
			ALIASES.put(name, List.of(names));
		}
	}

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private final String typed;
	private final List<String> terms;

	private LocationTerms(String typed, List<String> terms) {
		this.typed = typed;
		this.terms = terms;
	}

	/** Null when nothing usable was typed. */
	public static LocationTerms parse(String input) {
		if (input == null) {
			return null;
		}
		String typed = SPACES.matcher(input.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} .-]", " ")).replaceAll(" ").strip();
		if (typed.isEmpty()) {
			return null;
		}
		// a known name, else what was typed plus the places it starts the other name of ("gurg" -> Gurugram)
		List<String> terms = ALIASES.containsKey(typed) ? ALIASES.get(typed) : typedPrefixes(typed);
		return new LocationTerms(typed, terms);
	}

	private static final Pattern IN_INDIA = Pattern.compile(
			"\\b(" + String.join("|", INDIA) + "|ind)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/**
	 * True for a location in India ("Bengaluru, Karnataka", "Remote - India", "Bangalore, IND").
	 * A source for India keeps only these, so a global company's board brings its Indian roles
	 * and not the rest. A posting with no location is given the benefit of the doubt.
	 */
	public static boolean inIndia(String location) {
		return location == null || location.isBlank() || IN_INDIA.matcher(location).find();
	}

	/** True when the user asked for remote jobs, which also match on the remote flag. */
	public boolean remote() {
		return REMOTE.contains(typed);
	}

	/**
	 * A MySQL (ICU) regular expression that finds any of the terms at the start of a word.
	 * Terms hold only letters, digits, spaces, dots and dashes, and dots are escaped.
	 */
	public String regexp() {
		return "\\b(" + terms.stream().map(t -> t.replace(".", "\\.")).collect(Collectors.joining("|")) + ")";
	}

	/** True when this place name starts with what was typed, word by word ("navi m" -> "Navi Mumbai"). */
	static boolean startsWithTyped(String place, String typed) {
		String lower = place.toLowerCase(Locale.ROOT);
		int at = lower.indexOf(typed);
		while (at >= 0) {
			if (at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1))) {
				return true;
			}
			at = lower.indexOf(typed, at + 1);
		}
		return false;
	}

	public String typed() {
		return typed;
	}

	/**
	 * What to look for while the name is still being typed: the typed text, plus every other
	 * name of a place whose old or new name starts with it ("gurg" -> gurgaon, gurugram).
	 */
	static List<String> typedPrefixes(String typed) {
		Set<String> out = new LinkedHashSet<>();
		out.add(typed);
		ALIASES.forEach((name, terms) -> {
			if (name.startsWith(typed) && terms.size() <= 3) {
				out.addAll(terms);
			}
		});
		return new ArrayList<>(out);
	}

	/** Alias names that start with what was typed, e.g. "ind" -> India. */
	static List<String> matchingAliases(String typed) {
		Set<String> out = new LinkedHashSet<>();
		for (String alias : List.of("india", "remote", "delhi ncr")) {
			if (alias.startsWith(typed)) {
				out.add(alias);
			}
		}
		return new ArrayList<>(out);
	}

}
