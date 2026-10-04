package com.naukriradar.notify.controller;

import java.util.Map;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.notify.channel.Channel;
import com.naukriradar.notify.channel.Recipient;
import com.naukriradar.notify.dto.request.TestMessageRequest;
import com.naukriradar.notify.service.Delivery;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {

	private final Delivery delivery;

	public NotificationAdminController(Delivery delivery) {
		this.delivery = delivery;
	}

	/** Sends a test message right away, to check SMTP or the Telegram bot. */
	@PostMapping("/test")
	public Map<String, Object> test(@Valid @RequestBody TestMessageRequest request) {
		Recipient recipient = new Recipient(null, "Admin", request.email(), request.telegramChatId());
		try {
			delivery.sendNow(request.channel(), recipient, Map.of("channel", label(request.channel())));
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
		catch (RuntimeException ex) {
			throw new ServiceUnavailableException(label(request.channel()) + " failed: " + ex.getMessage());
		}
		return Map.of("sent", true, "channel", request.channel());
	}

	private static String label(Channel channel) {
		return channel == Channel.EMAIL ? "email" : "Telegram";
	}

}
