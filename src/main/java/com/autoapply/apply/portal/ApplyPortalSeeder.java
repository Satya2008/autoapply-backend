package com.autoapply.apply.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starter configurations for the applicant tracking systems most job links land on.
 * They ship in dry-run mode so nothing is submitted until an administrator reviews them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class ApplyPortalSeeder {

    private final ApplyPortalConfigRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (repository.count() > 0) return;

        repository.save(ApplyPortalConfig.builder()
                .code("greenhouse")
                .name("Greenhouse")
                .urlPattern("boards.greenhouse.io")
                .enabled(true)
                .dryRun(true)
                .priority(10)
                .readySelector("#application_form, form#application-form")
                .dismissSelectors("#onetrust-accept-btn-handler\n.cookie-accept")
                .fieldMappingJson("""
                        [
                          {"selector":"#first_name","valueFrom":"firstName","type":"text","required":true},
                          {"selector":"#last_name","valueFrom":"lastName","type":"text","required":true},
                          {"selector":"#email","valueFrom":"email","type":"text","required":true},
                          {"selector":"#phone","valueFrom":"phone","type":"text"},
                          {"selector":"input[type=file]#resume","valueFrom":"resumeFile","type":"file"},
                          {"selector":"#job_application_answers_attributes_0_text_value","valueFrom":"coverLetter","type":"text"}
                        ]""")
                .submitSelector("#submit_app, input[type=submit]")
                .successSelector(".application-confirmation, #application_confirmation")
                .failureText("There was a problem")
                .maxWaitSeconds(25)
                .build());

        repository.save(ApplyPortalConfig.builder()
                .code("lever")
                .name("Lever")
                .urlPattern("jobs.lever.co")
                .enabled(true)
                .dryRun(true)
                .priority(20)
                .readySelector("form.application-form, .application-page")
                .openFormSelector("a.postings-btn, .template-btn-submit")
                .fieldMappingJson("""
                        [
                          {"selector":"input[name='name']","valueFrom":"fullName","type":"text","required":true},
                          {"selector":"input[name='email']","valueFrom":"email","type":"text","required":true},
                          {"selector":"input[name='phone']","valueFrom":"phone","type":"text"},
                          {"selector":"input[name='urls[LinkedIn]']","valueFrom":"linkedinUrl","type":"text"},
                          {"selector":"input[name='urls[GitHub]']","valueFrom":"githubUrl","type":"text"},
                          {"selector":"input[type=file][name='resume']","valueFrom":"resumeFile","type":"file"},
                          {"selector":"textarea[name='comments']","valueFrom":"coverLetter","type":"text"}
                        ]""")
                .submitSelector("button[type=submit], .template-btn-submit")
                .successSelector(".application-confirmation, .confirmation-wrapper")
                .maxWaitSeconds(25)
                .build());

        repository.save(ApplyPortalConfig.builder()
                .code("workable")
                .name("Workable")
                .urlPattern("apply.workable.com")
                .enabled(true)
                .dryRun(true)
                .priority(30)
                .readySelector("form, [data-ui=application-form]")
                .dismissSelectors("[data-ui=cookie-accept]")
                .fieldMappingJson("""
                        [
                          {"selector":"input[name='firstname']","valueFrom":"firstName","type":"text","required":true},
                          {"selector":"input[name='lastname']","valueFrom":"lastName","type":"text","required":true},
                          {"selector":"input[name='email']","valueFrom":"email","type":"text","required":true},
                          {"selector":"input[name='phone']","valueFrom":"phone","type":"text"},
                          {"selector":"input[type=file]","valueFrom":"resumeFile","type":"file"},
                          {"selector":"textarea[name='coverletter']","valueFrom":"coverLetter","type":"text"}
                        ]""")
                .submitSelector("button[type=submit]")
                .successSelector("[data-ui=application-success], .application-success")
                .maxWaitSeconds(25)
                .build());

        repository.save(ApplyPortalConfig.builder()
                .code("generic_form")
                .name("Generic application form")
                .urlPattern("(?i).*(careers|jobs|apply).*")
                .enabled(false)
                .dryRun(true)
                .priority(900)
                .readySelector("form")
                .fieldMappingJson("""
                        [
                          {"selector":"input[name*='name' i]","valueFrom":"fullName","type":"text"},
                          {"selector":"input[type='email'], input[name*='mail' i]","valueFrom":"email","type":"text"},
                          {"selector":"input[type='tel'], input[name*='phone' i]","valueFrom":"phone","type":"text"},
                          {"selector":"input[type=file]","valueFrom":"resumeFile","type":"file"},
                          {"selector":"textarea","valueFrom":"coverLetter","type":"text"}
                        ]""")
                .submitSelector("button[type=submit], input[type=submit]")
                .maxWaitSeconds(20)
                .build());

        log.info("Seeded {} apply portal configurations (all in dry-run mode)", repository.count());
    }
}
