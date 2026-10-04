package com.naukriradar.core.service;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.core.client.AiReply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.CoverLetterResponse;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a cover letter for one application from the candidate's profile and the job. Uses
 * each provider's strong model (writing is where quality shows), and the same letter is
 * served from cache until the candidate asks for a new one.
 */
@Service
public class CoverLetterService {

	static final String PROMPT = "cover-letter";

	private final ApplicationRepository applications;
	private final ProfileService profiles;
	private final MatchingClient ai;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public CoverLetterService(ApplicationRepository applications, ProfileService profiles, MatchingClient ai,
			PlatformTransactionManager transactionManager) {
		this.applications = applications;
		this.profiles = profiles;
		this.ai = ai;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * @param fresh write a new one instead of the cached letter
	 * @throws ServiceUnavailableException when AI can't write right now
	 */
	public CoverLetterResponse write(String userId, String applicationId, boolean fresh) {
		Application application = transaction.execute(status -> load(userId, applicationId));
		String profile = describe(profiles.getProfile(userId), profiles.getSkills(userId).stream()
				.map(SkillResponse::name).collect(Collectors.joining(", ")));
		String job = "Title: " + application.getJobTitle() + "\nCompany: " + application.getJobCompany()
				+ (application.getJobLocation() == null ? "" : "\nLocation: " + application.getJobLocation());

		AiReply reply = ai.ai(PROMPT, Map.of("profile", profile, "job", job), userId, fresh);
		if (!reply.answered()) {
			throw new ServiceUnavailableException("No cover letter right now: " + reply.unavailableReason());
		}
		String letter = reply.answer().path("letter").asString();
		var at = clock.instant();
		transaction.executeWithoutResult(status -> load(userId, applicationId).coverLetter(letter, at));
		return new CoverLetterResponse(applicationId, letter, at, reply.answeredBy());
	}

	private Application load(String userId, String applicationId) {
		return applications.findByIdAndUserId(applicationId, userId)
				.orElseThrow(() -> new NotFoundException("No application " + applicationId + "."));
	}

	private static String describe(ProfileResponse p, String skills) {
		StringBuilder text = new StringBuilder();
		line(text, "Name", p.fullName());
		line(text, "Current title", p.currentTitle());
		line(text, "Years of experience", p.experienceYears() == null ? null : p.experienceYears().toString());
		line(text, "Skills", skills);
		line(text, "Looking for", String.join(", ", p.targetRoles()));
		line(text, "Based in", p.location());
		return text.toString().strip();
	}

	private static void line(StringBuilder text, String label, String value) {
		if (value != null && !value.isBlank()) {
			text.append(label).append(": ").append(value).append('\n');
		}
	}

}
