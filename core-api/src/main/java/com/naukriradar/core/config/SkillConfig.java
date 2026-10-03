package com.naukriradar.core.config;

import com.naukriradar.core.skill.SkillDictionary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillConfig {

	/** Loaded once at startup; a broken dictionary file stops the service from starting. */
	@Bean
	SkillDictionary skillDictionary(ResumeProperties properties) {
		return SkillDictionary.load(properties.skillDictionary());
	}

}
