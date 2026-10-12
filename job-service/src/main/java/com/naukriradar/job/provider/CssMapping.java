package com.naukriradar.job.provider;

import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;

/**
 * Where a field sits inside one scraped item, written as a CSS selector and an optional
 * {@code @attribute}:
 * <ul>
 * <li>{@code h3.title}: the text of the first match</li>
 * <li>{@code a.apply@href}: an attribute; href and src become absolute URLs</li>
 * <li>{@code .description@html}: the inner HTML, so line breaks survive into the description</li>
 * <li>{@code @data-id}: an attribute of the item itself</li>
 * </ul>
 */
public record CssMapping(String selector, String attribute) {

	private static final Pattern ATTRIBUTE = Pattern.compile("[A-Za-z_:][-A-Za-z0-9_:.]*");

	public static CssMapping parse(String mapping) {
		String text = mapping.strip();
		int at = text.lastIndexOf('@');
		if (at >= 0 && ATTRIBUTE.matcher(text.substring(at + 1)).matches() && text.lastIndexOf(']') < at) {
			return new CssMapping(text.substring(0, at).strip(), text.substring(at + 1));
		}
		return new CssMapping(text, null);
	}

	/** Throws {@code Selector.SelectorParseException} when the selector isn't valid CSS. */
	public void validate() {
		if (!selector.isEmpty()) {
			Evaluator ignored = QueryParser.parse(selector);
		}
		else if (attribute == null) {
			throw new IllegalArgumentException("empty selector");
		}
	}

	public String read(Element item) {
		Element target = selector.isEmpty() ? item : item.selectFirst(selector);
		if (target == null) {
			return null;
		}
		if (attribute == null) {
			return target.text();
		}
		if (attribute.equals("html")) {
			return target.html();
		}
		if (attribute.equals("href") || attribute.equals("src")) {
			String absolute = target.absUrl(attribute);
			return absolute.isEmpty() ? null : absolute;
		}
		return target.hasAttr(attribute) ? target.attr(attribute) : null;
	}

}
