package com.autoapply.jobs.source;

import com.autoapply.jobs.entity.Job;
import com.autoapply.settings.SettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to any JSON job API using only what is stored in {@link JobSourceConfig}:
 * the URL template, headers, and a JsonPath field mapping. Adding a new job board
 * means inserting a row - never touching this class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericRestJobProvider implements JobSourceProvider {

    private static final Pattern SETTING_REF = Pattern.compile("\\$\\{setting:([^}]+)}");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;

    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
            .build();

    @Override
    public String type() {
        return "GENERIC_REST";
    }

    @Override
    public List<Job> fetch(JobSourceConfig config, String query, String country, int page) {
        String url = buildUrl(config, query, country, page);
        Map<String, String> headers = resolvePlaceholders(readMap(config.getHeadersJson()), query, country, page);

        log.debug("Fetching jobs from {} -> {}", config.getCode(), url);

        WebClient.RequestBodySpec request = webClient
                .method(HttpMethod.valueOf(Optional.ofNullable(config.getHttpMethod()).orElse("GET")))
                .uri(url);
        headers.forEach(request::header);

        String response;
        if ("POST".equalsIgnoreCase(config.getHttpMethod()) && config.getBodyTemplate() != null) {
            String body = substitute(config.getBodyTemplate(), query, country, page);
            response = request.header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout(config)))
                    .block();
        } else {
            response = request.retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout(config)))
                    .block();
        }

        if (response == null || response.isBlank()) {
            log.warn("Source {} returned an empty response", config.getCode());
            return List.of();
        }
        return mapResults(config, response);
    }

    // ------------------------------------------------------------------ url

    private String buildUrl(JobSourceConfig config, String query, String country, int page) {
        String base = Optional.ofNullable(config.getBaseUrl()).orElse("").replaceAll("/+$", "");
        String path = Optional.ofNullable(config.getSearchPath()).orElse("");
        String raw = base + (path.startsWith("/") || path.isEmpty() ? path : "/" + path);
        raw = substitute(raw, query, country, page);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(raw);
        resolvePlaceholders(readMap(config.getQueryParamsJson()), query, country, page)
                .forEach(builder::queryParam);
        return builder.build().toUriString();
    }

    private String substitute(String template, String query, String country, int page) {
        if (template == null) return "";
        String encodedQuery = java.net.URLEncoder.encode(
                query == null ? "" : query, java.nio.charset.StandardCharsets.UTF_8);
        return template
                .replace("{query}", encodedQuery)
                .replace("{rawQuery}", query == null ? "" : query)
                .replace("{country}", country == null ? "" : country)
                .replace("{page}", String.valueOf(page))
                .replace("{limit}", "50");
    }

    /** Replaces ${setting:some.key} with the live value so secrets never sit in the source row. */
    private Map<String, String> resolvePlaceholders(Map<String, String> input, String query, String country, int page) {
        Map<String, String> resolved = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            String out = substitute(value, query, country, page);
            Matcher matcher = SETTING_REF.matcher(out);
            StringBuilder builder = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(builder,
                        Matcher.quoteReplacement(settings.getString(matcher.group(1).trim(), "")));
            }
            matcher.appendTail(builder);
            resolved.put(key, builder.toString());
        });
        return resolved;
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("Invalid JSON map in job source config: {}", e.getMessage());
            return Map.of();
        }
    }

    private int timeout(JobSourceConfig config) {
        return config.getTimeoutSeconds() == null ? 30 : config.getTimeoutSeconds();
    }

    // -------------------------------------------------------------- mapping

    private List<Job> mapResults(JobSourceConfig config, String response) {
        DocumentContext document = JsonPath.using(jsonPathConfig).parse(response);
        String resultsPath = Optional.ofNullable(config.getResultsPath()).filter(s -> !s.isBlank()).orElse("$.data");

        List<Map<String, Object>> items;
        try {
            Object raw = document.read(resultsPath);
            if (raw instanceof List<?> list) {
                items = list.stream()
                        .filter(Map.class::isInstance)
                        .map(o -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> casted = (Map<String, Object>) o;
                            return casted;
                        })
                        .toList();
            } else {
                log.warn("Source {}: resultsPath '{}' did not resolve to an array", config.getCode(), resultsPath);
                return List.of();
            }
        } catch (Exception e) {
            log.warn("Source {}: could not read resultsPath '{}': {}", config.getCode(), resultsPath, e.getMessage());
            return List.of();
        }

        Map<String, String> mapping = readMap(config.getFieldMappingJson());
        List<Job> jobs = new ArrayList<>();
        for (Map<String, Object> item : items) {
            try {
                Job job = mapOne(config, item, mapping);
                if (job != null && job.getJobId() != null && !job.getJobId().isBlank()) {
                    jobs.add(job);
                }
            } catch (Exception e) {
                log.debug("Skipping an unmappable result from {}: {}", config.getCode(), e.getMessage());
            }
        }
        return jobs;
    }

    private Job mapOne(JobSourceConfig config, Map<String, Object> item, Map<String, String> mapping) {
        DocumentContext itemContext = JsonPath.using(jsonPathConfig).parse(item);

        String jobId = str(itemContext, mapping.get("jobId"));
        if (jobId == null || jobId.isBlank()) {
            String title = str(itemContext, mapping.get("jobTitle"));
            String employer = str(itemContext, mapping.get("employerName"));
            if (title == null && employer == null) return null;
            jobId = config.getCode() + ":" + Math.abs(Objects.hash(title, employer, str(itemContext, mapping.get("jobApplyLink"))));
        } else {
            jobId = config.getCode() + ":" + jobId;
        }

        return Job.builder()
                .jobId(jobId)
                .sourceCode(config.getCode())
                .jobTitle(str(itemContext, mapping.get("jobTitle")))
                .jobPublisher(Optional.ofNullable(str(itemContext, mapping.get("jobPublisher"))).orElse(config.getName()))
                .employerName(str(itemContext, mapping.get("employerName")))
                .employerLogo(str(itemContext, mapping.get("employerLogo")))
                .employerWebsite(str(itemContext, mapping.get("employerWebsite")))
                .jobApplyLink(str(itemContext, mapping.get("jobApplyLink")))
                .jobApplyIsDirect(bool(itemContext, mapping.get("jobApplyIsDirect")))
                .jobDescription(str(itemContext, mapping.get("jobDescription")))
                .jobCity(str(itemContext, mapping.get("jobCity")))
                .jobState(str(itemContext, mapping.get("jobState")))
                .jobCountry(str(itemContext, mapping.get("jobCountry")))
                .jobIsRemote(bool(itemContext, mapping.get("jobIsRemote")))
                .jobEmploymentType(str(itemContext, mapping.get("jobEmploymentType")))
                .jobMinSalary(dbl(itemContext, mapping.get("jobMinSalary")))
                .jobMaxSalary(dbl(itemContext, mapping.get("jobMaxSalary")))
                .jobSalaryCurrency(str(itemContext, mapping.get("jobSalaryCurrency")))
                .jobSalaryPeriod(str(itemContext, mapping.get("jobSalaryPeriod")))
                .jobRequiredSkills(str(itemContext, mapping.get("jobRequiredSkills")))
                .jobPostedAt(LocalDateTime.now())
                .build();
    }

    private String str(DocumentContext context, String path) {
        if (path == null || path.isBlank()) return null;
        try {
            Object value = context.read(path);
            if (value == null) return null;
            if (value instanceof List<?> list) {
                return list.isEmpty() ? null : String.join(", ", list.stream().map(String::valueOf).toList());
            }
            String text = String.valueOf(value);
            return text.isBlank() || "null".equals(text) ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean bool(DocumentContext context, String path) {
        String value = str(context, path);
        if (value == null) return null;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private Double dbl(DocumentContext context, String path) {
        String value = str(context, path);
        if (value == null) return null;
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.\\-]", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
