package com.naukriradar.job.provider;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.job.exception.BoardUnavailableException;
import com.naukriradar.job.exception.JobSourceFetchException;
import com.naukriradar.job.model.JobField;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.model.SourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;
import org.springframework.stereotype.Component;

/**
 * Reads jobs from an HTML page with CSS selectors: {@code resultsPath} picks each job's
 * element, field mappings pick the parts (see {@link CssMapping}). Polite by design: robots.txt
 * is checked first, requests carry our real user agent, and pages after the first wait a moment.
 * Pages built by JavaScript come back empty here; those need a source with an API instead.
 */
@Component
public class HtmlScrapeJobProvider implements JobSourceProvider {

	private static final long PAGE_DELAY_MS = 1_500;

	private final PageFetcher fetcher;
	private final RobotsTxtPolicy robots;
	private final SettingPlaceholderResolver placeholders;
	private final HostGuard hostGuard;
	private final Resilience resilience;

	public HtmlScrapeJobProvider(PageFetcher fetcher, RobotsTxtPolicy robots, SettingPlaceholderResolver placeholders,
			HostGuard hostGuard, Resilience resilience) {
		this.fetcher = fetcher;
		this.robots = robots;
		this.placeholders = placeholders;
		this.hostGuard = hostGuard;
		this.resilience = resilience;
	}

	@Override
	public SourceType supports() {
		return SourceType.HTML_SCRAPE;
	}

	@Override
	public List<RawJob> fetchPage(JobSource source, FetchRequest request) {
		URI uri = SourceUris.build(source, request, placeholders);
		hostGuard.check(uri);
		robots.check(uri, source.getTimeoutSeconds());
		if (request.page() > 1) {
			pause();
		}
		String html;
		try {
			html = resilience.call("board-" + source.getCode(), () -> load(source, uri));
		}
		catch (DependencyUnavailableException ex) {
			throw new JobSourceFetchException("Skipped: the site failed several times just now; calls are paused for a moment.", ex);
		}
		return extract(source, html, uri);
	}

	private String load(JobSource source, URI uri) {
		Map<String, String> headers = new LinkedHashMap<>();
		source.getHeaders().forEach((name, value) -> headers.put(name, placeholders.resolve(value)));
		PageFetcher.Page page = fetcher.get(uri, "text/html,application/xhtml+xml", headers, source.getTimeoutSeconds());
		if (page.status() >= 500 || page.status() == 429) {
			throw new BoardUnavailableException("The site answered HTTP " + page.status() + ".");
		}
		if (!page.ok()) {
			throw new JobSourceFetchException("The site answered HTTP " + page.status() + ".");
		}
		return page.body();
	}

	List<RawJob> extract(JobSource source, String html, URI uri) {
		Document document = Jsoup.parse(html, uri.toString());
		Elements items;
		try {
			items = document.select(source.getResultsPath());
		}
		catch (Selector.SelectorParseException ex) {
			throw new JobSourceFetchException("results selector " + source.getResultsPath() + " is not valid CSS: " + ex.getMessage(), ex);
		}
		Map<JobField, CssMapping> mappings = new EnumMap<>(JobField.class);
		Map<JobField, String> literals = new EnumMap<>(JobField.class);
		source.getFieldMappings().forEach((key, mapping) -> JobField.fromKey(key).ifPresent(field -> {
			if (FieldMappings.isLiteral(mapping)) {
				literals.put(field, FieldMappings.literal(mapping));
			}
			else {
				mappings.put(field, CssMapping.parse(mapping));
			}
		}));

		List<RawJob> jobs = new ArrayList<>(items.size());
		for (Element item : items) {
			Map<JobField, Object> values = new EnumMap<>(JobField.class);
			values.putAll(literals);
			mappings.forEach((field, mapping) -> values.put(field, mapping.read(item)));
			jobs.add(new RawJob(values));
		}
		return jobs;
	}

	private static void pause() {
		try {
			Thread.sleep(PAGE_DELAY_MS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new JobSourceFetchException("Stopped while waiting between pages.", ex);
		}
	}

}
