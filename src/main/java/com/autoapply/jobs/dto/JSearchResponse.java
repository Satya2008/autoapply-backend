package com.autoapply.jobs.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class JSearchResponse {
    private String status;
    @JsonProperty("request_id") private String requestId;
    private List<JSearchJob> data;

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSearchJob {
        @JsonProperty("job_id") private String jobId;
        @JsonProperty("employer_name") private String employerName;
        @JsonProperty("employer_logo") private String employerLogo;
        @JsonProperty("employer_website") private String employerWebsite;
        @JsonProperty("job_publisher") private String jobPublisher;
        @JsonProperty("job_employment_type") private String jobEmploymentType;
        @JsonProperty("job_title") private String jobTitle;
        @JsonProperty("job_apply_link") private String jobApplyLink;
        @JsonProperty("job_apply_is_direct") private Boolean jobApplyIsDirect;
        @JsonProperty("job_description") private String jobDescription;
        @JsonProperty("job_is_remote") private Boolean jobIsRemote;
        @JsonProperty("job_posted_at_datetime_utc") private String jobPostedAtDatetimeUtc;
        @JsonProperty("job_offer_expiration_datetime_utc") private String jobOfferExpirationDatetimeUtc;
        @JsonProperty("job_city") private String jobCity;
        @JsonProperty("job_state") private String jobState;
        @JsonProperty("job_country") private String jobCountry;
        @JsonProperty("job_min_salary") private Double jobMinSalary;
        @JsonProperty("job_max_salary") private Double jobMaxSalary;
        @JsonProperty("job_salary_currency") private String jobSalaryCurrency;
        @JsonProperty("job_salary_period") private String jobSalaryPeriod;
        @JsonProperty("job_required_skills") private List<String> jobRequiredSkills;
    }
}
