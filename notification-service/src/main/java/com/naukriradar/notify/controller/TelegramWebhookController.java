package com.naukriradar.notify.controller;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.notify.channel.Message;
import com.naukriradar.notify.channel.NotificationChannel;
import com.naukriradar.notify.channel.Recipient;
import com.naukriradar.notify.channel.TelegramChannel;
import com.naukriradar.notify.config.NotifyProperties;
import com.naukriradar.notify.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Telegram calls this for every message to the bot. "/start CODE" with a code the user got
 * from the app links their chat. Telegram sends the secret we gave it when setting the
 * webhook; anything without it is refused, so nobody else can link chats.
 */
@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramWebhookController {

	private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

	private final NotifyProperties properties;
	private final NotificationChannel telegram;
	private final TemplateService templates;
	private final RestClient coreApi;

	public TelegramWebhookController(NotifyProperties properties, TelegramChannel telegram, TemplateService templates,
			RestClient.Builder builder) {
		this.properties = properties;
		this.telegram = telegram;
		this.templates = templates;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.coreApi = builder.clone().requestFactory(factory).baseUrl(properties.coreApiUrl()).build();
	}

	@PostMapping("/webhook")
	public ResponseEntity<Void> update(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
			@RequestBody JsonNode update) {
		if (!properties.telegramEnabled() || properties.telegramWebhookSecret() == null
				|| !properties.telegramWebhookSecret().equals(secret)) {
			throw new NotFoundException("Not found.");
		}
		JsonNode message = update.path("message");
		String text = message.path("text").asString();
		String chatId = message.path("chat").path("id").asString();
		if (chatId.isBlank() || !text.startsWith("/start ")) {
			return ResponseEntity.ok().build(); // anything else the bot ignores; Telegram only needs a 200
		}
		String code = text.substring("/start ".length()).strip();
		Recipient chat = new Recipient(null, null, null, chatId);
		try {
			coreApi.post().uri("/internal/v1/telegram/link").contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("code", code, "chatId", chatId)).retrieve().toBodilessEntity();
			telegram.send(chat, templates.render("telegram-linked", Map.of()));
		}
		catch (HttpClientErrorException ex) {
			telegram.send(chat, new Message("", "",
					"That code isn't valid or has expired. Get a new one in the app."));
		}
		catch (RuntimeException ex) {
			log.warn("Linking a Telegram chat failed: {}", ex.getMessage());
		}
		return ResponseEntity.ok().build();
	}

}
