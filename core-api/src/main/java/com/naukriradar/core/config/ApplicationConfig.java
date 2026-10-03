package com.naukriradar.core.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/** Wiring for applications: the client for matching-service and the apply-run worker pool. */
@Configuration
@EnableAsync
@EnableConfigurationProperties({ ApplicationProperties.class, ServicesProperties.class })
public class ApplicationConfig {

	public static final String APPLY_RUN_POOL = "applyRunPool";

	@Bean
	RestClient matchingRestClient(RestClient.Builder builder, ServicesProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.readTimeout());
		return builder.clone().requestFactory(factory).baseUrl(properties.matchingServiceUrl()).build();
	}

	/** Bounded: a full pool refuses new runs (503) instead of piling up threads or queue. */
	@Bean(name = APPLY_RUN_POOL)
	ThreadPoolTaskExecutor applyRunPool(ApplicationProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.workerThreads());
		executor.setMaxPoolSize(properties.workerThreads());
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setThreadNamePrefix("apply-run-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}

}
