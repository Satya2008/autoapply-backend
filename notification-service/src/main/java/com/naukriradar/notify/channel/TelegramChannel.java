package com.naukriradar.notify.channel;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.naukriradar.notify.config.NotifyProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Telegram, through the Bot API's sendMessage. Off when no bot token is configured. */
@Component
public class TelegramChannel implements NotificationChannel {

	private final RestClient client;
	private final NotifyProperties properties;

	public TelegramChannel(RestClient.Builder builder, NotifyProperties properties) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(15));
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.telegramBaseUrl()).build();
		this.properties = properties;
	}

	@Override
	public Channel channel() {
		return Channel.TELEGRAM;
	}

	@Override
	public boolean canReach(Recipient recipient) {
		return properties.telegramEnabled() && recipient.telegramChatId() != null && !recipient.telegramChatId().isBlank();
	}

	@Override
	public void send(Recipient recipient, Message message) {
		client.post()
				.uri("/bot{token}/sendMessage", properties.telegramBotToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("chat_id", recipient.telegramChatId(), "text", message.text(),
						"disable_web_page_preview", true))
				.retrieve()
				.toBodilessEntity();
	}

}
