package com.autoapply.jobs.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ships working configurations for several public job boards. Each one is only a row -
 * the same GENERIC_REST adapter drives all of them, which is what makes adding the next
 * board a dashboard task instead of a release.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class JobSourceSeeder {

    private final JobSourceConfigRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (repository.count() > 0) return;

        repository.save(JobSourceConfig.builder()
                .code("jsearch")
                .name("JSearch (RapidAPI)")
                .providerType("GENERIC_REST")
                .enabled(false)
                .baseUrl("https://jsearch.p.rapidapi.com")
                .searchPath("/search")
                .httpMethod("GET")
                .headersJson("""
                        {"x-rapidapi-key":"${setting:jobs.jsearch.api.key}","x-rapidapi-host":"jsearch.p.rapidapi.com"}""")
                .queryParamsJson("""
                        {"query":"{rawQuery}","page":"{page}","num_pages":"1","country":"{country}"}""")
                .resultsPath("$.data")
                .fieldMappingJson("""
                        {"jobId":"$.job_id","jobTitle":"$.job_title","employerName":"$.employer_name",\
                        "employerLogo":"$.employer_logo","employerWebsite":"$.employer_website",\
                        "jobPublisher":"$.job_publisher","jobApplyLink":"$.job_apply_link",\
                        "jobApplyIsDirect":"$.job_apply_is_direct","jobDescription":"$.job_description",\
                        "jobCity":"$.job_city","jobState":"$.job_state","jobCountry":"$.job_country",\
                        "jobIsRemote":"$.job_is_remote","jobEmploymentType":"$.job_employment_type",\
                        "jobMinSalary":"$.job_min_salary","jobMaxSalary":"$.job_max_salary",\
                        "jobSalaryCurrency":"$.job_salary_currency","jobSalaryPeriod":"$.job_salary_period",\
                        "jobRequiredSkills":"$.job_required_skills"}""")
                .defaultQueries("Java Developer in India,Spring Boot Developer in India")
                .priority(10)
                .build());

        repository.save(JobSourceConfig.builder()
                .code("remoteok")
                .name("RemoteOK")
                .providerType("GENERIC_REST")
                .enabled(true)
                .baseUrl("https://remoteok.com")
                .searchPath("/api")
                .httpMethod("GET")
                .headersJson("""
                        {"User-Agent":"Mozilla/5.0 (compatible; AutoApplyBot/1.0)","Accept":"application/json"}""")
                .resultsPath("$[?(@.id)]")
                .fieldMappingJson("""
                        {"jobId":"$.id","jobTitle":"$.position","employerName":"$.company",\
                        "employerLogo":"$.company_logo","jobApplyLink":"$.url","jobDescription":"$.description",\
                        "jobCity":"$.location","jobIsRemote":"$.remote","jobMinSalary":"$.salary_min",\
                        "jobMaxSalary":"$.salary_max","jobRequiredSkills":"$.tags"}""")
                .defaultQueries("developer")
                .priority(20)
                .build());

        repository.save(JobSourceConfig.builder()
                .code("arbeitnow")
                .name("Arbeitnow Job Board")
                .providerType("GENERIC_REST")
                .enabled(true)
                .baseUrl("https://www.arbeitnow.com")
                .searchPath("/api/job-board-api")
                .httpMethod("GET")
                .queryParamsJson("""
                        {"page":"{page}"}""")
                .headersJson("""
                        {"Accept":"application/json"}""")
                .resultsPath("$.data")
                .fieldMappingJson("""
                        {"jobId":"$.slug","jobTitle":"$.title","employerName":"$.company_name",\
                        "jobApplyLink":"$.url","jobDescription":"$.description","jobCity":"$.location",\
                        "jobIsRemote":"$.remote","jobEmploymentType":"$.job_types","jobRequiredSkills":"$.tags"}""")
                .defaultQueries("developer")
                .priority(30)
                .build());

        repository.save(JobSourceConfig.builder()
                .code("themuse")
                .name("The Muse")
                .providerType("GENERIC_REST")
                .enabled(true)
                .baseUrl("https://www.themuse.com")
                .searchPath("/api/public/jobs")
                .httpMethod("GET")
                .queryParamsJson("""
                        {"page":"{page}","category":"Software Engineer"}""")
                .headersJson("""
                        {"Accept":"application/json"}""")
                .resultsPath("$.results")
                .fieldMappingJson("""
                        {"jobId":"$.id","jobTitle":"$.name","employerName":"$.company.name",\
                        "jobApplyLink":"$.refs.landing_page","jobDescription":"$.contents",\
                        "jobCity":"$.locations[0].name","jobEmploymentType":"$.type",\
                        "jobRequiredSkills":"$.categories[*].name"}""")
                .defaultQueries("software engineer")
                .priority(40)
                .build());

        repository.save(JobSourceConfig.builder()
                .code("adzuna")
                .name("Adzuna")
                .providerType("GENERIC_REST")
                .enabled(false)
                .baseUrl("https://api.adzuna.com/v1/api/jobs")
                .searchPath("/in/search/{page}")
                .httpMethod("GET")
                .queryParamsJson("""
                        {"app_id":"${setting:jobs.adzuna.app.id}","app_key":"${setting:jobs.adzuna.app.key}",\
                        "results_per_page":"50","what":"{rawQuery}"}""")
                .headersJson("""
                        {"Accept":"application/json"}""")
                .resultsPath("$.results")
                .fieldMappingJson("""
                        {"jobId":"$.id","jobTitle":"$.title","employerName":"$.company.display_name",\
                        "jobApplyLink":"$.redirect_url","jobDescription":"$.description",\
                        "jobCity":"$.location.display_name","jobMinSalary":"$.salary_min",\
                        "jobMaxSalary":"$.salary_max","jobEmploymentType":"$.contract_time"}""")
                .defaultQueries("java developer")
                .priority(50)
                .build());

        log.info("Seeded {} job source configurations", repository.count());
    }
}
