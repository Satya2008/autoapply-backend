package com.naukriradar.core.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param localDir where {@code LocalFileStorage} keeps files
 */
@ConfigurationProperties("naukriradar.storage")
public record StorageProperties(@DefaultValue("./data/files") Path localDir) {
}
