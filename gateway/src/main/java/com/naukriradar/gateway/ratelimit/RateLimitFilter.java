package com.naukriradar.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.naukriradar.gateway.ratelimit.RateLimitProperties.KeyType;
import com.naukriradar.gateway.ratelimit.RateLimitProperties.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Applies the configured rate limits before a request is routed. The buckets live in Redis,
 * so every gateway instance counts against the same limit.
 *
 * <p>If Redis is slow or down the request goes through: losing rate limiting for a while is
 * better than losing the whole API. Until login exists (Phase 8) the user id is whatever the
 * client sends, so a client can dodge a USER_OR_IP limit by changing it; IP rules can't be
 * dodged that way.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

	static final String USER_HEADER = "X-User-Id";

	/** Anything else in the user header is not used as a key: it could be huge or contain separators. */
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

	private final RateLimitProperties properties;
	private final TokenBucketLimiter limiter;
	private final List<CompiledRule> rules;

	public RateLimitFilter(RateLimitProperties properties, TokenBucketLimiter limiter) {
		this.properties = properties;
		this.limiter = limiter;
		PathPatternParser parser = new PathPatternParser();
		this.rules = properties.rules().stream()
				.map(rule -> new CompiledRule(rule, rule.paths().stream().map(parser::parse).toList()))
				.toList();
	}

	/** Early, so a refused request costs nothing downstream. */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 100;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (!properties.enabled()) {
			return chain.filter(exchange);
		}
		ServerHttpRequest request = exchange.getRequest();
		Rule rule = match(request);
		if (rule == null) {
			return chain.filter(exchange);
		}
		String bucket = properties.keyPrefix() + ":" + rule.name() + ":" + caller(request, rule.by());
		return limiter.take(bucket, rule)
				.timeout(properties.timeout())
				.map(Optional::of)
				.onErrorResume(ex -> {
					log.warn("Rate limit check for {} skipped: {}", rule.name(), ex.toString());
					return Mono.just(Optional.empty());
				})
				.defaultIfEmpty(Optional.empty())
				.flatMap(decision -> {
					if (decision.isEmpty()) {
						return chain.filter(exchange);
					}
					HttpHeaders headers = exchange.getResponse().getHeaders();
					headers.set("X-RateLimit-Limit", String.valueOf(rule.capacity()));
					headers.set("X-RateLimit-Remaining", String.valueOf(decision.get().remaining()));
					return decision.get().allowed() ? chain.filter(exchange) : refuse(exchange, rule, decision.get());
				});
	}

	private Rule match(ServerHttpRequest request) {
		PathContainer path = request.getPath().pathWithinApplication();
		String method = request.getMethod().name();
		for (CompiledRule compiled : rules) {
			boolean methodMatches = compiled.rule().methods().isEmpty() || compiled.rule().methods().contains(method);
			if (methodMatches && compiled.patterns().stream().anyMatch(p -> p.matches(path))) {
				return compiled.rule();
			}
		}
		return null;
	}

	private String caller(ServerHttpRequest request, KeyType by) {
		if (by == KeyType.USER_OR_IP) {
			String user = request.getHeaders().getFirst(USER_HEADER);
			if (user != null && SAFE_ID.matcher(user.strip()).matches()) {
				return "u:" + user.strip().toLowerCase(Locale.ROOT);
			}
		}
		return "ip:" + clientIp(request);
	}

	private String clientIp(ServerHttpRequest request) {
		if (properties.trustForwardedFor()) {
			String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
			if (forwarded != null && !forwarded.isBlank()) {
				return forwarded.split(",")[0].strip();
			}
		}
		InetSocketAddress remote = request.getRemoteAddress();
		return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
	}

	private Mono<Void> refuse(ServerWebExchange exchange, Rule rule, TokenBucketLimiter.Decision decision) {
		// rounded up: a client that waits exactly this long must get through
		long seconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
				+ "\"detail\":\"Too many requests (" + rule.name() + "). Try again in " + seconds + " s.\","
				+ "\"instance\":\"" + jsonEscape(exchange.getRequest().getPath().value()) + "\"}";
		DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
		return response.writeWith(Mono.just(buffer));
	}

	private static String jsonEscape(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			if (c == '"' || c == '\\') {
				out.append('\\').append(c);
			}
			else if (c < 0x20) {
				out.append(String.format("\\u%04x", (int) c));
			}
			else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private record CompiledRule(Rule rule, List<PathPattern> patterns) {
	}

}
