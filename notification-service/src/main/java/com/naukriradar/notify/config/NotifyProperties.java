package com.naukriradar.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fromAddress sender of every email
 * @param telegramBaseUrl Telegram's Bot API (changed only in tests)
 * @param telegramBotToken from BotFather; without it Telegram is switched off
 * @param telegramWebhookSecret Telegram sends it with every update; updates without it are refused
 * @param coreApiUrl where Telegram links are recorded
 */
@ConfigurationProperties("naukriradar.notify")
public record NotifyProperties(
		@DefaultValue("NaukriRadar <no-reply@naukriradar.local>") String fromAddress,
		@DefaultValue("https://api.telegram.org") String telegramBaseUrl,
		String telegramBotToken,
		String telegramWebhookSecret,
		@DefaultValue("http://localhost:8081") String coreApiUrl) {

	public boolean telegramEnabled() {
		return telegramBotToken != null && !telegramBotToken.isBlank();
	}

}
