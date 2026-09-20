package com.autoapply.matching.ai;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chooses the configured AI provider, renders a database-stored prompt, calls the model
 * and parses the JSON it returns. Switching vendors is a single settings change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class AiService {

    public static final String PROMPT_JOB_MATCH = "job_match";
    public static final String PROMPT_COVER_LETTER = "cover_letter";
    public static final String PROMPT_RESUME_SUMMARY = "resume_summary";
    public static final String PROMPT_SKILL_GAP = "skill_gap";

    private final List<AiProvider> providers;
    private final PromptTemplateRepository promptRepository;
    private final SettingsService settings;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return settings.getBoolean(SettingKeys.AI_ENABLED, true) && activeProvider().isPresent();
    }

    public Optional<AiProvider> activeProvider() {
        String configured = settings.getString(SettingKeys.AI_PROVIDER, "gemini");
        return providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(configured))
                .filter(AiProvider::isConfigured)
                .findFirst();
    }

    public List<Map<String, Object>> providerStatus() {
        String active = settings.getString(SettingKeys.AI_PROVIDER, "gemini");
        return providers.stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.name(),
                        "configured", p.isConfigured(),
                        "active", p.name().equalsIgnoreCase(active)))
                .toList();
    }

    /** Renders the named prompt with the given variables and returns the model's parsed JSON. */
    public Optional<JsonNode> completeJson(String promptCode, Map<String, String> variables) {
        return complete(promptCode, variables).flatMap(this::parseJson);
    }

    public Optional<String> complete(String promptCode, Map<String, String> variables) {
        if (!settings.getBoolean(SettingKeys.AI_ENABLED, true)) return Optional.empty();

        Optional<AiProvider> provider = activeProvider();
        if (provider.isEmpty()) {
            log.debug("No configured AI provider available for '{}'", promptCode);
            return Optional.empty();
        }

        String prompt = render(promptCode, variables);
        if (prompt == null) return Optional.empty();

        int attempts = Math.max(1, settings.getInt(SettingKeys.AI_MAX_RETRIES, 2) + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return Optional.ofNullable(provider.get().complete(prompt));
            } catch (Exception e) {
                log.warn("AI call '{}' failed (attempt {}/{}): {}", promptCode, attempt, attempts, e.getMessage());
                if (attempt == attempts) return Optional.empty();
                sleep(attempt * 1000L);
            }
        }
        return Optional.empty();
    }

    public String render(String promptCode, Map<String, String> variables) {
        PromptTemplate template = promptRepository.findById(promptCode).orElse(null);
        if (template == null || !Boolean.TRUE.equals(template.getEnabled()) || template.getTemplate() == null) {
            log.warn("Prompt template '{}' is missing or disabled", promptCode);
            return null;
        }
        String rendered = template.getTemplate();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private Optional<JsonNode> parseJson(String raw) {
        try {
            String cleaned = raw.replaceAll("(?s)```(?:json)?", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            return Optional.of(objectMapper.readTree(cleaned));
        } catch (Exception e) {
            log.debug("AI response was not valid JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedPrompts() {
        if (promptRepository.count() > 0) return;

        promptRepository.save(PromptTemplate.builder()
                .code(PROMPT_JOB_MATCH)
                .name("Job match scoring")
                .description("Scores how well a candidate fits a job. Must return JSON.")
                .variables("candidateSkills, candidateExperience, candidateRole, candidateLocation, "
                        + "expectedSalary, jobTitle, jobCompany, jobLocation, jobDescription, jobSalary")
                .template("""
                        You are an experienced technical recruiter. Score how well this candidate fits this role.

                        CANDIDATE
                        - Current role: {{candidateRole}}
                        - Experience: {{candidateExperience}} years
                        - Skills: {{candidateSkills}}
                        - Location: {{candidateLocation}}
                        - Expected salary: {{expectedSalary}}

                        JOB
                        - Title: {{jobTitle}}
                        - Company: {{jobCompany}}
                        - Location: {{jobLocation}}
                        - Salary: {{jobSalary}}
                        - Description: {{jobDescription}}

                        Reply with ONLY a JSON object, no prose and no code fences:
                        {
                          "matchScore": <integer 0-100>,
                          "recommended": <true|false>,
                          "reasoning": "<one short sentence>",
                          "matchingSkills": ["..."],
                          "missingSkills": ["..."]
                        }""")
                .build());

        promptRepository.save(PromptTemplate.builder()
                .code(PROMPT_COVER_LETTER)
                .name("Cover letter writer")
                .description("Writes a tailored cover letter for an application.")
                .variables("candidateName, candidateRole, candidateExperience, candidateSkills, "
                        + "jobTitle, jobCompany, jobDescription")
                .template("""
                        Write a concise, specific cover letter (max 200 words) for this application.
                        Do not invent experience the candidate does not have. Plain text only.

                        CANDIDATE: {{candidateName}}, {{candidateRole}} with {{candidateExperience}} years.
                        SKILLS: {{candidateSkills}}

                        ROLE: {{jobTitle}} at {{jobCompany}}
                        ABOUT THE ROLE: {{jobDescription}}""")
                .build());

        promptRepository.save(PromptTemplate.builder()
                .code(PROMPT_RESUME_SUMMARY)
                .name("Resume summariser")
                .description("Extracts a structured profile from raw resume text.")
                .variables("resumeText")
                .template("""
                        Read this resume and extract a structured profile.
                        Reply with ONLY a JSON object, no prose and no code fences:
                        {
                          "summary": "<two sentences>",
                          "skills": ["..."],
                          "yearsOfExperience": <number>,
                          "currentRole": "<title>",
                          "suggestedRoles": ["..."]
                        }

                        RESUME:
                        {{resumeText}}""")
                .build());

        promptRepository.save(PromptTemplate.builder()
                .code(PROMPT_SKILL_GAP)
                .name("Skill gap analysis")
                .description("Tells a candidate what to learn next based on the jobs they are missing out on.")
                .variables("candidateSkills, targetRole, missedJobTitles, commonRequirements")
                .template("""
                        A candidate with these skills keeps narrowly missing roles.
                        SKILLS: {{candidateSkills}}
                        TARGET ROLE: {{targetRole}}
                        ROLES THEY MISSED: {{missedJobTitles}}
                        COMMONLY REQUIRED: {{commonRequirements}}

                        Reply with ONLY a JSON object:
                        {
                          "topGaps": ["..."],
                          "learningPlan": [{"skill":"...","why":"...","weeks":<number>}],
                          "encouragement": "<one sentence>"
                        }""")
                .build());

        log.info("Seeded {} AI prompt templates", promptRepository.count());
    }
}
