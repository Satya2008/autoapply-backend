package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.JobPosting;
import com.naukriradar.core.client.JobServiceClient;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.repository.ApplicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Everything the writing features need about one application, gathered once: the candidate
 * (profile, skills, resume text) and the job (the full posting from job-service when it can be
 * had, else the title and company kept on the application).
 */
@Component
public class ApplicationContextBuilder {

	static final int JOB_TEXT_CHARS = 3000;

	private final ApplicationRepository applications;
	private final ProfileService profiles;
	private final JobServiceClient jobs;
	private final ResumeEvidenceService evidence;
	private final TransactionTemplate transaction;

	public ApplicationContextBuilder(ApplicationRepository applications, ProfileService profiles, JobServiceClient jobs,
			ResumeEvidenceService evidence, PlatformTransactionManager transactionManager) {
		this.applications = applications;
		this.profiles = profiles;
		this.jobs = jobs;
		this.evidence = evidence;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** @throws NotFoundException if the user has no such application */
	public Context build(String userId, String applicationId) {
		Application application = transaction.execute(status -> load(userId, applicationId));
		ProfileResponse profile = profiles.getProfile(userId);
		List<SkillResponse> skills = profiles.getSkills(userId);
		Optional<JobPosting> posting = application.getJobId() == null ? Optional.empty() : jobs.job(application.getJobId());
		return new Context(application, profile, skills, posting.orElse(null), evidence.resumeText(userId).orElse(""));
	}

	public Application load(String userId, String applicationId) {
		return applications.findByIdAndUserId(applicationId, userId)
				.orElseThrow(() -> new NotFoundException("No application " + applicationId + "."));
	}

	/**
	 * @param posting null when job-service couldn't provide it
	 * @param resumeText empty without a resume
	 */
	public record Context(Application application, ProfileResponse profile, List<SkillResponse> skills, JobPosting posting,
			String resumeText) {

		public String company() {
			return posting != null && posting.company() != null ? posting.company() : application.getJobCompany();
		}

		public String title() {
			return posting != null && posting.title() != null ? posting.title() : application.getJobTitle();
		}

		public List<String> skillNames() {
			return skills.stream().map(SkillResponse::name).toList();
		}

		/** The job, as prompts get it. */
		public String jobText() {
			StringBuilder text = new StringBuilder();
			line(text, "Title", title());
			line(text, "Company", company());
			line(text, "Location", posting != null && posting.location() != null ? posting.location() : application.getJobLocation());
			if (posting != null) {
				line(text, "Required skills", String.join(", ", posting.requiredSkills()));
				String description = posting.description() == null ? "" : posting.description();
				line(text, "Description", description.length() > JOB_TEXT_CHARS ? description.substring(0, JOB_TEXT_CHARS) : description);
			}
			return text.toString().strip();
		}

		/** What the retrieval query is made of: the role, its skills, the start of the description. */
		public String retrievalQuery() {
			List<String> parts = new ArrayList<>();
			parts.add(title());
			if (posting != null) {
				parts.add(String.join(", ", posting.requiredSkills()));
				String description = posting.description() == null ? "" : posting.description();
				parts.add(description.length() > 1500 ? description.substring(0, 1500) : description);
			}
			return parts.stream().filter(p -> p != null && !p.isBlank()).collect(Collectors.joining("\n"));
		}

		/** The candidate, as prompts get it; contact details are left out. */
		public String profileText() {
			StringBuilder text = new StringBuilder();
			line(text, "Name", profile.fullName());
			line(text, "Current title", profile.currentTitle());
			line(text, "Years of experience", profile.experienceYears() == null ? null : profile.experienceYears().toString());
			line(text, "Skills", String.join(", ", skillNames()));
			line(text, "Looking for", String.join(", ", profile.targetRoles()));
			line(text, "Based in", profile.location());
			return text.toString().strip();
		}

		/** Everything the candidate told us, to check claims against. */
		public String facts() {
			return profileText() + "\n" + resumeText;
		}

		private static void line(StringBuilder text, String label, String value) {
			if (value != null && !value.isBlank()) {
				text.append(label).append(": ").append(value).append('\n');
			}
		}

	}

}
