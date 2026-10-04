package com.naukriradar.core.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.AiReply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.model.Resume;
import com.naukriradar.core.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a resume with AI after upload: skills with years, seniority, roles. Runs in the
 * background, so an upload never waits for (or fails because of) AI; the dictionary
 * extraction done during upload stays as the baseline.
 */
@Service
public class ResumeParsingService {

	private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);

	static final String PROMPT = "resume-parse";

	private static final int MAX_CHARS = 15_000;

	private static final int MAX_SKILL_NAME = 50;

	private final ResumeRepository resumes;
	private final ProfileService profiles;
	private final MatchingClient ai;
	private final JsonMapper json;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public ResumeParsingService(ResumeRepository resumes, ProfileService profiles, MatchingClient ai, JsonMapper json,
			PlatformTransactionManager transactionManager) {
		this.resumes = resumes;
		this.profiles = profiles;
		this.ai = ai;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** @throws NotFoundException if the user has no resume */
	public void parseInBackground(String userId) {
		if (!resumes.existsByUserId(userId)) {
			throw new NotFoundException("Upload a resume first.");
		}
		Thread.ofVirtual().name("resume-parse-" + userId).start(() -> {
			try {
				parse(userId);
			}
			catch (RuntimeException ex) {
				log.warn("AI resume parse failed for {}", userId, ex);
			}
		});
	}

	/** @return whether the AI read the resume */
	public boolean parse(String userId) {
		Optional<String> text = transaction.execute(status -> resumes.findByUserId(userId).map(Resume::getExtractedText));
		if (text == null || text.isEmpty() || text.get().isBlank()) {
			return false;
		}
		String resume = text.get().length() > MAX_CHARS ? text.get().substring(0, MAX_CHARS) : text.get();
		AiReply reply = ai.ai(PROMPT, Map.of("resume", resume), userId, false);
		if (!reply.answered()) {
			log.info("No AI resume parse for {}: {}", userId, reply.unavailableReason());
			return false;
		}
		JsonNode answer = reply.answer();
		Map<String, Integer> skills = new LinkedHashMap<>();
		answer.path("skills").forEach(skill -> {
			String name = skill.path("name").asString().strip().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && name.length() <= MAX_SKILL_NAME) {
				JsonNode years = skill.path("years");
				skills.putIfAbsent(name, years.isNumber() ? years.asInt() : null);
			}
		});
		JsonNode total = answer.path("totalYearsExperience");
		transaction.executeWithoutResult(status -> {
			resumes.findByUserId(userId).ifPresent(r -> r.parsed(json.writeValueAsString(answer), clock.instant()));
			List<String> added = profiles.applyAiResumeFacts(userId, skills, total.isNumber() ? total.asInt() : null);
			log.info("AI read the resume of {}: {} skill(s), {} new on the profile", userId, skills.size(), added.size());
		});
		return true;
	}

}
