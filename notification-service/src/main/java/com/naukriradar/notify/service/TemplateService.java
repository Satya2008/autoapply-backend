package com.naukriradar.notify.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.notify.channel.Message;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Message wording lives in Mustache files, not in code: {@code <name>.subject},
 * {@code <name>.html} and {@code <name>.text} under templates/notifications. HTML is escaped
 * in the email body, so a job title can't inject markup.
 */
@Service
public class TemplateService {

	private static final String DIR = "templates/notifications/";

	private final Mustache.Compiler html = Mustache.compiler().defaultValue("");
	private final Mustache.Compiler text = Mustache.compiler().escapeHTML(false).defaultValue("");
	private final Map<String, Template> cache = new ConcurrentHashMap<>();

	/** @throws IllegalArgumentException if there is no such template */
	public Message render(String name, Map<String, Object> data) {
		if (name == null || !name.matches("[a-z0-9-]+")) {
			throw new IllegalArgumentException("Bad template name: " + name);
		}
		return new Message(template(name + ".subject", text).execute(data).strip(),
				template(name + ".html", html).execute(data), template(name + ".text", text).execute(data).strip());
	}

	private Template template(String file, Mustache.Compiler compiler) {
		return cache.computeIfAbsent(file + (compiler == html ? ":html" : ":text"), key -> {
			ClassPathResource resource = new ClassPathResource(DIR + file + ".mustache");
			if (!resource.exists()) {
				throw new IllegalArgumentException("No template " + file);
			}
			try (InputStream in = resource.getInputStream()) {
				return compiler.compile(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		});
	}

}
