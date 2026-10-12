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

	/** A career page as many companies have: a cookie banner over it, and "Apply Now" opening the form in a new tab. */
	private static final String CAREER_PAGE = """
			<!doctype html><html><body>
			<div id="banner" style="position:fixed;inset:0;background:#fff;z-index:9">
			  <p>This site uses cookies.</p>
			  <button onclick="fetch('/cookies?choice=accept');banner.remove()">Accept Cookies</button>
			  <button onclick="fetch('/cookies?choice=reject');banner.remove()">Reject Cookies</button>
			</div>
			<h1>Java Developer</h1><p>Thank you for your interest in Acme.</p>
			<a href="/careers/apply%s" target="_blank">Apply Now</a>
			</body></html>""";

	/** Its form, found by labels only; {@code ?gender} adds a required question no profile answers. */
	private static final String CAREER_FORM = """
			<!doctype html><html><body>
			<form method="post" action="/submit">
			  <label for="fn">First Name *</label><input id="fn" name="first" required>
			  <label for="em">Email Address *</label><input id="em" name="email" type="email" required>
			  %s
			  <button type="submit">Submit application</button>
			</form></body></html>""";

	private static final String GENDER = """
			<label for="g">Gender *</label><select id="g" name="gender" required><option value="">Select</option>
			<option>Female</option><option>Male</option><option>Prefer not to say</option></select>""";

	private final HttpServer server;

	private final List<String> submissions = new CopyOnWriteArrayList<>();

	private final List<String> cookieChoices = new CopyOnWriteArrayList<>();

	public FakePortal() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/jobs/1", exchange -> reply(exchange, FORM));
		server.createContext("/submit", exchange -> {
			submissions.add(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
					StandardCharsets.UTF_8));
			reply(exchange, THANKS);
		});
		server.createContext("/careers/job", exchange ->
				reply(exchange, CAREER_PAGE.formatted(exchange.getRequestURI().getQuery() == null ? "" : "?" + exchange.getRequestURI().getQuery())));
		server.createContext("/careers/apply", exchange ->
				reply(exchange, CAREER_FORM.formatted("gender".equals(exchange.getRequestURI().getQuery()) ? GENDER : "")));
		server.createContext("/cookies", exchange -> {
			cookieChoices.add(exchange.getRequestURI().getQuery());
			reply(exchange, "");
		});
		server.start();
	}

	public String formUrl() {
		return "http://localhost:" + server.getAddress().getPort() + "/jobs/1";
	}

	/** The career page; {@code withGender} makes its form ask a question only the candidate can answer. */
	public String careerUrl(boolean withGender) {
		return "http://localhost:" + server.getAddress().getPort() + "/careers/job" + (withGender ? "?gender" : "");
	}

	/** What cookie banners were answered with, e.g. "choice=reject". */
	public List<String> cookieChoices() {
		return cookieChoices;
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
