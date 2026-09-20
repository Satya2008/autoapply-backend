package com.autoapply.notification;

import com.autoapply.apply.entity.Application;
import com.autoapply.jobs.entity.Job;
import com.autoapply.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class NotificationEvent {

    private String type;
    private String recipientEmail;
    private String recipientName;
    private String subject;
    private String body;
    @Builder.Default
    private Map<String, Object> data = new LinkedHashMap<>();

    public static NotificationEvent applicationSubmitted(User user, Job job, Application application) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobTitle", job.getJobTitle());
        data.put("company", job.getEmployerName());
        data.put("matchScore", application.getMatchScore());
        data.put("status", application.getStatus());
        data.put("applyLink", job.getJobApplyLink());

        return NotificationEvent.builder()
                .type("APPLICATION_SUBMITTED")
                .recipientEmail(user.getEmail())
                .recipientName(user.getFullName())
                .subject("Applied: " + job.getJobTitle() + " at " + job.getEmployerName())
                .body("Your application for " + job.getJobTitle() + " at " + job.getEmployerName()
                        + " has been submitted"
                        + (application.getMatchScore() == null ? "" : " (match score " + application.getMatchScore() + ")")
                        + ".")
                .data(data)
                .build();
    }

    public static NotificationEvent newMatches(User user, int count, double topScore) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        data.put("topScore", topScore);

        return NotificationEvent.builder()
                .type("NEW_MATCHES")
                .recipientEmail(user.getEmail())
                .recipientName(user.getFullName())
                .subject(count + " new job matches for you")
                .body("We found " + count + " new roles that fit your profile. "
                        + "The best one scores " + topScore + ".")
                .data(data)
                .build();
    }

    public static NotificationEvent dailyDigest(User user, long appliedToday, long newMatches, String highlights) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appliedToday", appliedToday);
        data.put("newMatches", newMatches);

        return NotificationEvent.builder()
                .type("DAILY_DIGEST")
                .recipientEmail(user.getEmail())
                .recipientName(user.getFullName())
                .subject("Your daily job hunt summary")
                .body("Applications sent today: " + appliedToday + "\nNew matches: " + newMatches
                        + (highlights == null || highlights.isBlank() ? "" : "\n\n" + highlights))
                .data(data)
                .build();
    }

    public static NotificationEvent system(String subject, String body) {
        return NotificationEvent.builder()
                .type("SYSTEM")
                .subject(subject)
                .body(body)
                .build();
    }
}
