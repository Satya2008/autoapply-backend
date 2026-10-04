package com.naukriradar.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Rate limits counted in Redis, with small limits so the tests reach them. */
class RateLimitTests {

	private static final HttpServer service = startFakeService();

	private static final HttpClient client = HttpClient.newHttpClient();

	@AfterAll
	static void stopFake() {
		service.stop(0);
	}

	static void common(DynamicPropertyRegistry registry) {
		String url = "http://localhost:" + service.getAddress().getPort();
		registry.add("services.core-api", () -> url);
		registry.add("services.job-service", () -> url);
		registry.add("services.matching-service", () -> url);
		registry.add("spring.data.redis.database", () -> "1");
		// fresh buckets for every test run
		registry.add("naukriradar.rate-limit.key-prefix", () -> "naukriradar-test:rate:" + UUID.randomUUID());
		registry.add("naukriradar.rate-limit.rules[0].name", () -> "runs");
		registry.add("naukriradar.rate-limit.rules[0].paths[0]", () -> "/api/v1/me/matches/runs");
		registry.add("naukriradar.rate-limit.rules[0].methods[0]", () -> "post");
		registry.add("naukriradar.rate-limit.rules[0].capacity", () -> "2");
		registry.add("naukriradar.rate-limit.rules[0].refill-every", () -> "1h");
		registry.add("naukriradar.rate-limit.rules[1].name", () -> "by-ip");
		registry.add("naukriradar.rate-limit.rules[1].paths[0]", () -> "/api/v1/jobs/**");
		registry.add("naukriradar.rate-limit.rules[1].capacity", () -> "1");
		registry.add("naukriradar.rate-limit.rules[1].refill-every", () -> "1h");
		registry.add("naukriradar.rate-limit.rules[1].by", () -> "IP");
		registry.add("naukriradar.rate-limit.rules[2].name", () -> "api");
		registry.add("naukriradar.rate-limit.rules[2].paths[0]", () -> "/api/v1/**");
		registry.add("naukriradar.rate-limit.rules[2].capacity", () -> "100");
		registry.add("naukriradar.rate-limit.rules[2].refill-every", () -> "100ms");
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class WithRedis {

		@LocalServerPort
		private int port;

		@DynamicPropertySource
		static void properties(DynamicPropertyRegistry registry) {
			common(registry);
		}

		@Test
		void aBurstAboveTheLimitIsRefusedWithRetryAfter() throws Exception {
			String user = UUID.randomUUID().toString();

			HttpResponse<String> first = post("/api/v1/me/matches/runs", user);
			HttpResponse<String> second = post("/api/v1/me/matches/runs", user);
			HttpResponse<String> third = post("/api/v1/me/matches/runs", user);

			assertThat(first.statusCode()).isEqualTo(200);
			assertThat(first.headers().firstValue("X-RateLimit-Limit")).hasValue("2");
			assertThat(first.headers().firstValue("X-RateLimit-Remaining")).hasValue("1");
			assertThat(second.statusCode()).isEqualTo(200);
			assertThat(second.headers().firstValue("X-RateLimit-Remaining")).hasValue("0");
			assertThat(third.statusCode()).isEqualTo(429);
			assertThat(third.headers().firstValue("Retry-After").map(Long::parseLong)).hasValueSatisfying(
					seconds -> assertThat(seconds).isBetween(3500L, 3600L));
			assertThat(third.headers().firstValue("Content-Type")).hasValue("application/problem+json");
			assertThat(third.body()).contains("\"status\":429").contains("runs")
					.contains("\"instance\":\"/api/v1/me/matches/runs\"");
		}

		@Test
		void eachUserHasTheirOwnBucket() throws Exception {
			String greedy = UUID.randomUUID().toString();
			post("/api/v1/me/matches/runs", greedy);
			post("/api/v1/me/matches/runs", greedy);
			assertThat(post("/api/v1/me/matches/runs", greedy).statusCode()).isEqualTo(429);

			assertThat(post("/api/v1/me/matches/runs", UUID.randomUUID().toString()).statusCode()).isEqualTo(200);
		}

		@Test
		void otherMethodsFallToTheNextRule() throws Exception {
			String user = UUID.randomUUID().toString();
			for (int i = 0; i < 5; i++) {
				HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/me/matches/runs"))
						.header("X-User-Id", user));
				assertThat(response.statusCode()).isEqualTo(200);
				assertThat(response.headers().firstValue("X-RateLimit-Limit")).hasValue("100");
			}
		}

		@Test
		void ipRulesIgnoreTheUserHeaderAndUntrustedForwardedFor() throws Exception {
			assertThat(send(HttpRequest.newBuilder(uri("/api/v1/jobs/1")).header("X-User-Id", "a")).statusCode())
					.isEqualTo(200);
			assertThat(send(HttpRequest.newBuilder(uri("/api/v1/jobs/2")).header("X-User-Id", "b")
					.header("X-Forwarded-For", "198.51.100.7")).statusCode()).isEqualTo(429);
		}

		@Test
		void aJunkUserHeaderCountsAgainstTheIpInstead() throws Exception {
			String junk = "x".repeat(500);
			HttpResponse<String> response = post("/api/v1/me/matches/runs", junk);

			assertThat(response.statusCode()).isIn(200, 429);
			assertThat(response.headers().firstValue("X-RateLimit-Limit")).hasValue("2");
		}

		@Test
		void pathsNoRuleCoversAreNotLimited() throws Exception {
			HttpResponse<String> docs = send(HttpRequest.newBuilder(uri("/docs/core-api")));

			assertThat(docs.statusCode()).isEqualTo(200);
			assertThat(docs.headers().firstValue("X-RateLimit-Limit")).isEmpty();
		}

		private HttpResponse<String> post(String path, String user) throws Exception {
			return send(HttpRequest.newBuilder(uri(path)).header("X-User-Id", user).POST(HttpRequest.BodyPublishers.noBody()));
		}

		private URI uri(String path) {
			return URI.create("http://localhost:" + port + path);
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class WithRedisDown {

		@LocalServerPort
		private int port;

		@DynamicPropertySource
		static void properties(DynamicPropertyRegistry registry) {
			common(registry);
			registry.add("spring.data.redis.port", () -> "1");
		}

		@Test
		void requestsGoThroughUnlimited() throws Exception {
			for (int i = 0; i < 4; i++) {
				HttpResponse<String> response = send(HttpRequest.newBuilder(
						URI.create("http://localhost:" + port + "/api/v1/me/matches/runs"))
						.header("X-User-Id", "u-1").POST(HttpRequest.BodyPublishers.noBody()));

				assertThat(response.statusCode()).isEqualTo(200);
				assertThat(response.headers().firstValue("X-RateLimit-Limit")).isEmpty();
			}
		}

	}

	private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static HttpServer startFakeService() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				byte[] bytes = "ok".getBytes();
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
