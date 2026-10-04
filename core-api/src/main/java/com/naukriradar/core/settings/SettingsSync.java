package com.naukriradar.core.settings;

import java.nio.charset.StandardCharsets;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps every core-api instance on the same settings. A change committed here is announced
 * on a Redis channel; the other instances receive it and replay it as a local
 * {@link SettingChangedEvent}, so their settings cache drops the key and their scheduler
 * picks up a new cron, exactly as on the instance where the change was made.
 *
 * <p>Only the key travels, never the value: secrets stay out of Redis, and each instance
 * reads the value from the database. Pub/sub may drop a message while an instance is
 * disconnected; the settings cache's five-minute expiry covers that case.
 */
@Component
public class SettingsSync implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(SettingsSync.class);

	private static final String SEP = "|";

	private final StringRedisTemplate redis;
	private final ApplicationEventPublisher events;
	private final InstanceId instance;
	private final String channel;

	public SettingsSync(StringRedisTemplate redis, ApplicationEventPublisher events, InstanceId instance, RedisKeys keys,
			RedisMessageListenerContainer container) {
		this.redis = redis;
		this.events = events;
		this.instance = instance;
		this.channel = keys.key("settings-changed");
		container.addMessageListener(this, new ChannelTopic(channel));
	}

	/** After the local listeners (cache 0, scheduler 10): this instance is up to date first. */
	@TransactionalEventListener(fallbackExecution = true)
	@Order(20)
	public void announce(SettingChangedEvent event) {
		if (event.remote()) {
			return;
		}
		try {
			redis.convertAndSend(channel, instance.value() + SEP + event.key());
		}
		catch (RuntimeException ex) {
			log.warn("Couldn't tell other instances that {} changed; they will see it within five minutes: {}",
					event.key(), ex.getMessage());
		}
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		int split = body.lastIndexOf(SEP);
		if (split <= 0 || split == body.length() - 1) {
			log.warn("Ignoring a malformed settings message");
			return;
		}
		String from = body.substring(0, split);
		String key = body.substring(split + 1);
		if (from.equals(instance.value())) {
			return;
		}
		if (SettingDefinitions.find(key).isEmpty()) {
			// a newer instance may know settings we don't; nothing to refresh here
			log.debug("Ignoring a change to unknown setting {}", key);
			return;
		}
		events.publishEvent(new SettingChangedEvent(key, true));
	}

}
