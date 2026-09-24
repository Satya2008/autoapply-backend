package com.autoapply.apply.service;

import com.autoapply.jobs.entity.Job;
import com.autoapply.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prepares every answer an application form is likely to ask for, so a candidate finishing
 * a risky application by hand only has to click each field and paste.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrefillService {

    private final ObjectMapper objectMapper;

    public String build(User user, Job job, String coverLetter) {
        try {
            return objectMapper.writeValueAsString(fields(user, job, coverLetter));
        } catch (Exception e) {
            log.warn("Could not build the prefill packet: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> parse(String prefillJson) {
        if (prefillJson == null || prefillJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(prefillJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> fields(User user, Job job, String coverLetter) {
        String fullName = nullSafe(user.getFullName());
        String[] nameParts = fullName.trim().split("\\s+", 2);

        List<Map<String, Object>> fields = new ArrayList<>();
        add(fields, "Full name", fullName, "identity", true);
        add(fields, "First name", nameParts.length > 0 ? nameParts[0] : "", "identity", false);
        add(fields, "Last name", nameParts.length > 1 ? nameParts[1] : "", "identity", false);
        add(fields, "Email", nullSafe(user.getEmail()), "identity", true);
        add(fields, "Phone", nullSafe(user.getPhone()), "identity", true);
        add(fields, "Location", nullSafe(user.getLocation()), "identity", false);

        add(fields, "Current role", nullSafe(user.getCurrentRole()), "experience", false);
        add(fields, "Years of experience",
                user.getExperienceYears() == null ? "" : String.valueOf(user.getExperienceYears()),
                "experience", false);
        add(fields, "Expected salary",
                user.getExpectedSalary() == null ? "" : String.valueOf(user.getExpectedSalary()),
                "experience", false);
        add(fields, "Notice period", nullSafe(user.getNoticePeriod()), "experience", false);
        add(fields, "Skills",
                user.getSkills() == null ? "" : String.join(", ", user.getSkills()),
                "experience", false);

        add(fields, "LinkedIn", nullSafe(user.getLinkedinUrl()), "links", false);
        add(fields, "GitHub", nullSafe(user.getGithubUrl()), "links", false);
        add(fields, "Portfolio", nullSafe(user.getPortfolioUrl()), "links", false);

        add(fields, "Cover letter",
                Optional.ofNullable(coverLetter).filter(s -> !s.isBlank())
                        .orElse(nullSafe(user.getCoverLetterTemplate())),
                "letter", true);

        add(fields, "Why this role",
                job.getEmployerName() == null ? "" :
                        "I am applying for " + nullSafe(job.getJobTitle()) + " at " + job.getEmployerName() + ".",
                "letter", false);

        fields.removeIf(field -> String.valueOf(field.get("value")).isBlank());
        return fields;
    }

    private void add(List<Map<String, Object>> fields, String label, String value,
                     String group, boolean primary) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("label", label);
        field.put("value", value == null ? "" : value);
        field.put("group", group);
        field.put("primary", primary);
        fields.add(field);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
