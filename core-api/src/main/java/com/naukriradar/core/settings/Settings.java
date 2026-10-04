package com.naukriradar.core.settings;

import java.time.Duration;
import java.util.List;

/**
 * Read access to runtime settings. Code depends on this, not on where values live, so tests
 * can hand in fixed values.
 */
public interface Settings {

	String getString(String key);

	int getInt(String key);

	boolean getBoolean(String key);

	Duration getDuration(String key);

	List<String> getDomains(String key);

}
