package com.naukriradar.job.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukriradar.job.exception.JobSourceFetchException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fills {@code ${setting:key}} in header values from the {@code naukriradar.settings.key}
 * property (env var {@code NAUKRIRADAR_SETTINGS_KEY}), so API keys stay out of the database.
 */
@Component
@RequiredArgsConstructor
public class SettingPlaceholderResolver {

	public static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{setting:([a-zA-Z0-9._-]+)}");

	private final Environment environment;

	public String resolve(String value) {
		Matcher matcher = PLACEHOLDER.matcher(value);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1);
			String setting = environment.getProperty("naukriradar.settings." + key);
			if (setting == null || setting.isBlank()) {
				// never send the literal placeholder to a third party
				throw new JobSourceFetchException("Setting '" + key + "' is not set.");
			}
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(setting));
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

}
