package com.naukriradar.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

/**
 * @param maxSize largest resume we accept
 * @param maxPages PDF pages read for text; a resume rarely needs more
 * @param maxTextChars extracted text is cut to this length before it is stored
 * @param skillDictionary list of known skills and their aliases
 */
@ConfigurationProperties("naukriradar.resume")
public record ResumeProperties(
		@DefaultValue("10MB") DataSize maxSize,
		@DefaultValue("20") int maxPages,
		@DefaultValue("200000") int maxTextChars,
		@DefaultValue("classpath:skills/dictionary.txt") Resource skillDictionary) {
}
