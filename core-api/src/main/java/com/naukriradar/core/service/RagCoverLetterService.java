package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.AiReply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.CoverLetterResponse;
import com.naukriradar.core.service.ApplicationContextBuilder.Context;
import com.naukriradar.core.service.ResumeEvidenceService.Evidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cover letters built on the candidate's own resume (retrieval-augmented):
 * <ol>
 * <li>retrieve the two or three resume chunks that fit this job best</li>
 * <li>have AI write from those, the profile and the job posting</li>
 * <li>check every claim of the letter against the resume and profile; if something isn't
 * there, ask once more, naming what to leave out</li>
 * </ol>
 * A specific letter instead of a generic one, from fewer tokens than the whole resume.
 *
 * <p>Without AI (off, budget spent, every provider down) the user still gets a letter: a plain
 * draft from a template, filled only with their own facts, marked as a draft.
 */
@Service
public class RagCoverLetterService {

	static final String PROMPT = "cover-letter-rag";

	static final int EVIDENCE_CHUNKS = 3;

	private final ApplicationContextBuilder contexts;
	private final ResumeEvidenceService evidence;
	private final MatchingClient ai;
	private final HallucinationGuard guard;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public RagCoverLetterService(ApplicationContextBuilder contexts, ResumeEvidenceService evidence, MatchingClient ai,
			HallucinationGuard guard, PlatformTransactionManager transactionManager) {
		this.contexts = contexts;
		this.evidence = evidence;
		this.ai = ai;
		this.guard = guard;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * @param fresh write a new one instead of the cached letter
	 * @throws NotFoundException if the user has no such application
	 */
	public CoverLetterResponse write(String userId, String applicationId, boolean fresh) {
		Context context = contexts.build(userId, applicationId);
		List<Evidence> chunks = evidence.evidence(userId, context.retrievalQuery(), EVIDENCE_CHUNKS);
		List<String> allowed = List.of(context.company() == null ? "" : context.company(), context.title() == null ? "" : context.title(),
				context.jobText());

		Map<String, String> variables = new HashMap<>();
		variables.put("profile", context.profileText());
		variables.put("evidence", Evidence.format(chunks));
		variables.put("job", context.jobText());
		variables.put("avoid", "nothing in particular");

		Written written;
		AiReply reply = ai.ai(PROMPT, variables, userId, fresh);
		if (reply.answered()) {
			String letter = reply.answer().path("letter").asString();
			HallucinationGuard.Check check = guard.check(letter, context.facts(), allowed);
			written = new Written(letter, reply.answeredBy(), false, check.unsupported(), null);
			if (!check.grounded()) {
				// one more try, naming what to leave out; keep whichever letter claims less that we can't back
				variables.put("avoid", String.join("; ", check.unsupported()));
				AiReply retry = ai.ai(PROMPT, variables, userId, true);
				if (retry.answered()) {
					String second = retry.answer().path("letter").asString();
					HallucinationGuard.Check recheck = guard.check(second, context.facts(), allowed);
					if (recheck.unsupported().size() < check.unsupported().size()) {
						written = new Written(second, retry.answeredBy(), false, recheck.unsupported(), null);
					}
				}
			}
		}
		else {
			written = new Written(template(context, chunks), "template", true, List.of(),
					"Written without AI: " + reply.unavailableReason());
		}

		Instant at = clock.instant();
		String letter = written.letter();
		transaction.executeWithoutResult(status -> contexts.load(userId, applicationId).coverLetter(letter, at));
		return new CoverLetterResponse(applicationId, letter, at, written.by(), written.draft(),
				written.unsupported().isEmpty(), written.unsupported(),
				chunks.stream().map(c -> c.section().name().toLowerCase(Locale.ROOT)).distinct().toList(), written.note());
	}

	/** A plain letter from the candidate's own facts only, so it can't claim anything untrue. */
	static String template(Context context, List<Evidence> chunks) {
		String company = context.company() == null || context.company().isBlank() ? "your company" : context.company();
		StringBuilder letter = new StringBuilder();
		letter.append("Dear Hiring Team").append(company.equals("your company") ? "" : " at " + company).append(",\n\n");
		letter.append("I am writing to apply for the ").append(context.title()).append(" role.");
		List<String> skills = relevantSkills(context);
		Integer years = context.profile().experienceYears();
		if (years != null && years > 0) {
			letter.append(" I bring ").append(years).append(years == 1 ? " year" : " years").append(" of experience");
			letter.append(skills.isEmpty() ? "." : " with " + join(skills) + ".");
		}
		else if (!skills.isEmpty()) {
			letter.append(" I work with ").append(join(skills)).append('.');
		}
		List<String> highlights = chunks.stream().limit(2).map(c -> firstSentence(c.text())).filter(s -> !s.isBlank()).toList();
		if (!highlights.isEmpty()) {
			letter.append("\n\nSome of my relevant work:");
			highlights.forEach(h -> letter.append("\n- ").append(h));
		}
		letter.append("\n\nI would welcome the chance to discuss how I can contribute to ").append(company).append(".\n\nRegards,");
		String name = context.profile().fullName();
		if (name != null && !name.isBlank()) {
			letter.append('\n').append(name.strip());
		}
		return letter.toString();
	}

	/** The candidate's skills the job asks for first, then the rest; five at most. */
	private static List<String> relevantSkills(Context context) {
		List<String> wanted = context.posting() == null ? List.of()
				: context.posting().requiredSkills().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
		List<String> mine = context.skillNames();
		List<String> ordered = new ArrayList<>(mine.stream().filter(s -> wanted.contains(s.toLowerCase(Locale.ROOT))).toList());
		mine.stream().filter(s -> !ordered.contains(s)).forEach(ordered::add);
		return ordered.stream().limit(5).toList();
	}

	private static String join(List<String> items) {
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
	}

	/** The first sentence or line of a chunk, cut to a readable length, without a leading bullet. */
	private static String firstSentence(String text) {
		String line = text.strip().split("\n")[0].replaceFirst("^[\\u2022\\u25AA\\u25CF\\-*\\u2013]\\s*", "");
		int end = line.indexOf(". ");
		String sentence = end > 0 ? line.substring(0, end + 1) : line;
		return sentence.length() > 200 ? sentence.substring(0, 197) + "..." : sentence;
	}

	private record Written(String letter, String by, boolean draft, List<String> unsupported, String note) {
	}

}
