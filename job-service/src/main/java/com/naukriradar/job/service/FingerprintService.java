package com.naukriradar.job.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import com.naukriradar.job.normalizer.NormalizedJob;
import org.springframework.stereotype.Component;

/**
 * Gives the same posting the same key on every board: SHA-256 of normalised title, company
 * and city. Normalising removes what boards add on their own: gender tags like "(m/w/d)",
 * legal suffixes like "GmbH" or "Pvt Ltd", accents, punctuation and case.
 */
@Component
public class FingerprintService {

	private static final Pattern GENDER_TAG = Pattern.compile(
			"\\(\\s*(?:[mwfdxh]\\s*[/|,]\\s*)+[mwfdxh]\\s*\\)|\\(\\s*all genders?\\s*\\)|\\*in\\b");

	private static final Pattern LEGAL_SUFFIX = Pattern.compile(
			"\\b(?:gmbh(?: & co\\.? kg)?|ag|se|kg|ug|ltd|limited|pvt|private|llc|llp|inc|corp|corporation|co|plc|bv|sa|sas|srl)\\b\\.?");

	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

	public String fingerprint(NormalizedJob job) {
		String key = clean(GENDER_TAG.matcher(lower(job.title())).replaceAll(" ")) + "|"
				+ clean(LEGAL_SUFFIX.matcher(lower(job.company())).replaceAll(" ")) + "|"
				+ city(job);
		return sha256(key);
	}

	/** First part of the location ("Pune, Maharashtra" is Pune); "remote" when there is none. */
	static String city(NormalizedJob job) {
		String location = job.location();
		if (location == null || location.isBlank()) {
			return job.remote() ? "remote" : "";
		}
		String first = location.split("[,;/|]", 2)[0];
		return clean(lower(first));
	}

	private static String lower(String value) {
		if (value == null) {
			return "";
		}
		String noAccents = ACCENTS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
		return noAccents.toLowerCase(Locale.ROOT);
	}

	private static String clean(String value) {
		return NON_WORD.matcher(value).replaceAll(" ").strip();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available on the JVM", ex);
		}
	}

}
