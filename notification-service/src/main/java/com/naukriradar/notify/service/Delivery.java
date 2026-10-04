package com.naukriradar.notify.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.notify.channel.Channel;
import com.naukriradar.notify.channel.Message;
import com.naukriradar.notify.channel.NotificationChannel;
import com.naukriradar.notify.channel.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a notification and sends it on each requested channel: the fan-out. Channels are
 * independent: one failing doesn't stop the others, and only the failed ones are tried again.
 */
@Service
public class Delivery {

	private static final Logger log = LoggerFactory.getLogger(Delivery.class);

	private final Map<Channel, NotificationChannel> channels = new EnumMap<>(Channel.class);
	private final TemplateService templates;
	private final NotificationLog notificationLog;
	private final Resilience resilience;

	public Delivery(List<NotificationChannel> channels, TemplateService templates, NotificationLog notificationLog,
			Resilience resilience) {
		channels.forEach(c -> this.channels.put(c.channel(), c));
		this.templates = templates;
		this.notificationLog = notificationLog;
		this.resilience = resilience;
	}

	/** @return channels that failed; empty when everything that could go out went out */
	public List<Channel> deliver(String eventId, String template, Recipient recipient, List<Channel> wanted,
			Map<String, Object> data) {
		Map<String, Object> model = new HashMap<>(data == null ? Map.of() : data);
		model.putIfAbsent("name", recipient.name() == null || recipient.name().isBlank() ? "there" : recipient.name());
		Message message = templates.render(template, model);
		return wanted.stream().distinct().filter(channel -> !sendOne(eventId, template, recipient, channel, message)).toList();
	}

	/** Sends straight away and without the log: the admin's test message. */
	public void sendNow(Channel channel, Recipient recipient, Map<String, Object> data) {
		NotificationChannel sender = channels.get(channel);
		if (sender == null || !sender.canReach(recipient)) {
			throw new IllegalArgumentException(channel + " is not set up, or there is no address for it.");
		}
		sender.send(recipient, templates.render("test", data));
	}

	/** @return true when sent now, sent before, or not possible on this channel (nothing to retry) */
	private boolean sendOne(String eventId, String template, Recipient recipient, Channel channel, Message message) {
		NotificationChannel sender = channels.get(channel);
		if (sender == null || !sender.canReach(recipient)) {
			log.debug("{} can't reach user {}; skipped", channel, recipient.userId());
			return true;
		}
		if (notificationLog.sent(eventId, channel.name())) {
			return true; // a repeat of an event already delivered on this channel
		}
		try {
			resilience.run("channel-" + channel.name().toLowerCase(), () -> sender.send(recipient, message));
			notificationLog.record(eventId, channel.name(), recipient.userId(), template, true, null);
			return true;
		}
		catch (RuntimeException ex) {
			log.warn("{} to user {} failed: {}", channel, recipient.userId(), ex.getMessage());
			notificationLog.record(eventId, channel.name(), recipient.userId(), template, false, ex.getMessage());
			return false;
		}
	}

}
