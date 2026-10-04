package com.naukriradar.worker.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.naukriradar.worker.config.BrowserProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

/**
 * Fills a portal's application form from the candidate's prepared answers and submits it.
 * Nothing here knows a site: what to fill where comes from the portal's selectors, set by an
 * admin. Two keys are special: {@code submit} (the button) and {@code success} (an element
 * that only appears once the application went through).
 */
@Component
public class BrowserApplyEngine {

	public static final String SUBMIT = "submit";

	public static final String SUCCESS = "success";

	private final BrowserPool browsers;
	private final BrowserProperties properties;

	public BrowserApplyEngine(BrowserPool browsers, BrowserProperties properties) {
		this.browsers = browsers;
		this.properties = properties;
	}

	/**
	 * @param submit false for a dry run: fill everything, press nothing
	 */
	public FormResult apply(String url, Map<String, String> selectors, Map<String, String> answers, boolean submit) {
		return browsers.withBrowser(driver -> {
			try {
				return fill(driver, url, selectors, answers, submit);
			}
			catch (WebDriverException ex) {
				return FormResult.failed("The browser couldn't finish: " + firstLine(ex.getMessage()), screenshot(driver));
			}
		});
	}

	private FormResult fill(WebDriver driver, String url, Map<String, String> selectors, Map<String, String> answers,
			boolean submit) {
		driver.get(url);
		List<String> filled = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> field : selectors.entrySet()) {
			String name = field.getKey();
			String answer = answers.get(name);
			if (SUBMIT.equals(name) || SUCCESS.equals(name) || answer == null || answer.isBlank()) {
				continue;
			}
			WebElement input = find(driver, field.getValue());
			if (input == null) {
				missing.add(name);
				continue;
			}
			input.clear();
			input.sendKeys(answer);
			filled.add(name);
		}
		if (!submit) {
			boolean button = selectors.containsKey(SUBMIT) && find(driver, selectors.get(SUBMIT)) != null;
			return new FormResult(Outcome.DRY_RUN, filled, missing, button ? "Filled; submit button found, not pressed."
					: "Filled; no submit button found.", screenshot(driver));
		}
		if (!missing.isEmpty()) {
			// a half-filled form is worse than none: the candidate finishes it
			return new FormResult(Outcome.NEEDS_YOU, filled, missing,
					"The form has fields we couldn't find (" + String.join(", ", missing) + ").", screenshot(driver));
		}
		WebElement button = selectors.containsKey(SUBMIT) ? find(driver, selectors.get(SUBMIT)) : null;
		if (button == null) {
			return new FormResult(Outcome.NEEDS_YOU, filled, missing, "No submit button found on the form.",
					screenshot(driver));
		}
		button.click();
		if (!selectors.containsKey(SUCCESS)) {
			// we pressed submit but can't tell whether it worked; never guess "submitted"
			return new FormResult(Outcome.UNKNOWN, filled, missing,
					"Submitted, but this portal has no success check configured.", screenshot(driver));
		}
		try {
			new WebDriverWait(driver, properties.successTimeout())
					.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selectors.get(SUCCESS))));
			return new FormResult(Outcome.SUBMITTED, filled, missing, "Submitted; the portal confirmed it.", null);
		}
		catch (TimeoutException ex) {
			return FormResult.failed("Submitted, but the portal showed no confirmation within "
					+ properties.successTimeout().toSeconds() + "s.", screenshot(driver));
		}
	}

	private WebElement find(WebDriver driver, String css) {
		try {
			return new WebDriverWait(driver, properties.fieldTimeout())
					.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
		}
		catch (TimeoutException ex) {
			return null;
		}
	}

	private static byte[] screenshot(WebDriver driver) {
		try {
			return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "unknown error";
		}
		int end = message.indexOf('\n');
		return end < 0 ? message : message.substring(0, end);
	}

	public enum Outcome {

		/** The portal confirmed the application. */
		SUBMITTED,

		/** Didn't go through; worth trying again later. */
		FAILED,

		/** Can't be automated as configured; the candidate applies by hand. */
		NEEDS_YOU,

		/** Submit was pressed but whether it worked is unknown; the candidate checks. */
		UNKNOWN,

		/** Filled without submitting. */
		DRY_RUN

	}

	/** @param screenshot PNG of the page when something went wrong, else null */
	public record FormResult(Outcome outcome, List<String> filled, List<String> missing, String note, byte[] screenshot) {

		static FormResult failed(String note, byte[] screenshot) {
			return new FormResult(Outcome.FAILED, List.of(), List.of(), note, screenshot);
		}

	}

}
