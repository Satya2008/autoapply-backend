package com.naukriradar.worker.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.naukriradar.worker.config.BrowserProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

	/** The file input for the resume; its answer is the path of the file to attach. */
	public static final String RESUME = "resume";

	private final BrowserPool browsers;
	private final BrowserProperties properties;
	private final HumanPace human;

	public BrowserApplyEngine(BrowserPool browsers, BrowserProperties properties) {
		this.browsers = browsers;
		this.properties = properties;
		this.human = new HumanPace(properties.humanPace());
	}

	/**
	 * @param submit false for a dry run: fill everything, press nothing
	 */
	public FormResult apply(String url, Map<String, String> selectors, Map<String, String> answers, boolean submit) {
		return apply(url, selectors, answers, submit, (driver, a) -> List.of());
	}

	/**
	 * @param byLabels fills the questions the mapping doesn't name (gender, "how did you hear",
	 *     sponsorship...) from the answers, by their labels, before the "anything still empty?" check
	 */
	public FormResult apply(String url, Map<String, String> selectors, Map<String, String> answers, boolean submit,
			BiFunction<WebDriver, Map<String, String>, List<String>> byLabels) {
		return browsers.withBrowser(driver -> {
			try {
				return fill(driver, url, selectors, answers, submit, byLabels);
			}
			catch (WebDriverException ex) {
				return FormResult.failed("The browser couldn't finish: " + firstLine(ex.getMessage()), screenshot(driver));
			}
		});
	}

	private FormResult fill(WebDriver driver, String url, Map<String, String> selectors, Map<String, String> answers,
			boolean submit, BiFunction<WebDriver, Map<String, String>, List<String>> byLabels) {
		driver.get(url);
		List<String> filled = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		fillMapped(driver, selectors, answers, filled, missing);
		filled.addAll(byLabels.apply(driver, answers));
		// what the form still requires that we couldn't answer: never press submit on a form that would bounce
		List<String> unanswered = requiredButEmpty(driver);
		if (!submit) {
			boolean button = selectors.containsKey(SUBMIT) && find(driver, selectors.get(SUBMIT)) != null;
			String still = unanswered.isEmpty() ? "" : " Still to answer: " + String.join("; ", unanswered) + ".";
			return new FormResult(Outcome.DRY_RUN, filled, missing, (button ? "Filled; submit button found, not pressed."
					: "Filled; no submit button found.") + still, screenshot(driver));
		}
		if (!missing.isEmpty()) {
			// a half-filled form is worse than none: the candidate finishes it
			return new FormResult(Outcome.NEEDS_YOU, filled, missing,
					"The form has fields we couldn't find (" + String.join(", ", missing) + ").", screenshot(driver));
		}
		if (!unanswered.isEmpty()) {
			return new FormResult(Outcome.NEEDS_YOU, filled, unanswered,
					"Filled what we could; the form also asks: " + String.join("; ", unanswered) + ".", screenshot(driver));
		}
		WebElement button = selectors.containsKey(SUBMIT) ? find(driver, selectors.get(SUBMIT)) : null;
		if (button == null) {
			return new FormResult(Outcome.NEEDS_YOU, filled, missing, "No submit button found on the form.",
					screenshot(driver));
		}
		press(driver, button);
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

	/**
	 * Fills the fields a portal mapping names. What it filled goes to {@code filled}; required
	 * fields it couldn't find go to {@code missing}.
	 */
	public void fillMapped(WebDriver driver, Map<String, String> selectors, Map<String, String> answers, List<String> filled,
			List<String> missing) {
		for (Map.Entry<String, String> field : selectors.entrySet()) {
			String name = field.getKey();
			String answer = answers.get(name);
			if (SUBMIT.equals(name) || SUCCESS.equals(name) || answer == null || answer.isBlank()) {
				continue;
			}
			FieldSpec spec = FieldSpec.parse(field.getValue());
			if (RESUME.equals(name)) {
				// a file input: the answer is a local path; these inputs are usually hidden behind a styled button
				WebElement upload = findPresent(driver, spec.css());
				if (upload == null || !Files.isRegularFile(Path.of(answer))) {
					if (!spec.optional()) {
						missing.add(name);
					}
				}
				else {
					upload.sendKeys(Path.of(answer).toAbsolutePath().toString());
					// sites upload the file in the background; pressing submit before that finishes loses it
					pause(UPLOAD_WAIT_MS);
					filled.add(name);
				}
				continue;
			}
			WebElement input = spec.optional() ? findQuickly(driver, spec.css()) : find(driver, spec.css());
			if (input == null) {
				if (!spec.optional()) {
					missing.add(name);
				}
				continue;
			}
			human.betweenFields();
			human.focus(driver, input);
			input.clear();
			human.type(input, answer);
			if (spec.pick()) {
				// a type-ahead box: wait for its list, then take the suggestion that fits the answer;
				// none fits (a school the list doesn't know) -> leave it empty rather than pick a wrong one
				pause(PICK_WAIT_MS);
				List<WebElement> offered = driver.findElements(By.cssSelector("[role=option], [class*='__option']"));
				int best = FormReviewer.bestOption(offered.stream().map(WebElement::getText).toList(), answer);
				if (best < 0) {
					input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, Keys.ESCAPE);
					if (!spec.optional()) {
						missing.add(name);
					}
					continue;
				}
				human.click(driver, offered.get(best));
				human.brief();
			}
			else if (!"textarea".equalsIgnoreCase(input.getTagName())) {
				// leave the box like a person does, so the form checks it now instead of showing a stale "required"
				input.sendKeys(Keys.TAB);
			}
			filled.add(name);
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

	/**
	 * Clicks the submit button the way a person would: scrolled to the middle of the screen
	 * first, since a long form puts it far below and sticky badges (reCAPTCHA, cookie bars)
	 * cover the edges. If something still lies on top of it, the click goes to the button itself.
	 */
	private static void press(WebDriver driver, WebElement button) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", button);
		try {
			button.click();
		}
		catch (ElementClickInterceptedException ex) {
			js.executeScript("arguments[0].click();", button);
		}
	}

	private static final long UPLOAD_WAIT_MS = 5_000;

	private static final long PICK_WAIT_MS = 1_500;

	/** At most this many unanswered questions are listed; the rest is "and N more". */
	private static final int MAX_LISTED = 8;

	/**
	 * A selector as written in a portal's mapping: {@code ?} in front makes the field optional
	 * (forms differ between companies), {@code pick:} marks a type-ahead box where the fitting
	 * suggestion is chosen after typing. Both may be combined: {@code ?pick:#candidate-location}.
	 */
	record FieldSpec(String css, boolean optional, boolean pick) {

		static FieldSpec parse(String raw) {
			String css = raw.strip();
			boolean optional = css.startsWith("?");
			if (optional) {
				css = css.substring(1).strip();
			}
			boolean pick = css.startsWith("pick:");
			if (pick) {
				css = css.substring("pick:".length()).strip();
			}
			return new FieldSpec(css, optional, pick);
		}

	}

	/**
	 * Labels of visible required fields still empty: unchecked required boxes and radio groups,
	 * blank required inputs. Hidden helper inputs and the CAPTCHA's own field are ignored.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> requiredButEmpty(WebDriver driver) {
		Object result = ((JavascriptExecutor) driver).executeScript("""
				const seenGroups = new Set();
				const out = [];
				const labelOf = (el) => {
				  const byFor = el.id && document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
				  const text = (byFor && byFor.innerText) || (el.closest('label') && el.closest('label').innerText)
				    || el.getAttribute('aria-label') || (el.closest('fieldset') && el.closest('fieldset').querySelector('legend')?.innerText)
				    || el.name || el.id || 'a required field';
				  return text.replace(/\\s+/g, ' ').replace(/\\*/g, '').trim().slice(0, 80);
				};
				// a type-ahead box keeps its text empty once a choice is made and shows the choice beside it
				const chosen = (el) => {
				  if (el.getAttribute('role') !== 'combobox' && !/select/i.test(el.className + ' ' + (el.parentElement?.className || ''))) return false;
				  // the whole control (react-select: select__control), not the input's own wrapper
				  const box = el.closest('[class*="__control"], [class*="-control"]') || el.closest('[class*="value-container"]');
				  const shown = box && box.querySelector('[class*="single-value"], [class*="singleValue"], [class*="multi-value"]');
				  return !!(shown && shown.textContent.trim());
				};
				for (const el of document.querySelectorAll('input, textarea, select')) {
				  const required = el.required || el.getAttribute('aria-required') === 'true';
				  if (!required || el.disabled || el.type === 'hidden' || /recaptcha|captcha/i.test(el.name + el.id)) continue;
				  const visible = el.offsetParent !== null || el.type === 'file';
				  if (!visible) continue;
				  if (el.type === 'radio' || el.type === 'checkbox') {
				    const group = el.name || el.id;
				    if (seenGroups.has(group)) continue;
				    seenGroups.add(group);
				    const members = el.name ? document.querySelectorAll('input[name="' + CSS.escape(el.name) + '"]') : [el];
				    if (![...members].some(m => m.checked)) out.push(labelOf(el));
				  } else if (el.type === 'file') {
				    if (!el.files || el.files.length === 0) out.push(labelOf(el));
				  } else if (!String(el.value || '').trim() && !chosen(el)) {
				    out.push(labelOf(el));
				  }
				}
				return [...new Set(out)];
				""");
		List<String> labels = result instanceof List<?> list ? (List<String>) list : List.of();
		if (labels.size() <= MAX_LISTED) {
			return labels;
		}
		List<String> shown = new ArrayList<>(labels.subList(0, MAX_LISTED));
		shown.add("and " + (labels.size() - MAX_LISTED) + " more");
		return shown;
	}

	/** Optional fields aren't waited for: if the form has them, they are already there. */
	private static WebElement findQuickly(WebDriver driver, String css) {
		List<WebElement> found = driver.findElements(By.cssSelector(css));
		return found.isEmpty() ? null : found.get(0);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/** Waits for the element to exist, visible or not (file inputs are often hidden). */
	private WebElement findPresent(WebDriver driver, String css) {
		try {
			return new WebDriverWait(driver, properties.fieldTimeout())
					.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(css)));
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
