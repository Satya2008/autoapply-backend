package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.AiReply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.ScreeningAnswersResponse;
import com.naukriradar.core.dto.response.ScreeningAnswersResponse.Answer;
import com.naukriradar.core.dto.response.ScreeningAnswersResponse.Source;
import com.naukriradar.core.service.ApplicationContextBuilder.Context;
import com.naukriradar.core.service.ResumeEvidenceService.Evidence;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Answers an application form's screening questions, only from what the candidate told us:
 * <ol>
 * <li>questions a profile field answers exactly (notice period, years, location...) are
 * answered from the profile: free and certain</li>
 * <li>the rest go to AI in one call, with the resume excerpts retrieved for each question; AI
 * must say "not answerable" rather than guess, and its answers are checked for claims the
 * resume doesn't back</li>
 * <li>what's left is marked for the user, with the excerpts that may help them answer</li>
 * </ol>
 * Without AI, steps 1 and 3 still work.
 */
@Service
public class ScreeningAnswerService {

	static final String PROMPT = "screening-answers";

	static final int EVIDENCE_PER_QUESTION = 2;

	static final int MAX_EVIDENCE = 6;

	private final ApplicationContextBuilder contexts;
	private final ResumeEvidenceService evidence;
	private final ProfileFactsAnswerer facts;
	private final MatchingClient ai;
	private final HallucinationGuard guard;

	public ScreeningAnswerService(ApplicationContextBuilder contexts, ResumeEvidenceService evidence,
			ProfileFactsAnswerer facts, MatchingClient ai, HallucinationGuard guard) {
		this.contexts = contexts;
		this.evidence = evidence;
		this.facts = facts;
		this.ai = ai;
		this.guard = guard;
	}

	/** @throws NotFoundException if the user has no such application */
	public ScreeningAnswersResponse answer(String userId, String applicationId, List<String> questions) {
		Context context = contexts.build(userId, applicationId);
		int n = questions.size();
		String[] answers = new String[n];
		Source[] sources = new Source[n];
		boolean[] check = new boolean[n];
		List<List<Evidence>> excerpts = new ArrayList<>(n);
		List<Integer> forAi = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String question = questions.get(i).strip();
			excerpts.add(evidence.evidence(userId, question + "\n" + context.title(), EVIDENCE_PER_QUESTION));
			Optional<String> fromProfile = facts.answer(question, context.profile(), context.skills());
			if (fromProfile.isPresent()) {
				answers[i] = fromProfile.get();
				sources[i] = Source.PROFILE;
			}
			else {
				sources[i] = Source.NONE;
				forAi.add(i);
			}
		}

		String answeredBy = null;
		String note = null;
		if (!forAi.isEmpty()) {
			AiReply reply = ai.ai(PROMPT, variables(context, questions, forAi, excerpts), userId, false);
			if (reply.answered()) {
				answeredBy = reply.answeredBy();
				for (JsonNode item : reply.answer().path("answers")) {
					int index = item.path("index").asInt(0);
					String text = item.path("answer").isString() ? item.path("answer").asString().strip() : "";
					if (index < 1 || index > forAi.size() || !item.path("answerable").asBoolean(false) || text.isEmpty()) {
						continue;
					}
					int question = forAi.get(index - 1);
					answers[question] = text;
					sources[question] = Source.AI;
					check[question] = !guard.check(text, context.facts(), List.of(context.jobText())).grounded();
				}
			}
			else {
				note = "Answered from your profile only: " + reply.unavailableReason();
			}
		}

		List<Answer> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			List<String> shown = excerpts.get(i).stream()
					.map(e -> "[" + e.section().name().toLowerCase(Locale.ROOT) + "] " + e.text())
					.toList();
			result.add(new Answer(questions.get(i).strip(), answers[i], sources[i], sources[i] == Source.NONE || check[i], shown));
		}
		return new ScreeningAnswersResponse(applicationId, result, answeredBy, note);
	}

	/** One call for all the questions left: numbered, with the excerpts retrieved for any of them. */
	private static Map<String, String> variables(Context context, List<String> questions, List<Integer> forAi,
			List<List<Evidence>> excerpts) {
		Map<String, Evidence> union = new LinkedHashMap<>();
		StringBuilder numbered = new StringBuilder();
		for (int k = 0; k < forAi.size(); k++) {
			int i = forAi.get(k);
			numbered.append(k + 1).append(". ").append(questions.get(i).strip()).append('\n');
			for (Evidence e : excerpts.get(i)) {
				if (union.size() < MAX_EVIDENCE) {
					union.putIfAbsent(e.text(), e);
				}
			}
		}
		Map<String, String> variables = new HashMap<>();
		variables.put("profile", context.profileText());
		variables.put("evidence", Evidence.format(List.copyOf(union.values())));
		variables.put("questions", numbered.toString().strip());
		return variables;
	}

}
