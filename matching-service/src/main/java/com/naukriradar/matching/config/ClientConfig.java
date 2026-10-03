package com.naukriradar.matching.config;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

	@Bean
	JdkClientHttpRequestFactory serviceRequestFactory(ServicesProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.readTimeout());
		return factory;
	}

	@Bean
	@Qualifier("coreApi")
	RestClient coreApiRestClient(RestClient.Builder builder, JdkClientHttpRequestFactory serviceRequestFactory,
			ServicesProperties properties) {
		return builder.clone().requestFactory(serviceRequestFactory).baseUrl(properties.coreApiUrl()).build();
	}

	@Bean
	@Qualifier("jobService")
	RestClient jobServiceRestClient(RestClient.Builder builder, JdkClientHttpRequestFactory serviceRequestFactory,
			ServicesProperties properties) {
		return builder.clone().requestFactory(serviceRequestFactory).baseUrl(properties.jobServiceUrl()).build();
	}

}
