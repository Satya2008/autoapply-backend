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

/** Starts the gateway in front of a fake core-api and checks what reaches it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {

	private static final HttpServer coreApi = startFakeCoreApi();

	private final HttpClient client = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void routeToFake(DynamicPropertyRegistry registry) {
		registry.add("services.core-api", () -> "http://localhost:" + coreApi.getAddress().getPort());
	}

	@AfterAll
	static void stopFake() {
		coreApi.stop(0);
	}

	@Test
	void profileRequestReachesCoreApiWithTheUserHeader() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/me/profile"))
				.header("X-User-Id", "11111111-1111-1111-1111-111111111111"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("core-api saw GET /api/v1/me/profile as 11111111-1111-1111-1111-111111111111");
	}

	@Test
	void serviceDocsAreServedUnderTheGateway() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/docs/core-api")));

		assertThat(response.body()).isEqualTo("core-api saw GET /v3/api-docs as null");
	}

	@Test
	void unknownPathsAreNotForwarded() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/unknown")));

		assertThat(response.statusCode()).isEqualTo(404);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Echoes back the method, path and user header it received. */
	private static HttpServer startFakeCoreApi() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				String body = "core-api saw " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
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
