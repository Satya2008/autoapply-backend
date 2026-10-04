package com.naukriradar.common.resilience;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Wraps a call to another system (a job board, another service, an AI provider):
 *
 * <pre>retry( circuit breaker( bulkhead( call ) ) )</pre>
 *
 * <ul>
 * <li><b>Bulkhead</b>: at most N calls to one dependency at a time, so a slow one can't take
 * every thread we have.</li>
 * <li><b>Circuit breaker</b>: when most recent calls failed, stop calling for a while and fail
 * at once instead of waiting on timeouts. Then let a few calls through to see if it's back.</li>
 * <li><b>Retry</b>: transient failures only, with exponential backoff and jitter. It sits
 * outside the breaker, so every attempt is counted and an open circuit is never retried.</li>
 * </ul>
 *
 * Each dependency gets its own breaker and bulkhead, created on first use. Timeouts belong to
 * the HTTP clients (every one has a read timeout), not to this class.
 */
public class Resilience {

	private final CircuitBreakerRegistry breakers;
	private final RetryRegistry retries;
	private final BulkheadRegistry bulkheads;

	public Resilience(ResilienceProperties properties) {
		Predicate<Throwable> transientFailure = Resilience::isTransient;
		this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
				.failureRateThreshold(properties.failureRate())
				.slidingWindowSize(properties.window())
				.minimumNumberOfCalls(properties.minimumCalls())
				.waitDurationInOpenState(properties.openFor())
				.permittedNumberOfCallsInHalfOpenState(3)
				.recordException(transientFailure)
				.build());
		this.retries = RetryRegistry.of(RetryConfig.custom()
				.maxAttempts(properties.maxAttempts())
				.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(properties.firstBackoff(), 2.0,
						properties.jitter()))
				.retryOnException(transientFailure)
				.build());
		this.bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
				.maxConcurrentCalls(properties.maxConcurrent())
				.maxWaitDuration(Duration.ZERO)
				.build());
	}

	/**
	 * @throws DependencyUnavailableException if the circuit is open or the bulkhead is full
	 */
	public <T> T call(String dependency, Supplier<T> action) {
		CircuitBreaker breaker = breakers.circuitBreaker(dependency);
		Supplier<T> guarded = Bulkhead.decorateSupplier(bulkheads.bulkhead(dependency), action);
		guarded = CircuitBreaker.decorateSupplier(breaker, guarded);
		guarded = Retry.decorateSupplier(retries.retry(dependency), guarded);
		try {
			return guarded.get();
		}
		catch (CallNotPermittedException ex) {
			throw new DependencyUnavailableException(dependency, "it failed repeatedly, calls are paused for a moment", ex);
		}
		catch (BulkheadFullException ex) {
			throw new DependencyUnavailableException(dependency, "too many calls to it are in progress", ex);
		}
	}

	public void run(String dependency, Runnable action) {
		call(dependency, () -> {
			action.run();
			return null;
		});
	}

	public List<DependencyState> states() {
		return breakers.getAllCircuitBreakers().stream()
				.map(breaker -> {
					CircuitBreaker.Metrics metrics = breaker.getMetrics();
					int free = bulkheads.bulkhead(breaker.getName()).getMetrics().getAvailableConcurrentCalls();
					return new DependencyState(breaker.getName(), breaker.getState().name(),
							metrics.getFailureRate() < 0 ? null : metrics.getFailureRate(), metrics.getNumberOfBufferedCalls(),
							metrics.getNumberOfFailedCalls(), metrics.getNumberOfNotPermittedCalls(), free);
				})
				.sorted(Comparator.comparing(DependencyState::name))
				.toList();
	}

	/** Worth another try: our own markers, network trouble, and 5xx or 429 answers. */
	static boolean isTransient(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof TransientFailure || t instanceof ResourceAccessException) {
				return true;
			}
			if (t instanceof RestClientResponseException response) {
				int status = response.getStatusCode().value();
				return status >= 500 || status == 429;
			}
		}
		return false;
	}

	/**
	 * @param failureRate percent, or null until the breaker has seen enough calls
	 * @param freeSlots calls that could start right now
	 */
	public record DependencyState(String name, String state, Float failureRate, int recentCalls, int recentFailures,
			long refusedCalls, int freeSlots) {
	}

}
