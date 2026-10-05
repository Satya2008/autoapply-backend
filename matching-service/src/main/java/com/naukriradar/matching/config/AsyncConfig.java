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

	public static final String EVAL_EXECUTOR = "evalExecutor";

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

	/**
	 * One eval at a time, plus two waiting: an eval may make dozens of AI calls, and two running
	 * side by side would only race for the same providers and budget.
	 */
	@Bean(name = EVAL_EXECUTOR)
	ThreadPoolTaskExecutor evalExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(2);
		executor.setThreadNamePrefix("eval-");
		return executor;
	}

}
