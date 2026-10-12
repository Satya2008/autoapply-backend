package com.naukriradar.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the gateway in front of fake services and checks what reaches each one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {

	private static final HttpServer coreApi = startFakeService("core-api");

	private static final HttpServer jobService = startFakeService("job-service");

	private static final HttpServer matchingService = startFakeService("matching-service");

	private static final HttpServer notificationService = startFakeService("notification-service");

	/** The admin the fake core-api knows ("Bearer admin-token"). */
	static final String ADMIN = "99999999-9999-9999-9999-999999999999";

	private final HttpClient client = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void routeToFake(DynamicPropertyRegistry registry) {
		registry.add("services.core-api", () -> "http://localhost:" + coreApi.getAddress().getPort());
		registry.add("services.job-service", () -> "http://localhost:" + jobService.getAddress().getPort());
		registry.add("services.matching-service", () -> "http://localhost:" + matchingService.getAddress().getPort());
		registry.add("services.notification-service",
				() -> "http://localhost:" + notificationService.getAddress().getPort());
		registry.add("spring.data.redis.database", () -> "1");
	}

	@AfterAll
	static void stopFake() {
		coreApi.stop(0);
		jobService.stop(0);
		matchingService.stop(0);
		notificationService.stop(0);
	}

	@Test
	void profileRequestReachesCoreApiWithTheUserHeader() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/me/profile"))
				.header("Authorization", "Bearer user-11111111-1111-1111-1111-111111111111"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("core-api saw GET /api/v1/me/profile as 11111111-1111-1111-1111-111111111111");
	}

	@Test
	void serviceDocsAreServedUnderTheGateway() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/docs/core-api")));

		assertThat(response.body()).isEqualTo("core-api saw GET /v3/api-docs as null");
	}

	@Test
	void jobSourceAdminRequestsReachJobService() throws Exception {
		HttpResponse<String> list = send(HttpRequest.newBuilder(uri("/api/v1/admin/job-sources")));
		HttpResponse<String> fetch = send(HttpRequest.newBuilder(uri("/api/v1/admin/job-sources/abc/fetch"))
				.POST(HttpRequest.BodyPublishers.noBody()));
		HttpResponse<String> docs = send(HttpRequest.newBuilder(uri("/docs/job-service")));

		assertThat(list.body()).isEqualTo("job-service saw GET /api/v1/admin/job-sources as " + ADMIN);
		assertThat(fetch.body()).isEqualTo("job-service saw POST /api/v1/admin/job-sources/abc/fetch as " + ADMIN);
		assertThat(docs.body()).isEqualTo("job-service saw GET /v3/api-docs as null");
	}

	@Test
	void jobSearchAndRunsReachJobService() throws Exception {
		HttpResponse<String> search = send(HttpRequest.newBuilder(uri("/api/v1/jobs?q=java")));
		HttpResponse<String> detail = send(HttpRequest.newBuilder(uri("/api/v1/jobs/abc")));
		HttpResponse<String> runs = send(HttpRequest.newBuilder(uri("/api/v1/admin/jobs/fetch-runs"))
				.POST(HttpRequest.BodyPublishers.noBody()));

		assertThat(search.body()).isEqualTo("job-service saw GET /api/v1/jobs as " + ADMIN);
		assertThat(detail.body()).isEqualTo("job-service saw GET /api/v1/jobs/abc as " + ADMIN);
		assertThat(runs.body()).isEqualTo("job-service saw POST /api/v1/admin/jobs/fetch-runs as " + ADMIN);
	}

	@Test
	void matchesGoToMatchingServiceEvenThoughTheyAreUnderMe() throws Exception {
		String user = "22222222-2222-2222-2222-222222222222";
		HttpResponse<String> list = send(HttpRequest.newBuilder(uri("/api/v1/me/matches")).header("Authorization", "Bearer user-" + user));
		HttpResponse<String> run = send(HttpRequest.newBuilder(uri("/api/v1/me/matches/runs")).header("Authorization", "Bearer user-" + user)
				.POST(HttpRequest.BodyPublishers.noBody()));
		HttpResponse<String> profile = send(HttpRequest.newBuilder(uri("/api/v1/me/profile")).header("Authorization", "Bearer user-" + user));
		HttpResponse<String> docs = send(HttpRequest.newBuilder(uri("/docs/matching-service")));

		assertThat(list.body()).isEqualTo("matching-service saw GET /api/v1/me/matches as " + user);
		assertThat(run.body()).isEqualTo("matching-service saw POST /api/v1/me/matches/runs as " + user);
		assertThat(profile.body()).startsWith("core-api saw GET /api/v1/me/profile");
		assertThat(docs.body()).isEqualTo("matching-service saw GET /v3/api-docs as null");
	}

	@Test
	void skillGapAndEvalsGoToMatchingAndScreeningAnswersToCoreApi() throws Exception {
		String user = "44444444-4444-4444-4444-444444444444";
		HttpResponse<String> gap = send(HttpRequest.newBuilder(uri("/api/v1/me/skill-gap")).header("Authorization", "Bearer user-" + user));
		HttpResponse<String> eval = send(HttpRequest.newBuilder(uri("/api/v1/admin/evals/runs"))
				.POST(HttpRequest.BodyPublishers.ofString("{}")));
		HttpResponse<String> answers = send(HttpRequest.newBuilder(uri("/api/v1/me/applications/a1/screening-answers"))
				.header("Authorization", "Bearer user-" + user).POST(HttpRequest.BodyPublishers.ofString("{}")));

		assertThat(gap.body()).isEqualTo("matching-service saw GET /api/v1/me/skill-gap as " + user);
		assertThat(eval.body()).isEqualTo("matching-service saw POST /api/v1/admin/evals/runs as " + ADMIN);
		assertThat(answers.body()).isEqualTo("core-api saw POST /api/v1/me/applications/a1/screening-answers as " + user);
	}

	@Test
	void applicationsAndPortalsGoToCoreApi() throws Exception {
		String user = "33333333-3333-3333-3333-333333333333";
		HttpResponse<String> runs = send(HttpRequest.newBuilder(uri("/api/v1/me/applications/runs")).header("Authorization", "Bearer user-" + user)
				.POST(HttpRequest.BodyPublishers.noBody()));
		HttpResponse<String> portals = send(HttpRequest.newBuilder(uri("/api/v1/admin/portals")));

		assertThat(runs.body()).isEqualTo("core-api saw POST /api/v1/me/applications/runs as " + user);
		assertThat(portals.body()).isEqualTo("core-api saw GET /api/v1/admin/portals as " + ADMIN);
	}

	@Test
	void settingsAuditAndSchedulerGoToCoreApi() throws Exception {
		HttpResponse<String> setting = send(HttpRequest.newBuilder(uri("/api/v1/admin/settings/applications.max-attempts"))
				.PUT(HttpRequest.BodyPublishers.ofString("{}")));
		HttpResponse<String> audit = send(HttpRequest.newBuilder(uri("/api/v1/admin/audit?actor=x")));
		HttpResponse<String> job = send(HttpRequest.newBuilder(uri("/api/v1/admin/scheduler/auto-apply/run"))
				.POST(HttpRequest.BodyPublishers.noBody()));

		assertThat(setting.body()).isEqualTo("core-api saw PUT /api/v1/admin/settings/applications.max-attempts as " + ADMIN);
		assertThat(audit.body()).isEqualTo("core-api saw GET /api/v1/admin/audit as " + ADMIN);
		assertThat(job.body()).isEqualTo("core-api saw POST /api/v1/admin/scheduler/auto-apply/run as " + ADMIN);
	}

	@Test
	void eachServiceHasItsOwnCacheAdminPath() throws Exception {
		HttpResponse<String> jobs = send(HttpRequest.newBuilder(uri("/api/v1/admin/cache/job-service")));
		HttpResponse<String> clear = send(HttpRequest.newBuilder(uri("/api/v1/admin/cache/matching-service/match-pages"))
				.DELETE());
		HttpResponse<String> core = send(HttpRequest.newBuilder(uri("/api/v1/admin/cache/core-api")));

		assertThat(jobs.body()).isEqualTo("job-service saw GET /api/v1/admin/cache as " + ADMIN);
		assertThat(clear.body()).isEqualTo("matching-service saw DELETE /api/v1/admin/cache/match-pages as " + ADMIN);
		assertThat(core.body()).isEqualTo("core-api saw GET /api/v1/admin/cache as " + ADMIN);
		assertThat(send(HttpRequest.newBuilder(uri("/api/v1/admin/cache"))).statusCode()).isEqualTo(404);
		assertThat(send(HttpRequest.newBuilder(uri("/api/v1/admin/cache/nope"))).statusCode()).isEqualTo(404);
	}

	@Test
	void promptsAndAiAdminGoToMatchingAndResilienceToEachService() throws Exception {
		HttpResponse<String> prompts = send(HttpRequest.newBuilder(uri("/api/v1/admin/prompts/job-fit/versions"))
				.POST(HttpRequest.BodyPublishers.noBody()));
		HttpResponse<String> usage = send(HttpRequest.newBuilder(uri("/api/v1/admin/ai/usage?groupBy=model")));
		HttpResponse<String> jobs = send(HttpRequest.newBuilder(uri("/api/v1/admin/resilience/job-service")));
		HttpResponse<String> core = send(HttpRequest.newBuilder(uri("/api/v1/admin/resilience/core-api")));

		assertThat(prompts.body()).isEqualTo("matching-service saw POST /api/v1/admin/prompts/job-fit/versions as " + ADMIN);
		assertThat(usage.body()).isEqualTo("matching-service saw GET /api/v1/admin/ai/usage as " + ADMIN);
		assertThat(jobs.body()).isEqualTo("job-service saw GET /api/v1/admin/resilience as " + ADMIN);
		assertThat(core.body()).isEqualTo("core-api saw GET /api/v1/admin/resilience as " + ADMIN);
	}

	@Test
	void eachServiceHasItsOwnEventsAdminPath() throws Exception {
		HttpResponse<String> dlq = send(HttpRequest.newBuilder(uri("/api/v1/admin/events/job-service/dlq?topic=x")));
		HttpResponse<String> replay = send(HttpRequest.newBuilder(uri("/api/v1/admin/events/core-api/dlq/abc/replay"))
				.POST(HttpRequest.BodyPublishers.noBody()));
		HttpResponse<String> outbox = send(HttpRequest.newBuilder(uri("/api/v1/admin/events/matching-service/outbox")));

		assertThat(dlq.body()).isEqualTo("job-service saw GET /api/v1/admin/events/dlq as " + ADMIN);
		assertThat(replay.body()).isEqualTo("core-api saw POST /api/v1/admin/events/dlq/abc/replay as " + ADMIN);
		assertThat(outbox.body()).isEqualTo("matching-service saw GET /api/v1/admin/events/outbox as " + ADMIN);
	}

	@Test
	void notificationAdminAndTheTelegramWebhookGoToNotificationService() throws Exception {
		HttpResponse<String> test = send(HttpRequest.newBuilder(uri("/api/v1/admin/notifications/test"))
				.POST(HttpRequest.BodyPublishers.ofString("{}")));
		HttpResponse<String> webhook = send(HttpRequest.newBuilder(uri("/api/v1/telegram/webhook"))
				.POST(HttpRequest.BodyPublishers.ofString("{}")));
		HttpResponse<String> prefs = send(HttpRequest.newBuilder(uri("/api/v1/me/notification-preferences")));

		assertThat(test.body()).isEqualTo("notification-service saw POST /api/v1/admin/notifications/test as " + ADMIN);
		assertThat(webhook.body()).isEqualTo("notification-service saw POST /api/v1/telegram/webhook as null");
		assertThat(prefs.body()).startsWith("core-api saw GET /api/v1/me/notification-preferences");
	}

	@Test
	void internalEndpointsAreNotExposed() throws Exception {
		assertThat(send(HttpRequest.newBuilder(uri("/internal/v1/users/x/matching-profile"))).statusCode()).isEqualTo(404);
		assertThat(send(HttpRequest.newBuilder(uri("/internal/v1/jobs/candidates"))
				.POST(HttpRequest.BodyPublishers.noBody())).statusCode()).isEqualTo(404);
		assertThat(send(HttpRequest.newBuilder(uri("/internal/v1/users/x/matches"))).statusCode()).isEqualTo(404);
	}

	@Test
	void withoutASessionTheApiIsClosedExceptSignInAndSignUp() throws Exception {
		assertThat(sendAnonymous(HttpRequest.newBuilder(uri("/api/v1/me/profile"))).statusCode()).isEqualTo(401);
		assertThat(sendAnonymous(HttpRequest.newBuilder(uri("/api/v1/me/profile"))
				.header("Authorization", "Bearer not-a-session")).statusCode()).isEqualTo(401);
		assertThat(sendAnonymous(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
				.POST(HttpRequest.BodyPublishers.ofString("{}"))).body()).isEqualTo("core-api saw POST /api/v1/auth/login as null");
		assertThat(sendAnonymous(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
				.POST(HttpRequest.BodyPublishers.ofString("{}"))).statusCode()).isEqualTo(200);
	}

	@Test
	void aUserIdSentByTheClientIsNeverTrusted() throws Exception {
		String me = "55555555-5555-5555-5555-555555555555";
		// no session: posing as someone else gets nothing
		assertThat(sendAnonymous(HttpRequest.newBuilder(uri("/api/v1/me/profile"))
				.header("X-User-Id", "66666666-6666-6666-6666-666666666666")).statusCode()).isEqualTo(401);
		// with a session: the header is replaced by the session's user
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/me/profile"))
				.header("Authorization", "Bearer user-" + me).header("X-User-Id", "66666666-6666-6666-6666-666666666666")
				.header("X-User-Role", "ADMIN"));
		assertThat(response.body()).isEqualTo("core-api saw GET /api/v1/me/profile as " + me);
	}

	@Test
	void theAdminConsoleIsForAdminsOnly() throws Exception {
		HttpResponse<String> asUser = send(HttpRequest.newBuilder(uri("/api/v1/admin/users"))
				.header("Authorization", "Bearer user-77777777-7777-7777-7777-777777777777"));
		HttpResponse<String> asAdmin = send(HttpRequest.newBuilder(uri("/api/v1/admin/users")));

		assertThat(asUser.statusCode()).isEqualTo(403);
		assertThat(asAdmin.body()).isEqualTo("core-api saw GET /api/v1/admin/users as " + ADMIN);
	}

	@Test
	void testHelpersAreNotPublic() throws Exception {
		assertThat(send(HttpRequest.newBuilder(uri("/api/v1/dev/users?email=a@b.c"))).statusCode()).isEqualTo(404);
	}

	@Test
	void unknownPathsAreNotForwarded() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/unknown")));

		assertThat(response.statusCode()).isEqualTo(404);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	/** Signed in as the admin unless the request says otherwise. */
	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		HttpRequest built = request.build();
		if (built.headers().firstValue("Authorization").isEmpty()) {
			built = request.header("Authorization", "Bearer admin-token").build();
		}
		return client.send(built, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> sendAnonymous(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Echoes back its name, the method, path and user header it received. core-api also resolves sessions. */
	private static HttpServer startFakeService(String name) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				if (path.equals("/internal/v1/sessions/resolve")) {
					// "admin-token" is the admin; "user-<id>" is that user; anything else isn't a session
					String token = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
							.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]*)\".*", "$1");
					String json = token.equals("admin-token") ? "{\"userId\": \"" + ADMIN + "\", \"role\": \"ADMIN\"}"
							: token.startsWith("user-") ? "{\"userId\": \"" + token.substring(5) + "\", \"role\": \"USER\"}" : null;
					byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set("Content-Type", "application/json");
					exchange.sendResponseHeaders(json == null ? 404 : 200, json == null ? -1 : bytes.length);
					if (json != null) {
						exchange.getResponseBody().write(bytes);
					}
					exchange.close();
					return;
				}
				String body = name + " saw " + exchange.getRequestMethod() + " " + path
						+ " as " + exchange.getRequestHeaders().getFirst("X-User-Id");
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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
