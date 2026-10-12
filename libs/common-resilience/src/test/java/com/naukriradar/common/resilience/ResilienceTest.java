package com.naukriradar.common.resilience;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceTest {

	private final Resilience resilience = new Resilience(
			new ResilienceProperties(3, Duration.ofMillis(10), 0.5, 50, 4, 4, Duration.ofSeconds(30), 2));

	@Test
	void aTransientFailureIsRetriedUntilItWorks() {
		AtomicInteger calls = new AtomicInteger();

		String result = resilience.call("board", () -> {
			if (calls.incrementAndGet() < 3) {
				throw new ResourceAccessException("timeout", new IOException());
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(3);
	}

	@Test
	void aClientErrorIsNotRetried() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> resilience.call("board", () -> {
			calls.incrementAndGet();
			throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", null, null, null);
		})).isInstanceOf(HttpClientErrorException.class);
		assertThat(calls).hasValue(1);
	}

	@Test
	void callNoTimeoutRetryDoesNotRetryATimeoutButStillRetriesA5xx() {
		AtomicInteger timeoutCalls = new AtomicInteger();
		assertThatThrownBy(() -> resilience.callNoTimeoutRetry("ai-model", () -> {
			timeoutCalls.incrementAndGet();
			throw new ResourceAccessException("read timed out", new java.net.SocketTimeoutException("Read timed out"));
		})).isInstanceOf(ResourceAccessException.class);
		// a hung call is tried once and then handed back, so the caller can fall over to another model
		assertThat(timeoutCalls).hasValue(1);

		AtomicInteger serverErrorCalls = new AtomicInteger();
		String result = resilience.callNoTimeoutRetry("ai-model-2", () -> {
			if (serverErrorCalls.incrementAndGet() < 3) {
				throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "overloaded", null, null, null);
			}
			return "ok";
		});
		// a 503 can clear, so it is still retried
		assertThat(result).isEqualTo("ok");
		assertThat(serverErrorCalls).hasValue(3);
	}

	@Test
	void repeatedFailuresOpenTheCircuitAndLaterCallsFailFast() {
		AtomicInteger calls = new AtomicInteger();
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> resilience.call("ai", () -> {
				calls.incrementAndGet();
				throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "down", null, null, null);
			})).isInstanceOfAny(HttpServerErrorException.class, DependencyUnavailableException.class);
		}
		int before = calls.get();

		assertThatThrownBy(() -> resilience.call("ai", () -> calls.incrementAndGet()))
				.isInstanceOf(DependencyUnavailableException.class);
		assertThat(calls).hasValue(before);
		assertThat(resilience.states()).anySatisfy(s -> {
			assertThat(s.name()).isEqualTo("ai");
			assertThat(s.state()).isEqualTo("OPEN");
		});
		assertThat(resilience.call("other", () -> "fine")).isEqualTo("fine");
	}

}
