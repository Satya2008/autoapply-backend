package com.naukriradar.worker.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import com.naukriradar.worker.config.BrowserProperties;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WindowType;
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
			driver = new ChromeDriver(options(properties.headless()));
			driver.manage().timeouts().pageLoadTimeout(properties.pageTimeout());
			return work.apply(driver);
		}
		finally {
			if (driver != null) {
				if (!properties.headless()) {
					// a visible browser is being watched: leave the result on screen for a moment
					pause(VISIBLE_LINGER_MS);
				}
				driver.quit();
			}
			slots.release();
		}
	}


	private static final long VISIBLE_LINGER_MS = 10_000;

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public int free() {
		return slots.availablePermits();
	}

	/**
	 * A visible Chrome window handed to the user: filled by us, finished by them. It is not
	 * pooled and never closed here; "detach" keeps it open even when this service stops. The
	 * user closes it after submitting. It always uses NaukriRadar's own profile, so a job site
	 * the user logged in to once stays logged in. Chrome lets one window use a profile at a time,
	 * so while that window is open each new form opens in a new tab of it, reached through its
	 * debugging port (on 127.0.0.1 only).
	 *
	 * @throws ProfileInUseException when the profile is held by a Chrome we can't reach
	 */
	public WebDriver openForUser() {
		if (profileInUse(properties.userProfile())) {
			return newTabInOpenWindow();
		}
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized", "--disable-extensions",
				"--user-data-dir=" + properties.userProfile().toAbsolutePath().normalize(),
				"--remote-debugging-address=127.0.0.1", "--remote-debugging-port=" + properties.userDebugPort());
		options.setExperimentalOption("detach", true);
		if (properties.chromeBinary() != null && !properties.chromeBinary().isBlank()) {
			options.setBinary(properties.chromeBinary());
		}
		WebDriver driver;
		try {
			driver = new ChromeDriver(options);
		}
		catch (SessionNotCreatedException ex) {
			if (String.valueOf(ex.getMessage()).contains("user data directory is already in use")) {
				throw new ProfileInUseException();
			}
			throw ex;
		}
		driver.manage().timeouts().pageLoadTimeout(properties.pageTimeout());
		return driver;
	}

	/** Joins the open NaukriRadar window and opens a tab for this form. */
	private WebDriver newTabInOpenWindow() {
		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOption("debuggerAddress", "127.0.0.1:" + properties.userDebugPort());
		WebDriver driver;
		try {
			driver = new ChromeDriver(options);
		}
		catch (WebDriverException ex) {
			// a window from before tabs were shared: it has no debugging port
			throw new ProfileInUseException();
		}
		driver.switchTo().newWindow(WindowType.TAB);
		driver.manage().timeouts().pageLoadTimeout(properties.pageTimeout());
		return driver;
	}

	/**
	 * A running Chrome holds its profile's "lockfile" open (Windows) or keeps a "SingletonLock"
	 * link (Linux, macOS). Chrome itself then fails with "crashed", which says nothing useful.
	 */
	static boolean profileInUse(Path profile) {
		if (Files.isSymbolicLink(profile.resolve("SingletonLock"))) {
			return true;
		}
		Path lock = profile.resolve("lockfile");
		try {
			Files.deleteIfExists(lock);
			return false;
		}
		catch (IOException ex) {
			// held open by the running Chrome
			return true;
		}
	}

	/** The NaukriRadar Chrome window from the last "Fill & Review" is still open. */
	public static class ProfileInUseException extends RuntimeException {

		public ProfileInUseException() {
			super("An older NaukriRadar Chrome window is still open. Close it once (submit first if you need to); from then on each form opens as a new tab.");
		}

	}

	private ChromeOptions options(boolean headless) {
		ChromeOptions options = new ChromeOptions();
		if (headless) {
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
