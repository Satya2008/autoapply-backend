package com.naukriradar.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.model.PortalConfig;
import com.naukriradar.core.model.RiskBand;
import org.springframework.stereotype.Component;

/**
 * Decides how safe it is to automate applying through a link. Order of precedence: an
 * admin's portal config, then the high-risk list, then the low-risk list, else MEDIUM.
 * A domain covers its subdomains ("lever.co" covers "jobs.lever.co"), but only real ones:
 * "linkedin.com.evil.io" is not LinkedIn, and it is not low risk either.
 */
@Component
public class RiskClassifier {

	private final List<String> highRisk;
	private final List<String> lowRisk;

	public RiskClassifier(ApplicationProperties properties) {
		this.highRisk = properties.highRiskDomains().stream().map(RiskClassifier::normalise).toList();
		this.lowRisk = properties.lowRiskDomains().stream().map(RiskClassifier::normalise).toList();
	}

	public Risk classify(String applyUrl, List<PortalConfig> portals) {
		String host = host(applyUrl);
		if (host == null) {
			// can't tell what site it is, so treat it as the riskiest
			return new Risk(RiskBand.HIGH, "The apply link isn't a usable web address.");
		}
		Optional<PortalConfig> portal = portals.stream()
				.filter(p -> covers(p.getDomain(), host))
				.max(Comparator.comparingInt(p -> p.getDomain().length()));
		if (portal.isPresent()) {
			return new Risk(portal.get().getRiskBand(), portal.get().getName() + " is set to " + portal.get().getRiskBand()
					+ " risk by an admin.");
		}
		for (String domain : highRisk) {
			if (covers(domain, host)) {
				return new Risk(RiskBand.HIGH, domain + " doesn't allow automated applications.");
			}
		}
		for (String domain : lowRisk) {
			if (covers(domain, host)) {
				return new Risk(RiskBand.LOW, domain + " is an applicant tracking system that accepts direct applications.");
			}
		}
		return new Risk(RiskBand.MEDIUM, host + " isn't a known site, so it won't be automated.");
	}

	/** The lower-case host of an http(s) URL, without "www."; null if there is none. */
	static String host(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			URI uri = new URI(url.strip());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
				return null;
			}
			return normalise(uri.getHost());
		}
		catch (URISyntaxException ex) {
			return null;
		}
	}

	static boolean covers(String domain, String host) {
		return host.equals(domain) || host.endsWith("." + domain);
	}

	private static String normalise(String domain) {
		String lower = domain.strip().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".")) {
			lower = lower.substring(0, lower.length() - 1);
		}
		return lower.startsWith("www.") ? lower.substring(4) : lower;
	}

	public record Risk(RiskBand band, String reason) {
	}

}
