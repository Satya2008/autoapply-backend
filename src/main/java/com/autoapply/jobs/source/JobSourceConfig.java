package com.autoapply.jobs.source;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A configurable connection to a job board. The GENERIC_REST provider can consume almost
 * any JSON job API using only the field mappings stored here, so onboarding a new board
 * is a row in this table rather than a code change.
 */
@Entity
@Table(name = "job_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false, length = 80)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Which built-in adapter handles this source: JSEARCH, ADZUNA, REMOTEOK, GENERIC_REST. */
    @Column(length = 40)
    private String providerType;

    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 1000)
    private String baseUrl;

    /** Path appended to baseUrl. Supports {query}, {page}, {country}, {limit} placeholders. */
    @Column(length = 1000)
    private String searchPath;

    @Column(length = 10)
    @Builder.Default
    private String httpMethod = "GET";

    /** JSON object of request headers. Values may reference a setting as ${setting:key.name}. */
    @Column(columnDefinition = "TEXT")
    private String headersJson;

    /** JSON object of extra query parameters, same placeholder support as searchPath. */
    @Column(columnDefinition = "TEXT")
    private String queryParamsJson;

    /** JSON body template for POST style APIs. */
    @Column(columnDefinition = "TEXT")
    private String bodyTemplate;

    /** JsonPath to the array of jobs in the response, e.g. $.data or $.results */
    @Column(length = 300)
    @Builder.Default
    private String resultsPath = "$.data";

    /**
     * JSON object mapping our Job fields to JsonPath expressions relative to one result item.
     * Example: {"jobId":"$.job_id","jobTitle":"$.job_title","employerName":"$.employer_name"}
     */
    @Column(columnDefinition = "TEXT")
    private String fieldMappingJson;

    /** Default search terms for this source when the scheduler runs it. */
    @Column(columnDefinition = "TEXT")
    private String defaultQueries;

    @Builder.Default
    private Integer priority = 100;

    @Builder.Default
    private Integer rateLimitPerHour = 100;

    @Builder.Default
    private Integer timeoutSeconds = 30;

    // --- operational telemetry, updated by the fetcher ---
    private LocalDateTime lastRunAt;
    private String lastRunStatus;
    @Column(length = 1000)
    private String lastRunMessage;
    @Builder.Default
    private Integer totalFetched = 0;
    @Builder.Default
    private Integer consecutiveFailures = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
        if (priority == null) priority = 100;
        if (totalFetched == null) totalFetched = 0;
        if (consecutiveFailures == null) consecutiveFailures = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
