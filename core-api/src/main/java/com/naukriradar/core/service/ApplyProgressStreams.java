package com.naukriradar.core.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open live streams (Server-Sent Events) per user on this instance. The browser keeps one
 * connection open and is told when an apply run finishes, instead of polling.
 *
 * <p>With several instances the user's stream may be on a different one than the run; that is
 * why every instance listens to apply.completed itself (see ApplyProgressListener).
 */
@Service
public class ApplyProgressStreams {

	private static final Duration TIMEOUT = Duration.ofMinutes(30);

	/** Idle proxies close silent connections; a comment line every so often keeps them open. */
	private static final Duration HEARTBEAT = Duration.ofSeconds(25);

	private final Map<String, List<SseEmitter>> streams = new ConcurrentHashMap<>();
	private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().name("sse-heartbeat").daemon().factory());

	public ApplyProgressStreams() {
		heartbeat.scheduleAtFixedRate(this::beat, HEARTBEAT.toSeconds(), HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
	}

	public SseEmitter open(String userId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
		List<SseEmitter> mine = streams.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
		mine.add(emitter);
		Runnable remove = () -> mine.remove(emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(error -> remove.run());
		send(userId, emitter, "connected", Map.of("userId", userId));
		return emitter;
	}

	/** @return how many open streams got it */
	public int send(String userId, String eventName, Object data) {
		List<SseEmitter> mine = streams.getOrDefault(userId, List.of());
		mine.forEach(emitter -> send(userId, emitter, eventName, data));
		return mine.size();
	}

	public int openStreams() {
		return streams.values().stream().mapToInt(List::size).sum();
	}

	private void send(String userId, SseEmitter emitter, String eventName, Object data) {
		try {
			emitter.send(SseEmitter.event().name(eventName).data(data));
		}
		catch (IOException | IllegalStateException ex) {
			// the browser went away; drop the stream
			streams.getOrDefault(userId, List.of()).remove(emitter);
		}
	}

	private void beat() {
		streams.forEach((userId, emitters) -> emitters.forEach(emitter -> {
			try {
				emitter.send(SseEmitter.event().comment("keep-alive"));
			}
			catch (IOException | IllegalStateException ex) {
				emitters.remove(emitter);
			}
		}));
	}

	@PreDestroy
	void stop() {
		heartbeat.shutdownNow();
		streams.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
	}

}
