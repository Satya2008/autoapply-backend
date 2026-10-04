package com.naukriradar.notify.channel;

import com.naukriradar.notify.config.NotifyProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Email over SMTP, as HTML with a plain-text alternative. */
@Component
public class EmailChannel implements NotificationChannel {

	private final JavaMailSender mail;
	private final NotifyProperties properties;

	public EmailChannel(JavaMailSender mail, NotifyProperties properties) {
		this.mail = mail;
		this.properties = properties;
	}

	@Override
	public Channel channel() {
		return Channel.EMAIL;
	}

	@Override
	public boolean canReach(Recipient recipient) {
		return recipient.email() != null && !recipient.email().isBlank();
	}

	@Override
	public void send(Recipient recipient, Message message) {
		try {
			MimeMessage mime = mail.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
			helper.setFrom(properties.fromAddress());
			helper.setTo(recipient.email());
			helper.setSubject(message.subject());
			helper.setText(message.text(), message.html());
			mail.send(mime);
		}
		catch (MessagingException ex) {
			throw new MailSendException("Could not build the email", ex);
		}
	}

}
