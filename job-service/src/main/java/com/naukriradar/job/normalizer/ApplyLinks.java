package com.naukriradar.job.normalizer;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Picks the link to apply on when a job has several (an aggregator lists the company's own page,
 * LinkedIn and a few job boards). A company's own form can be filled for the candidate; a job
 * board wants a login first; LinkedIn bans automation. So: application systems, then company
 * sites, then job boards, then LinkedIn.
 */
final class ApplyLinks {

	/** Application systems whose forms open without an account. */
	private static final List<String> ATS = List.of("greenhouse.io", "lever.co", "ashbyhq.com", "workable.com",
			"smartrecruiters.com", "recruitee.com", "bamboohr.com", "zohorecruit.com", "zohorecruit.in", "keka.com",
			"darwinbox.in", "freshteam.com", "jobvite.com", "breezy.hr", "teamtailor.com", "personio.de", "personio.com");

	/** Boards that show the form only after a login. */
	private static final List<String> BOARDS = List.of("shine.com", "apna.co", "foundit.in", "monsterindia.com",
			"timesjobs.com", "jobrapido.com", "bebee.com", "glassdoor.co.in", "glassdoor.com", "indeed.com", "naukri.com",
			"instahyre.com", "iimjobs.com", "hirist.tech", "hirist.com", "cutshort.io", "wellfound.com", "jobsora.com",
			"talent.com", "efinancialcareers.com", "efinancialcareers.co.in", "simplyhired.com", "ziprecruiter.com");

	private ApplyLinks() {
	}

	/** The best of {@code main} and {@code options}; options that aren't http(s) links are ignored. */
	static String best(String main, Object options) {
		String best = main;
		int bestRank = rank(main);
		if (options instanceof Collection<?> links) {
			for (String link : links.stream().filter(Objects::nonNull).map(Object::toString).toList()) {
				int rank = rank(link);
				if (rank < bestRank) {
					best = link;
					bestRank = rank;
				}
			}
		}
		return best;
	}

	/** 0 application system, 1 company site, 2 job board, 3 LinkedIn, 9 not a link. */
	static int rank(String link) {
		String host = host(link);
		if (host == null) {
			return 9;
		}
		if (covers(host, "linkedin.com")) {
			return 3;
		}
		// Workday, Taleo and the like want an account first, so they rank with company sites
		if (ATS.stream().anyMatch(d -> covers(host, d))) {
			return 0;
		}
		return BOARDS.stream().anyMatch(d -> covers(host, d)) ? 2 : 1;
	}

	private static boolean covers(String host, String domain) {
		return host.equals(domain) || host.endsWith("." + domain);
	}

	private static String host(String link) {
		if (link == null) {
			return null;
		}
		try {
			URI uri = URI.create(link.strip());
			String scheme = uri.getScheme();
			if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
				return null;
			}
			return uri.getHost().toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
