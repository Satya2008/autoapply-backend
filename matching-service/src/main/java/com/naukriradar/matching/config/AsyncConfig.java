package com.naukriradar.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A small, bounded pool for match runs. Spring's default async executor would accept
 * unlimited work; under load that means unbounded threads or an unbounded queue. Here a
 * full pool refuses the run straight away, and the caller gets a 503 to retry later.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	public static final String MATCH_EXECUTOR = "matchExecutor";

	@Bean(name = MATCH_EXECUTOR)
	ThreadPoolTaskExecutor matchExecutor(MatchingProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.workerThreads());
		executor.setMaxPoolSize(properties.workerThreads());
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setThreadNamePrefix("match-run-");
		// let running matches finish on shutdown; anything left is closed as interrupted on the next start
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}

}
