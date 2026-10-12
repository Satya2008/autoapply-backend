package com.naukriradar.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sign-in for every API call. The client sends "Authorization: Bearer &lt;token&gt;" (a stream
 * that can't set headers sends ?access_token=); core-api says whose session it is, and the
 * request goes on with X-User-Id and X-User-Role set here. Whatever a client puts in those two
 * headers is dropped, so nobody can pose as someone else. /api/v1/admin is for admins only.
 *
 * <p>Answers are cached for a short while, so a busy page doesn't ask core-api on every call. Calls that
 * end sessions or change who someone is (sign out, sign out everywhere, an admin disabling a user,
 * changing their role or password) drop the cached answers for that user at once.
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

	static final String USER_HEADER = "X-User-Id";
	static final String ROLE_HEADER = "X-User-Role";

	/** Open without a session: signing up and in, and Telegram's webhook (checked by its own secret). */
	private static final List<String> OPEN = List.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout",
			"/api/v1/telegram/webhook");

	private static final Duration CACHE_FOR = Duration.ofSeconds(30);

	private final WebClient core;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	public AuthFilter(@Value("${services.core-api}") String coreApiUrl) {
		this.core = WebClient.builder().baseUrl(coreApiUrl).build();
	}

	/** Before the rate limiter, so it counts the verified user. */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 50;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getPath().pathWithinApplication().value();
		// never trust identity headers from outside
		ServerHttpRequest clean = request.mutate().headers(h -> {
			h.remove(USER_HEADER);
			h.remove(ROLE_HEADER);
		}).build();
		if (!path.startsWith("/api/")) {
			return chain.filter(exchange.mutate().request(clean).build());
		}
		if (path.startsWith("/api/v1/dev/")) {
			// test helpers: not public
			return problem(exchange, HttpStatus.NOT_FOUND, "Not found.");
		}
		if (path.equals("/api/v1/auth/logout")) {
			String token = token(request, path);
			return chain.filter(exchange.mutate().request(clean).build())
					.doFinally(signal -> Optional.ofNullable(token).ifPresent(cache::remove));
		}
		if (OPEN.contains(path) || request.getMethod() == HttpMethod.OPTIONS) {
			return chain.filter(exchange.mutate().request(clean).build());
		}
		String token = token(request, path);
		if (token == null) {
			return problem(exchange, HttpStatus.UNAUTHORIZED, "Sign in first.");
		}
		// only the session check decides 401/503; the routed call's own (empty) completion must not
		return resolve(token)
				.map(Optional::of)
				.defaultIfEmpty(Optional.empty())
				.onErrorResume(SessionCheckUnavailable.class, ex -> Mono.just(Optional.of(UNAVAILABLE)))
				.flatMap(found -> {
					if (found.isEmpty()) {
						return problem(exchange, HttpStatus.UNAUTHORIZED, "Your session has ended. Sign in again.");
					}
					SessionUser user = found.get();
					if (user == UNAVAILABLE) {
						return problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Sign-in can't be checked right now. Try again shortly.");
					}
					if (path.startsWith("/api/v1/admin/") && !"ADMIN".equals(user.role())) {
						return problem(exchange, HttpStatus.FORBIDDEN, "Only admins can do that.");
					}
					ServerHttpRequest signedIn = clean.mutate().headers(h -> {
						h.set(USER_HEADER, user.userId());
						h.set(ROLE_HEADER, user.role());
					}).build();
					// after core-api has made the change, so no call in between caches the old answer
					return chain.filter(exchange.mutate().request(signedIn).build())
							.doFinally(signal -> forgetIfSessionsChange(request.getMethod(), path, user));
				});
	}

	private static final Pattern ADMIN_USER_CHANGE = Pattern.compile("/api/v1/admin/users/([^/]+)/(status|role|reset-password)");

	/** Whose cached sessions a call makes stale, if any: they are asked again on their next call. */
	private void forgetIfSessionsChange(HttpMethod method, String path, SessionUser user) {
		if (method == HttpMethod.GET) {
			return;
		}
		String whose = null;
		if (path.equals("/api/v1/auth/logout-everywhere") || path.equals("/api/v1/auth/change-password")) {
			whose = user.userId();
		} else {
			Matcher m = ADMIN_USER_CHANGE.matcher(path);
			if (m.matches()) {
				whose = m.group(1);
			}
		}
		if (whose != null) {
			String userId = whose;
			cache.values().removeIf(c -> c.user().userId().equals(userId));
		}
	}

	/** Stands for "couldn't ask core-api" inside the chain above. */
	private static final SessionUser UNAVAILABLE = new SessionUser("", "");

	private static String token(ServerHttpRequest request, String path) {
		String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
			return header.substring(7).strip();
		}
		// a browser's EventSource can't send headers; only the live streams accept the token in the URL
		if (path.endsWith("/stream")) {
			return request.getQueryParams().getFirst("access_token");
		}
		return null;
	}

	private Mono<SessionUser> resolve(String token) {
		Cached hit = cache.get(token);
		Instant now = Instant.now();
		if (hit != null && hit.until().isAfter(now)) {
			return Mono.just(hit.user());
		}
		return core.post()
				.uri("/internal/v1/sessions/resolve")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("token", token))
				.retrieve()
				.bodyToMono(SessionUser.class)
				.timeout(Duration.ofSeconds(5))
				.doOnNext(user -> {
					if (cache.size() > 10_000) {
						cache.clear();
					}
					cache.put(token, new Cached(user, now.plus(CACHE_FOR)));
				})
				.onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty())
				.onErrorResume(ex -> !(ex instanceof WebClientResponseException.NotFound), ex -> {
					log.warn("Couldn't check a session: {}", ex.toString());
					return Mono.error(new SessionCheckUnavailable());
				});
	}

	private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String detail) {
		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		String body = "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value()
				+ ",\"detail\":\"" + detail.replace("\"", "'") + "\"}";
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

	record SessionUser(String userId, String role) {
	}

	private record Cached(SessionUser user, Instant until) {
	}

	/** core-api couldn't be asked; the request fails with 503 rather than going through unchecked. */
	static class SessionCheckUnavailable extends RuntimeException {

		SessionCheckUnavailable() {
			super("Sign-in check unavailable", null, false, false);
		}

	}

}
