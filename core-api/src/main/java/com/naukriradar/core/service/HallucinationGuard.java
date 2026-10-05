package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukriradar.core.skill.SkillExtractor;
import org.springframework.stereotype.Component;

/**
 * Checks what an AI-written text claims about the candidate against what the candidate
 * actually wrote (resume and profile). It looks for the claims that hurt most when invented:
 * skills, numbers ("5 years", "40%", "2M users") and employers ("at Infosys").
 *
 * <p>Deliberately simple and deterministic: no second AI call (which could be wrong the same
 * way), the same text always gives the same verdict, and every flag says exactly what wasn't
 * found. It errs on the side of flagging; a flag means "check this", not "this is false".
 */
@Component
public class HallucinationGuard {

	/** A number with what it counts: the kind of claim recruiters check. */
	private static final Pattern NUMBER_CLAIM = Pattern.compile(
			"(\\d+(?:[.,]\\d+)?)\\s*\\+?\\s*(years?|yrs?|months?|%|percent|x\\b|k\\b|m\\b|million|lakhs?|crores?|users|customers|"
					+ "clients|engineers|people|members|projects|services|applications|countries|teams)",
			Pattern.CASE_INSENSITIVE);

	/** "at Acme Corp", "joined Initech": a claimed employer, up to four capitalised words. */
	private static final Pattern EMPLOYER_CLAIM = Pattern.compile(
			"\\b(?:at|joined|worked for|working for|employed by)\\s+((?:[A-Z][\\w&.'-]*)(?:\\s+[A-Z][\\w&.'-]*){0,3})");

	private static final Set<String> NOT_EMPLOYERS = Set.of("i", "the", "my", "a", "an", "your", "our", "this", "present",
			"scale", "work", "home", "least", "times", "first", "heart");

	private final SkillExtractor skills;

	public HallucinationGuard(SkillExtractor skills) {
		this.skills = skills;
	}

	/**
	 * @param text what the AI wrote
	 * @param facts everything the candidate gave us: resume text, profile, skills
	 * @param allowed names the text may use though the candidate never wrote them (the target company, the job title)
	 */
	public Check check(String text, String facts, Collection<String> allowed) {
		if (text == null || text.isBlank()) {
			return new Check(List.of());
		}
		String source = facts == null ? "" : facts.toLowerCase(Locale.ROOT);
		Set<String> allowedLower = new LinkedHashSet<>();
		allowed.stream().filter(a -> a != null && !a.isBlank()).forEach(a -> allowedLower.add(a.toLowerCase(Locale.ROOT)));
		Set<String> sourceSkills = skills.extract(facts);
		Set<String> allowedSkills = new LinkedHashSet<>();
		allowed.forEach(a -> allowedSkills.addAll(skills.extract(a)));

		List<String> unsupported = new ArrayList<>();
		for (String skill : skills.extract(text)) {
			if (!sourceSkills.contains(skill) && !allowedSkills.contains(skill)) {
				unsupported.add("skill \"" + skill + "\"");
			}
		}
		Matcher numbers = NUMBER_CLAIM.matcher(text);
		while (numbers.find()) {
			String number = numbers.group(1).replace(",", "");
			if (!Pattern.compile("(?<![\\d.])" + Pattern.quote(number) + "(?![\\d])").matcher(source.replace(",", "")).find()) {
				unsupported.add("number \"" + numbers.group().strip() + "\"");
			}
		}
		Matcher employers = EMPLOYER_CLAIM.matcher(text);
		while (employers.find()) {
			// "at Acme Corp I built...": the pronoun that starts the next clause isn't part of the name
			String name = employers.group(1).strip().replaceAll("[.,;:]+$", "").replaceAll("\\s+(I|We|My|Our)$", "");
			String lower = name.toLowerCase(Locale.ROOT);
			if (NOT_EMPLOYERS.contains(lower.split("\\s+")[0]) || !skills.extract(name).isEmpty()) {
				continue;
			}
			boolean known = source.contains(lower) || allowedLower.stream().anyMatch(a -> a.contains(lower) || lower.contains(a));
			if (!known) {
				unsupported.add("employer \"" + name + "\"");
			}
		}
		return new Check(List.copyOf(new LinkedHashSet<>(unsupported)));
	}

	/** @param unsupported claims not found in what the candidate gave us; empty means grounded */
	public record Check(List<String> unsupported) {

		public boolean grounded() {
			return unsupported.isEmpty();
		}

	}

}
