package com.autoapply.matching.service;
import com.autoapply.jobs.entity.Job;
import com.autoapply.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Service @RequiredArgsConstructor @Slf4j
public class GeminiService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}") private String apiKey;
    @Value("${gemini.api.base-url}") private String baseUrl;

    public double getMatchScore(User user, Job job) {
        try {
            String prompt = "Analyze job match. Return ONLY JSON: {matchScore: 0-100, recommended: true/false, missingSkills: []}" +
                " USER skills: " + user.getSkills() + ", exp: " + user.getExperienceYears() + " yrs, role: " + user.getCurrentRole() +
                " JOB: " + job.getJobTitle() + " at " + job.getEmployerName() +
                " Desc: " + (job.getJobDescription() != null ? job.getJobDescription().substring(0, Math.min(400, job.getJobDescription().length())) : "");

            Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{ Map.of("parts", new Object[]{ Map.of("text", prompt) }) }
            );

            String response = webClient.post()
                    .uri(baseUrl + "/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .bodyValue(requestBody).retrieve().bodyToMono(String.class).block();

            var root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            text = text.replaceAll("```json|```", "").trim();
            return objectMapper.readTree(text).path("matchScore").asDouble(50.0);
        } catch (Exception e) {
            log.error("Gemini error: {}", e.getMessage());
            return 50.0;
        }
    }
}
