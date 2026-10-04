package com.naukriradar.worker.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A small job portal on localhost: an application form that posts to /submit and a thank-you
 * page. Every submitted form is recorded, so a test can tell whether (and how often) the
 * browser really submitted.
 */
public final class FakePortal implements AutoCloseable {

	private static final String FORM = """
			<!doctype html><html><body>
			<h1>Backend Engineer</h1>
			<form id="apply" method="post" action="/submit">
			  <input id="name" name="name">
			  <input id="email" name="email" type="email">
			  <button id="send" type="submit">Apply</button>
			</form></body></html>""";

	private static final String THANKS = "<!doctype html><html><body><div id=\"thanks\">Thanks for applying!</div></body></html>";

	private final HttpServer server;

	private final List<String> submissions = new CopyOnWriteArrayList<>();

	public FakePortal() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/jobs/1", exchange -> reply(exchange, FORM));
		server.createContext("/submit", exchange -> {
			submissions.add(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
					StandardCharsets.UTF_8));
			reply(exchange, THANKS);
		});
		server.start();
	}

	public String formUrl() {
		return "http://localhost:" + server.getAddress().getPort() + "/jobs/1";
	}

	/** Decoded form bodies, e.g. "name=Asha&email=asha@example.com". */
	public List<String> submissions() {
		return submissions;
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private static void reply(HttpExchange exchange, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

}
