package com.naukriradar.worker.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param poolSize browsers open at once; each takes a few hundred MB, so this is what decides
 *     how many applications go out in parallel on one worker
 * @param pageTimeout how long a page may take to load
 * @param fieldTimeout how long to wait for one form field to appear
 * @param successTimeout how long after submitting to wait for the portal's success sign
 * @param chromeBinary path to Chrome when it isn't in the usual place; empty to let Selenium find it
 * @param screenshots where screenshots of failed attempts go
 * @param userProfile the Chrome profile of the window handed to the user ("Fill & Review"): a site
 *     the user logs in to there stays logged in next time
 * @param userDebugPort local port of that window, so later forms open as tabs in it
 * @param humanPace fill forms at a person's pace (pointer moves, letter-by-letter typing, pauses);
 *     off only for tests
 */
@ConfigurationProperties("naukriradar.browser")
public record BrowserProperties(
		@DefaultValue("1") int poolSize,
		@DefaultValue("true") boolean headless,
		@DefaultValue("30s") Duration pageTimeout,
		@DefaultValue("5s") Duration fieldTimeout,
		@DefaultValue("15s") Duration successTimeout,
		String chromeBinary,
		@DefaultValue("./data/screenshots") Path screenshots,
		@DefaultValue("./data/chrome-profile") Path userProfile,
		@DefaultValue("9333") int userDebugPort,
		@DefaultValue("true") boolean humanPace) {
}
