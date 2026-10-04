package com.naukriradar.notify.channel;

/** One way of reaching a user. Adding a channel (SMS, WhatsApp) is a new implementation. */
public interface NotificationChannel {

	Channel channel();

	/** Configured and the recipient has an address on it. */
	boolean canReach(Recipient recipient);

	/** @throws RuntimeException if it didn't go out; the caller retries */
	void send(Recipient recipient, Message message);

}
