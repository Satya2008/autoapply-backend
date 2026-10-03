package com.naukriradar.core.config;

import com.naukriradar.core.storage.FileStorage;
import com.naukriradar.core.storage.LocalFileStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ StorageProperties.class, ResumeProperties.class })
public class StorageConfig {

	@Bean
	FileStorage fileStorage(StorageProperties properties) {
		return new LocalFileStorage(properties.localDir());
	}

}
