package com.autoapply.notification;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Builds its own mail sender from settings on each send, so SMTP credentials can be
 * changed in the dashboard and take effect on the very next email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailChannel implements NotificationChannel {

    private final SettingsService settings;

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.MAIL_HOST) && settings.isConfigured(SettingKeys.MAIL_USERNAME);
    }

    @Override
    public void send(NotificationEvent event) throws Exception {
        if (event.getRecipientEmail() == null || event.getRecipientEmail().isBlank()) {
            log.debug("Skipping email for {} - no recipient", event.getType());
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getString(SettingKeys.MAIL_HOST, "smtp.gmail.com"));
        sender.setPort(settings.getInt(SettingKeys.MAIL_PORT, 587));
        sender.setUsername(settings.getString(SettingKeys.MAIL_USERNAME));
        sender.setPassword(settings.getString(SettingKeys.MAIL_PASSWORD));

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable",
                String.valueOf(settings.getBoolean(SettingKeys.MAIL_STARTTLS, true)));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");

        var message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(settings.getString(SettingKeys.MAIL_FROM, "noreply@autoapply.local"));
        helper.setTo(event.getRecipientEmail());
        helper.setSubject(event.getSubject());
        helper.setText(buildBody(event), false);

        String replyTo = settings.getString(SettingKeys.APP_SUPPORT_EMAIL, "");
        if (!replyTo.isBlank()) helper.setReplyTo(replyTo);

        sender.send(message);
        log.debug("Sent {} email to {}", event.getType(), event.getRecipientEmail());
    }

    private String buildBody(NotificationEvent event) {
        String appName = settings.getString(SettingKeys.APP_NAME, "AutoApply AI");
        String publicUrl = settings.getString(SettingKeys.APP_PUBLIC_URL, "");
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(event.getRecipientName() == null ? "there" : event.getRecipientName()).append(",\n\n");
        body.append(event.getBody()).append("\n\n");
        if (!publicUrl.isBlank()) {
            body.append("Open your dashboard: ").append(publicUrl).append("\n\n");
        }
        body.append("- ").append(appName);
        return body.toString();
    }
}
