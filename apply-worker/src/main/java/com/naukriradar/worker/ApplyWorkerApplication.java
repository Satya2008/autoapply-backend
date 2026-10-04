package com.naukriradar.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApplyWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApplyWorkerApplication.class, args);
	}

}
