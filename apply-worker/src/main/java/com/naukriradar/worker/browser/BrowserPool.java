package com.naukriradar.worker.browser;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import com.naukriradar.worker.config.BrowserProperties;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

/**
 * At most {@code poolSize} headless Chrome browsers at a time. Each task gets a fresh browser
 * and it is closed right after, whatever happens: a browser kept between applications slowly
 * grows in memory, and a crashed page can't leak into the next application.
 */
@Component
public class BrowserPool {

	private final Semaphore slots;
	private final BrowserProperties properties;

	public BrowserPool(BrowserProperties properties) {
		if (properties.poolSize() < 1) {
			throw new IllegalArgumentException("naukriradar.browser.pool-size must be at least 1");
		}
		this.slots = new Semaphore(properties.poolSize(), true);
		this.properties = properties;
	}

	public <T> T withBrowser(Function<WebDriver, T> work) {
		try {
			slots.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for a browser", ex);
		}
		WebDriver driver = null;
		try {
			driver = new ChromeDriver(options());
			driver.manage().timeouts().pageLoadTimeout(properties.pageTimeout());
			return work.apply(driver);
		}
		finally {
			if (driver != null) {
				driver.quit();
			}
			slots.release();
		}
	}

	public int free() {
		return slots.availablePermits();
	}

	private ChromeOptions options() {
		ChromeOptions options = new ChromeOptions();
		if (properties.headless()) {
			options.addArguments("--headless=new");
		}
		// small and predictable: no GPU, no extensions, a normal desktop size
		options.addArguments("--disable-gpu", "--disable-extensions", "--disable-dev-shm-usage", "--no-sandbox",
				"--window-size=1280,900");
		if (properties.chromeBinary() != null && !properties.chromeBinary().isBlank()) {
			options.setBinary(properties.chromeBinary());
		}
		options.setPageLoadTimeout(properties.pageTimeout().plus(Duration.ofSeconds(5)));
		return options;
	}

}
