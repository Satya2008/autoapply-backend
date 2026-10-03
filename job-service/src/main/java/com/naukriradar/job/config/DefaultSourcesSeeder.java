package com.naukriradar.job.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.repository.JobSourceRepository;
import com.naukriradar.job.service.JobSourceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates the boards listed in {@code sources/default-sources.json} on first start. A source
 * that already exists is left alone, so admin edits are never overwritten.
 */
@Component
@RequiredArgsConstructor
public class DefaultSourcesSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DefaultSourcesSeeder.class);

	private final JobsProperties properties;
	private final JobSourceRepository repository;
	private final JobSourceService service;
	private final JsonMapper jsonMapper;

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (!properties.seedDefaults()) {
			return;
		}
		List<JobSourceRequest> defaults;
		try (InputStream in = new ClassPathResource("sources/default-sources.json").getInputStream()) {
			defaults = jsonMapper.readValue(in, new TypeReference<>() {
			});
		}
		for (JobSourceRequest source : defaults) {
			if (!repository.existsByCode(source.code())) {
				service.create(source);
				log.info("Added default job source {}", source.code());
			}
		}
	}

}
