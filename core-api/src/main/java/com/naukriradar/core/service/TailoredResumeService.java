package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.AiReply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.TailoredResumeResponse;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Job;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Project;
import com.naukriradar.core.dto.response.TailoredResumeResponse.Resume;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.service.ApplicationContextBuilder.Context;
import com.naukriradar.core.skill.SkillExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The candidate's resume rewritten for one job by AI, under one rule: nothing new. AI only
 * reorders, selects and rewords what the resume says; every claim is then checked against the
 * resume and profile, and a version that adds something gets one retry naming what to leave
 * out. The job's skills the resume doesn't have are listed for the candidate, never added.
 *
 * <p>The result is kept on the application and is the resume uploaded when its form is filled.
 */
@Service
public class TailoredResumeService {

	static final String PROMPT = "tailored-resume";

	private final ApplicationContextBuilder contexts;
	private final MatchingClient ai;
	private final HallucinationGuard guard;
	private final SkillExtractor skills;
	private final ProfileRepository profiles;
	private final JsonMapper json;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public TailoredResumeService(ApplicationContextBuilder contexts, MatchingClient ai, HallucinationGuard guard,
			SkillExtractor skills, ProfileRepository profiles, JsonMapper json, PlatformTransactionManager transactionManager) {
		this.contexts = contexts;
		this.ai = ai;
		this.guard = guard;
		this.skills = skills;
		this.profiles = profiles;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** Writes (or rewrites) the resume for this application's job and keeps it. */
	public TailoredResumeResponse tailor(String userId, String applicationId) {
		Context context = contexts.build(userId, applicationId);
		if (context.resumeText().isBlank()) {
			throw new BusinessRuleException("Upload your resume first: the tailored one is made from it.");
		}
		Map<String, String> variables = new HashMap<>();
		variables.put("resume", context.resumeText());
		variables.put("job", context.jobText());
		variables.put("avoid", "nothing in particular");

		Written written = write(variables, userId, context, true);
		if (!written.unsupported().isEmpty()) {
			// one more try, naming what to leave out; keep whichever claims less that we can't back
			variables.put("avoid", String.join("; ", written.unsupported()));
			Written retry = write(variables, userId, context, true);
			if (retry.unsupported().size() < written.unsupported().size()) {
				written = retry;
			}
		}

		Instant at = clock.instant();
		Stored stored = new Stored(written.resume(), written.unsupported(), written.by());
		String asJson = json.writeValueAsString(stored);
		transaction.executeWithoutResult(status -> contexts.load(userId, applicationId).tailoredResume(asJson, at));
		return response(applicationId, context, stored, at);
	}

	/** The resume kept for this application, or 404 if none was made yet. */
	public TailoredResumeResponse get(String userId, String applicationId) {
		Application application = contexts.load(userId, applicationId);
		if (application.getTailoredResume() == null) {
			throw new NotFoundException("No tailored resume for this application yet.");
		}
		Context context = contexts.build(userId, applicationId);
		return response(applicationId, context, read(application.getTailoredResume()), application.getTailoredResumeAt());
	}

	/** The kept resume as an ATS-friendly PDF; empty if none was made. */
	public Optional<byte[]> pdf(String userId, String applicationId) {
		Application application = contexts.load(userId, applicationId);
		if (application.getTailoredResume() == null) {
			return Optional.empty();
		}
		Stored stored = read(application.getTailoredResume());
		Contact contact = transaction.execute(status -> contact(userId));
		return Optional.of(ResumePdf.render(contact.name(), contact.line(), stored.resume()));
	}

	/** A file name for the PDF: "Asha_Rao_Resume_Acme.pdf". */
	public String fileName(String userId, String applicationId) {
		Application application = contexts.load(userId, applicationId);
		Contact contact = transaction.execute(status -> contact(userId));
		String name = (contact.name() == null || contact.name().isBlank() ? "Resume" : contact.name() + " Resume")
				+ (application.getJobCompany() == null ? "" : " " + application.getJobCompany());
		return name.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "") + ".pdf";
	}

	private Written write(Map<String, String> variables, String userId, Context context, boolean fresh) {
		AiReply reply = ai.ai(PROMPT, variables, userId, fresh);
		if (!reply.answered()) {
			throw new BusinessRuleException("AI couldn't write it right now (" + reply.unavailableReason() + "). Try again in a minute.");
		}
		Resume resume = parse(reply.answer());
		HallucinationGuard.Check check = guard.check(text(resume), context.facts(), List.of());
		return new Written(resume, check.unsupported(), reply.answeredBy());
	}

	private TailoredResumeResponse response(String applicationId, Context context, Stored stored, Instant at) {
		Set<String> wanted = jobSkills(context);
		Set<String> before = covered(wanted, context.resumeText());
		Set<String> after = covered(wanted, text(stored.resume()));
		List<String> missing = wanted.stream().filter(s -> !after.contains(s)).toList();
		return new TailoredResumeResponse(applicationId, stored.resume(), percent(before.size(), wanted.size()),
				percent(after.size(), wanted.size()), List.copyOf(after), missing, stored.unsupported(), stored.by(), at);
	}

	/** The skills the job asks for: those its parse found, and any the description names. */
	Set<String> jobSkills(Context context) {
		Set<String> wanted = new LinkedHashSet<>();
		if (context.posting() != null) {
			wanted.addAll(skills.extract(String.join(", ", context.posting().requiredSkills())));
		}
		wanted.addAll(skills.extract(context.jobText()));
		return wanted;
	}

	private Set<String> covered(Set<String> wanted, String text) {
		Set<String> present = skills.extract(text);
		return wanted.stream().filter(present::contains).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static int percent(int part, int whole) {
		return whole == 0 ? 0 : Math.round(100f * part / whole);
	}

	static String text(Resume resume) {
		return Stream.of(Stream.of(resume.summary()), resume.skills().stream(),
				resume.experience().stream().flatMap(j -> Stream.concat(Stream.of(j.title(), j.company()), j.bullets().stream())),
				resume.projects().stream().flatMap(p -> Stream.concat(Stream.of(p.name()), p.bullets().stream())),
				resume.education().stream())
				.flatMap(s -> s)
				.filter(s -> s != null && !s.isBlank())
				.collect(Collectors.joining("\n"));
	}

	static Resume parse(JsonNode answer) {
		List<Job> experience = new ArrayList<>();
		answer.path("experience").forEach(j -> experience.add(new Job(j.path("title").asString(""), j.path("company").asString(""),
				j.path("dates").asString(""), strings(j.path("bullets")))));
		List<Project> projects = new ArrayList<>();
		answer.path("projects").forEach(p -> projects.add(new Project(p.path("name").asString(""), strings(p.path("bullets")))));
		return new Resume(answer.path("summary").asString(""), strings(answer.path("skills")), experience, projects,
				strings(answer.path("education")));
	}

	private static List<String> strings(JsonNode array) {
		List<String> out = new ArrayList<>();
		array.forEach(item -> {
			String value = item.asString("").strip();
			if (!value.isEmpty()) {
				out.add(value);
			}
		});
		return out;
	}

	private Stored read(String stored) {
		return json.readValue(stored, Stored.class);
	}

	private Contact contact(String userId) {
		Profile profile = profiles.findById(userId).orElseThrow(() -> new NotFoundException("No profile."));
		String line = Stream.of(profile.getUser().getEmail(), profile.getPhone(), profile.getLocation(), profile.getLinkedinUrl(),
				profile.getGithubUrl(), profile.getPortfolioUrl())
				.filter(s -> s != null && !s.isBlank())
				.map(s -> s.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", ""))
				.collect(Collectors.joining("  |  "));
		return new Contact(profile.getFullName(), line);
	}

	/** What is kept on the application. */
	record Stored(Resume resume, List<String> unsupported, String by) {
	}

	private record Written(Resume resume, List<String> unsupported, String by) {
	}

	private record Contact(String name, String line) {
	}

}
