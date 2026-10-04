package com.naukriradar.common.redis.cache;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.naukriradar.common.redis.InstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Tells the other instances to drop entries from their memory after a value changed here.
 * Redis pub/sub delivers at most once: an instance that is briefly disconnected misses the
 * message. That is why local entries also expire after a short TTL.
 */
public class CacheEvictionBus implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(CacheEvictionBus.class);

	/** ASCII unit separator: can't appear in a cache name and is unlikely in a key. */
	private static final String SEP = "\u001f";

	private final StringRedisTemplate redis;
	private final String channel;
	private final InstanceId instance;
	private volatile Consumer<Eviction> receiver = eviction -> {
	};

	public CacheEvictionBus(StringRedisTemplate redis, String channel, InstanceId instance) {
		this.redis = redis;
		this.channel = channel;
		this.instance = instance;
	}

	public String channel() {
		return channel;
	}

	void onEviction(Consumer<Eviction> receiver) {
		this.receiver = receiver;
	}

	void publish(Eviction eviction) {
		String message = String.join(SEP, instance.value(), eviction.cache(), eviction.kind().name(), eviction.key());
		try {
			redis.convertAndSend(channel, message);
		}
		catch (RuntimeException ex) {
			log.warn("Couldn't tell other instances to evict {} from {}: {}", eviction.key(), eviction.cache(),
					ex.getMessage());
		}
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String[] parts = new String(message.getBody(), StandardCharsets.UTF_8).split(SEP, 4);
		if (parts.length != 4 || parts[0].equals(instance.value())) {
			return;
		}
		Kind kind;
		try {
			kind = Kind.valueOf(parts[2]);
		}
		catch (IllegalArgumentException ex) {
			log.warn("Ignoring a malformed cache eviction message");
			return;
		}
		receiver.accept(new Eviction(parts[1], kind, parts[3]));
	}

	enum Kind {
		KEY, PREFIX, ALL
	}

	record Eviction(String cache, Kind kind, String key) {
	}

}
