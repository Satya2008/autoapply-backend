package com.autoapply.apply.engine;

import com.autoapply.apply.portal.ApplyPortalConfig;
import com.autoapply.apply.portal.ApplyPortalConfigRepository;
import com.autoapply.jobs.entity.Job;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Drives a real browser to submit an application, following the selectors stored for the
 * matching portal. Nothing about any particular site is hard-coded here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrowserApplyEngine {

    private final ApplyPortalConfigRepository portalRepository;
    private final SettingsService settings;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public ApplyOutcome apply(User user, Job job, String coverLetter) {
        if (!settings.getBoolean(SettingKeys.SELENIUM_ENABLED, false)) {
            return ApplyOutcome.skipped("Browser automation is disabled in settings");
        }
        String url = job.getJobApplyLink();
        if (url == null || url.isBlank()) {
            return ApplyOutcome.failed("This job has no apply link", null);
        }

        ApplyPortalConfig portal = findPortal(url);
        if (portal == null) {
            return ApplyOutcome.skipped("No portal configuration matches " + hostOf(url)
                    + ". Add one in the admin dashboard to support this site.");
        }

        WebDriver driver = null;
        try {
            driver = createDriver();
            driver.manage().timeouts().pageLoadTimeout(
                    Duration.ofSeconds(settings.getInt(SettingKeys.SELENIUM_PAGE_TIMEOUT_SECONDS, 45)));
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver,
                    Duration.ofSeconds(portal.getMaxWaitSeconds() == null ? 20 : portal.getMaxWaitSeconds()));

            dismissOverlays(driver, portal);

            if (notBlank(portal.getReadySelector())) {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(portal.getReadySelector())));
            }
            if (notBlank(portal.getOpenFormSelector())) {
                clickIfPresent(driver, portal.getOpenFormSelector());
                pause(1200);
            }

            int filled = fillForm(driver, wait, portal, user, coverLetter);

            if (Boolean.TRUE.equals(portal.getDryRun())) {
                recordSuccess(portal);
                return ApplyOutcome.dryRun("Form filled (" + filled + " fields). Submit skipped: this portal is in dry-run mode.");
            }

            if (!notBlank(portal.getSubmitSelector())) {
                return ApplyOutcome.failed("Portal config has no submit selector", screenshot(driver, job));
            }
            WebElement submit = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector(portal.getSubmitSelector())));
            submit.click();
            pause(2500);

            if (notBlank(portal.getFailureText()) && driver.getPageSource().contains(portal.getFailureText())) {
                recordFailure(portal, "Portal reported a failure message");
                return ApplyOutcome.failed("Portal rejected the application", screenshot(driver, job));
            }
            if (notBlank(portal.getSuccessSelector())) {
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(portal.getSuccessSelector())));
                } catch (TimeoutException e) {
                    recordFailure(portal, "Success indicator never appeared");
                    return ApplyOutcome.failed("Could not confirm the application was accepted",
                            screenshot(driver, job));
                }
            }

            recordSuccess(portal);
            return ApplyOutcome.success("Application submitted through " + portal.getName()
                    + " (" + filled + " fields filled)");

        } catch (Exception e) {
            String shot = driver == null ? null : screenshot(driver, job);
            recordFailure(portal, e.getMessage());
            log.warn("Browser apply failed for job {}: {}", job.getJobId(), e.getMessage());
            return ApplyOutcome.failed(e.getClass().getSimpleName() + ": " + e.getMessage(), shot);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                    // the browser may already be gone
                }
            }
        }
    }

    // ------------------------------------------------------------- filling

    private int fillForm(WebDriver driver, WebDriverWait wait, ApplyPortalConfig portal,
                         User user, String coverLetter) {
        List<Map<String, Object>> fields = readFields(portal);
        Map<String, String> values = candidateValues(user, coverLetter);
        boolean humanize = settings.getBoolean(SettingKeys.SELENIUM_HUMANIZE, true);
        int filled = 0;

        for (Map<String, Object> field : fields) {
            String selector = str(field.get("selector"));
            String valueFrom = str(field.get("valueFrom"));
            String type = Optional.ofNullable(str(field.get("type"))).orElse("text");
            boolean required = Boolean.TRUE.equals(field.get("required"));
            String literal = str(field.get("value"));

            String value = literal != null ? literal : values.get(valueFrom);
            if (selector == null) continue;

            try {
                WebElement element = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));

                switch (type.toLowerCase()) {
                    case "file" -> {
                        String path = values.get("resumeFile");
                        if (path == null || !new File(path).exists()) {
                            if (required) throw new IllegalStateException("resume file is not available");
                            continue;
                        }
                        element.sendKeys(path);
                    }
                    case "select" -> {
                        if (value == null) continue;
                        new Select(element).selectByVisibleText(value);
                    }
                    case "checkbox" -> {
                        boolean shouldCheck = value == null || Boolean.parseBoolean(value);
                        if (element.isSelected() != shouldCheck) element.click();
                    }
                    case "click" -> element.click();
                    default -> {
                        if (value == null || value.isBlank()) {
                            if (required) throw new IllegalStateException("no value for " + valueFrom);
                            continue;
                        }
                        element.clear();
                        if (humanize) {
                            for (char c : value.toCharArray()) {
                                element.sendKeys(String.valueOf(c));
                                pause(25 + random.nextInt(60));
                            }
                        } else {
                            element.sendKeys(value);
                        }
                    }
                }
                filled++;
                pause(humanize ? 300 + random.nextInt(500) : 100);

            } catch (Exception e) {
                if (required) {
                    throw new IllegalStateException(
                            "Required field '" + selector + "' could not be filled: " + e.getMessage(), e);
                }
                log.debug("Optional field '{}' skipped: {}", selector, e.getMessage());
            }
        }
        return filled;
    }

    private Map<String, String> candidateValues(User user, String coverLetter) {
        String fullName = Optional.ofNullable(user.getFullName()).orElse("");
        String[] nameParts = fullName.trim().split("\\s+", 2);

        Map<String, String> values = new HashMap<>();
        values.put("fullName", fullName);
        values.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
        values.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
        values.put("email", nullSafe(user.getEmail()));
        values.put("phone", nullSafe(user.getPhone()));
        values.put("location", nullSafe(user.getLocation()));
        values.put("currentRole", nullSafe(user.getCurrentRole()));
        values.put("experienceYears", user.getExperienceYears() == null ? "" : String.valueOf(user.getExperienceYears()));
        values.put("expectedSalary", user.getExpectedSalary() == null ? "" : String.valueOf(user.getExpectedSalary()));
        values.put("noticePeriod", nullSafe(user.getNoticePeriod()));
        values.put("linkedinUrl", nullSafe(user.getLinkedinUrl()));
        values.put("githubUrl", nullSafe(user.getGithubUrl()));
        values.put("portfolioUrl", nullSafe(user.getPortfolioUrl()));
        values.put("skills", user.getSkills() == null ? "" : String.join(", ", user.getSkills()));
        values.put("coverLetter", coverLetter == null ? nullSafe(user.getCoverLetterTemplate()) : coverLetter);
        values.put("resumeFile", nullSafe(user.getResumeFilePath()));
        return values;
    }

    private List<Map<String, Object>> readFields(ApplyPortalConfig portal) {
        if (portal.getFieldMappingJson() == null || portal.getFieldMappingJson().isBlank()) return List.of();
        try {
            return objectMapper.readValue(portal.getFieldMappingJson(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            log.warn("Portal {} has an invalid field mapping: {}", portal.getCode(), e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------- driver

    private WebDriver createDriver() throws Exception {
        String browser = settings.getString(SettingKeys.SELENIUM_BROWSER, "chrome").toLowerCase();
        boolean headless = settings.getBoolean(SettingKeys.SELENIUM_HEADLESS, true);
        String windowSize = settings.getString(SettingKeys.SELENIUM_WINDOW_SIZE, "1440,900");
        String userAgent = settings.getString(SettingKeys.SELENIUM_USER_AGENT, "");
        String remoteUrl = settings.getString(SettingKeys.SELENIUM_REMOTE_URL, "");

        switch (browser) {
            case "firefox" -> {
                FirefoxOptions options = new FirefoxOptions();
                if (headless) options.addArguments("-headless");
                options.addArguments("--width=" + windowSize.split(",")[0]);
                if (notBlank(remoteUrl)) return new RemoteWebDriver(new URL(remoteUrl), options);
                WebDriverManager.firefoxdriver().setup();
                return new FirefoxDriver(options);
            }
            case "edge" -> {
                EdgeOptions options = new EdgeOptions();
                if (headless) options.addArguments("--headless=new");
                options.addArguments("--window-size=" + windowSize);
                if (notBlank(remoteUrl)) return new RemoteWebDriver(new URL(remoteUrl), options);
                WebDriverManager.edgedriver().setup();
                return new EdgeDriver(options);
            }
            default -> {
                ChromeOptions options = new ChromeOptions();
                if (headless) options.addArguments("--headless=new");
                options.addArguments("--window-size=" + windowSize);
                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage",
                        "--disable-blink-features=AutomationControlled");
                options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
                if (notBlank(userAgent)) options.addArguments("--user-agent=" + userAgent);
                if (notBlank(remoteUrl)) return new RemoteWebDriver(new URL(remoteUrl), options);
                WebDriverManager.chromedriver().setup();
                return new ChromeDriver(options);
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private ApplyPortalConfig findPortal(String url) {
        String lower = url.toLowerCase();
        for (ApplyPortalConfig portal : portalRepository.findByEnabledTrueOrderByPriorityAsc()) {
            String pattern = portal.getUrlPattern();
            if (pattern == null || pattern.isBlank()) continue;
            if (lower.contains(pattern.toLowerCase())) return portal;
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(url).find()) return portal;
            } catch (Exception ignored) {
                // not a regex, the substring check above already ran
            }
        }
        return null;
    }

    private void dismissOverlays(WebDriver driver, ApplyPortalConfig portal) {
        if (!notBlank(portal.getDismissSelectors())) return;
        for (String selector : portal.getDismissSelectors().split("[\\n,]")) {
            clickIfPresent(driver, selector.trim());
        }
    }

    private void clickIfPresent(WebDriver driver, String selector) {
        if (!notBlank(selector)) return;
        try {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                pause(500);
            }
        } catch (Exception ignored) {
            // overlays are best-effort
        }
    }

    private String screenshot(WebDriver driver, Job job) {
        if (!settings.getBoolean(SettingKeys.SELENIUM_SCREENSHOT_ON_FAILURE, true)) return null;
        try {
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path directory = Paths.get("./data/screenshots");
            Files.createDirectories(directory);
            String name = "apply_" + job.getJobId().replaceAll("[^a-zA-Z0-9]", "_")
                    + "_" + System.currentTimeMillis() + ".png";
            Path target = directory.resolve(name);
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void recordSuccess(ApplyPortalConfig portal) {
        portal.setSuccessCount((portal.getSuccessCount() == null ? 0 : portal.getSuccessCount()) + 1);
        portal.setLastUsedAt(LocalDateTime.now());
        portal.setLastError(null);
        portalRepository.save(portal);
    }

    private void recordFailure(ApplyPortalConfig portal, String error) {
        if (portal == null) return;
        portal.setFailureCount((portal.getFailureCount() == null ? 0 : portal.getFailureCount()) + 1);
        portal.setLastUsedAt(LocalDateTime.now());
        portal.setLastError(error == null ? "unknown" : error.substring(0, Math.min(900, error.length())));
        portalRepository.save(portal);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
