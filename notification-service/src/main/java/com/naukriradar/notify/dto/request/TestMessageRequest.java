package com.naukriradar.notify.dto.request;

import com.naukriradar.notify.channel.Channel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestMessageRequest(
		@NotNull Channel channel,
		@Email @Size(max = 254) String email,
		@Size(max = 40) String telegramChatId) {
}
