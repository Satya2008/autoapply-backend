package com.naukriradar.worker.browser;

import java.util.concurrent.ThreadLocalRandom;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

/**
 * Fills a form at a person's pace: the pointer moves to a field before clicking it, letters
 * are typed one by one with uneven gaps, and there is a short pause between fields. A form
 * filled in two seconds with no pointer movement looks like a bot to the site's checks. When
 * switched off (tests), everything happens at once.
 */
final class HumanPace {

	/** Longer answers are typed a few letters at a time, as a fast typist would; else they take minutes. */
	private static final int LETTER_BY_LETTER = 25;

	private final boolean on;

	HumanPace(boolean on) {
		this.on = on;
	}

	/** Scrolls the field into view smoothly, moves the pointer onto it and clicks it. */
	void focus(WebDriver driver, WebElement el) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("arguments[0].scrollIntoView({block: 'center', behavior: '" + (on ? "smooth" : "auto") + "'});", el);
		rest(120, 300);
		click(driver, el);
	}

	/** Moves the pointer to the element, then clicks; whatever covers it, the click reaches it. */
	void click(WebDriver driver, WebElement el) {
		try {
			if (on) {
				int dx = ThreadLocalRandom.current().nextInt(-3, 4);
				int dy = ThreadLocalRandom.current().nextInt(-2, 3);
				new Actions(driver).moveToElement(el, dx, dy).pause(between(80, 220)).click().perform();
			}
			else {
				el.click();
			}
		}
		catch (ElementClickInterceptedException ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
		}
		catch (WebDriverException ex) {
			// an element the pointer can't reach (zero size, off-screen): click it directly
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
		}
	}

	/** Types like a person: one letter at a time with uneven gaps, now and then a longer one. */
	void type(WebElement el, String text) {
		if (!on) {
			el.sendKeys(text);
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int i = 0;
		while (i < text.length()) {
			int size = text.length() <= LETTER_BY_LETTER ? 1 : random.nextInt(3, 9);
			int end = Math.min(text.length(), i + size);
			// never split a surrogate pair (emoji, some scripts)
			if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
				end++;
			}
			el.sendKeys(text.substring(i, end));
			i = end;
			rest(25, 60);
			if (random.nextInt(25) == 0) {
				// a moment's thought
				rest(150, 350);
			}
		}
	}

	/** The pause a person takes moving on to the next field. */
	void betweenFields() {
		rest(150, 450);
	}

	/** A short pause, e.g. after choosing from a list. */
	void brief() {
		rest(100, 250);
	}

	private void rest(long fromMs, long toMs) {
		if (!on) {
			return;
		}
		try {
			Thread.sleep(between(fromMs, toMs).toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static java.time.Duration between(long fromMs, long toMs) {
		return java.time.Duration.ofMillis(ThreadLocalRandom.current().nextLong(fromMs, toMs + 1));
	}

}
