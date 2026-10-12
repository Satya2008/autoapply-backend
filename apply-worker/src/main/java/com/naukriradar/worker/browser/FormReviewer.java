package com.naukriradar.worker.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.naukriradar.worker.config.BrowserProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * "Fill & Review": opens the application form in a visible Chrome on the user's machine, fills
 * everything it can, and leaves the last step (checking, the CAPTCHA, Submit) to the user.
 * <ol>
 * <li>fields a portal mapping names, exactly</li>
 * <li>any other field whose label says what it is (first name, notice period, current CTC...)</li>
 * <li>required questions still empty go to AI, which answers from the resume and profile</li>
 * </ol>
 * Submit is never pressed here, and consent boxes are left for the user to tick.
 */
@Component
public class FormReviewer {

	private static final Logger log = LoggerFactory.getLogger(FormReviewer.class);

	/** After the page reports it is loaded, a moment for scripts to draw the form. */
	private static final long PAGE_SETTLE_MS = 700;

	private static final long PAGE_LOAD_MAX_MS = 8_000;
	private static final long PICK_WAIT_MS = 1_000;
	private static final long UPLOAD_WAIT_MS = 3_000;

	private static final Pattern RESUME_LABEL = Pattern.compile("resume|\\bcv\\b|curriculum");

	/** Label -> answer key, first match wins, so "first name" is tried before "name". */
	private static final List<Map.Entry<Pattern, String>> LABELS = List.of(
			rule("first\\s*name|given\\s*name", "firstName"),
			rule("last\\s*name|surname|family\\s*name", "lastName"),
			rule("preferred.*name", null),
			// the profile's answer bank; sponsorship first: its question often says "work authorization" too
			rule("sponsor", "needsSponsorship"),
			rule("authori[sz]ed to work|eligible to work|legally (allowed|permitted|able) to work|work authori[sz]ation|right to work",
					"authorizedToWork"),
			rule("gender|\\bsex\\b", "gender"),
			rule("how did you (hear|find|learn|come to know)|where did you (hear|find|see)|referral source|source of (this )?application",
					"heardAbout"),
			rule("disabilit", "disability"),
			rule("veteran", "veteranStatus"),
			rule("full\\s*name|^\\s*name\\s*\\*?$|your\\s*name|candidate\\s*name", "fullName"),
			rule("e-?mail", "email"),
			rule("linked\\s*in", "linkedinUrl"),
			rule("git\\s*hub", "githubUrl"),
			rule("portfolio|personal\\s*(web)?site|^\\s*website", "portfolioUrl"),
			rule("current\\s*(ctc|salary|compensation|package)", "currentCtc"),
			rule("expected\\s*(ctc|salary|compensation|package)|salary\\s*expectation", "expectedCtc"),
			rule("notice\\s*period", "noticePeriod"),
			rule("relevant.*experience|total.*experience|years\\s*of\\s*(professional\\s*)?experience|experience\\s*\\(?in\\s*years", "experience"),
			rule("current\\s*(company|employer|organi[sz]ation)|^\\s*company\\s*name", "currentCompany"),
			rule("current\\s*(designation|title|role|position)|job\\s*title", "currentTitle"),
			rule("phone|mobile|contact\\s*number", "phone"),
			rule("country", "country"),
			rule("location|city|where.*(based|live)|residence", "location"),
			rule("school|college|university|institut", "college"),
			rule("degree|qualification", "highestDegree"),
			rule("discipline|field\\s*of\\s*study|major|speciali[sz]ation", "fieldOfStudy"),
			rule("start.*year", "educationStartYear"),
			rule("(end|graduation|passing|completion).*year|year\\s*of\\s*(passing|graduation)", "graduationYear"),
			rule("relocat", "willingToRelocate"));

	private final BrowserPool browsers;
	private final BrowserApplyEngine engine;
	private final HumanPace human;

	public FormReviewer(BrowserPool browsers, BrowserApplyEngine engine, BrowserProperties properties) {
		this.browsers = browsers;
		this.engine = engine;
		this.human = new HumanPace(properties.humanPace());
	}

	/** A question the form still asks, as shown to AI. */
	public record Question(String label, List<String> options) {

		public String asText() {
			return options.isEmpty() ? label : label + " (choose one: " + String.join(" | ", options) + ")";
		}

	}

	/**
	 * @param filled labels we filled from the profile
	 * @param aiAnswered question -> AI's answer, for the user to read before submitting
	 * @param leftForYou what is still empty: questions nobody could answer, consent boxes, CAPTCHA
	 */
	public record ReviewResult(boolean opened, List<String> filled, Map<String, String> aiAnswered, List<String> leftForYou,
			String note) {
	}

	/**
	 * @param ask answers questions (by their {@link Question#asText()}); a missing or blank answer
	 *     leaves the question to the user
	 */
	public ReviewResult review(String url, Map<String, String> selectors, Map<String, String> answers,
			Function<List<Question>, Map<String, String>> ask) {
		WebDriver driver;
		try {
			driver = browsers.openForUser();
		}
		catch (BrowserPool.ProfileInUseException ex) {
			return new ReviewResult(false, List.of(), Map.of(), List.of(), ex.getMessage());
		}
		catch (WebDriverException ex) {
			return new ReviewResult(false, List.of(), Map.of(), List.of(), "Chrome could not be opened: " + firstLine(ex.getMessage()));
		}
		return fillForm(driver, url, selectors, answers, ask);
	}

	/** Fills the form open in {@code driver}; package-private for tests, which bring their own browser. */
	ReviewResult fillForm(WebDriver driver, String url, Map<String, String> selectors, Map<String, String> answers,
			Function<List<Question>, Map<String, String>> ask) {
		List<String> filled = new ArrayList<>();
		Map<String, String> aiAnswered = new LinkedHashMap<>();
		try {
			driver.get(url);
			settle(driver);
			dismissCookieBanner(driver);
			clickApplyIfNeeded(driver);
			dismissCookieBanner(driver);
			chooseHowToApply(driver);
			if (asksToLogIn(driver)) {
				// job boards and Workday-style portals show the form only to a signed-in user
				return new ReviewResult(true, List.of(), Map.of(), List.of(LOG_IN),
						"This site wants you to log in (or create an account) first. Do that in the NaukriRadar Chrome window, then press"
								+ " \"Fill for me\" again: NaukriRadar's Chrome remembers the login.");
			}

			// 1. the portal's own mapping, when it has one
			if (selectors != null && !selectors.isEmpty()) {
				List<String> missing = new ArrayList<>();
				engine.fillMapped(driver, selectors, answers, filled, missing);
			}

			// 2. every other empty field whose label we recognise
			filled.addAll(fillByLabels(driver, answers));

			// 3. required questions still empty: AI, from the resume and profile
			List<Field> open = scan(driver).stream()
					.filter(f -> f.empty() && f.required() && !"checkbox".equals(f.kind()) && !"file".equals(f.kind()))
					.toList();
			if (!open.isEmpty()) {
				List<Question> questions = open.stream().map(f -> new Question(f.label(), f.options())).toList();
				Map<String, String> replies = safeAsk(ask, questions);
				for (int i = 0; i < open.size(); i++) {
					String reply = replies.get(questions.get(i).asText());
					if (reply != null && !reply.isBlank() && fill(driver, open.get(i), reply)) {
						aiAnswered.put(open.get(i).label(), reply);
					}
				}
			}

			List<String> left = new ArrayList<>(scan(driver).stream()
					.filter(f -> f.empty() && f.required() && !f.label().isBlank())
					.map(Field::label)
					.distinct()
					.toList());
			if (captchaForAPerson(driver)) {
				left.add("the CAPTCHA (\"I'm not a robot\")");
			}
			if (filled.isEmpty() && aiAnswered.isEmpty() && left.isEmpty()) {
				// nothing to fill: the page is a description, or "Apply" leads somewhere we didn't follow
				return new ReviewResult(true, List.of(), Map.of(), List.of(FIND_FORM),
						"No application form on this page. Click its Apply button in the Chrome window, then press"
								+ " \"Fill for me\" again if a form opens on this site.");
			}
			highlightSubmit(driver);
			String note = left.isEmpty() ? "Filled. Check it and press Submit." : "Filled what we could. Still for you: " + String.join("; ", left) + ".";
			return new ReviewResult(true, filled, aiAnswered, left, note);
		}
		catch (WebDriverException | UnsupportedOperationException ex) {
			log.info("Fill & Review stopped on {}: {}", url, firstLine(ex.getMessage()));
			return new ReviewResult(true, filled, aiAnswered, List.of(), "Filled part of the form; the page got in the way ("
					+ firstLine(ex.getMessage()) + "). Finish it in the open window.");
		}
		// the window stays open on purpose: the user finishes and submits
	}


	/**
	 * Fills every empty field whose label says what it is (first name, notice period, gender,
	 * "how did you hear"...) from the user's answers; no AI. Also used by the auto-apply engine
	 * for the questions a portal mapping doesn't name.
	 *
	 * @return the labels filled
	 */
	public List<String> fillByLabels(WebDriver driver, Map<String, String> answers) {
		List<String> filled = new ArrayList<>();
		for (Field field : scan(driver)) {
			if (!field.empty()) {
				continue;
			}
			String key = "file".equals(field.kind())
					? (RESUME_LABEL.matcher(field.label().toLowerCase(Locale.ROOT)).find() ? BrowserApplyEngine.RESUME : null)
					: keyFor(field.label());
			String answer = key == null ? null : answerFor(key, field, answers);
			if (answer == null) {
				continue;
			}
			// a list of degree levels doesn't know "M.A."; it knows "Master's Degree"
			String level = "highestDegree".equals(key) ? degreeLevel(answer) : null;
			// and a school or degree a list doesn't know at all is usually offered as "Other"
			boolean otherFits = !"text".equals(field.kind()) && !"textarea".equals(field.kind())
					&& OTHER_FITS.matcher(field.label().toLowerCase(Locale.ROOT)).find();
			// "Prefer not to say" is written a dozen ways across forms
			List<String> sameAnswer = DECLINE.matcher(answer).find() ? DECLINE_WORDINGS
					: "veteranStatus".equals(key) && answer.toLowerCase(Locale.ROOT).contains("not") ? NOT_A_VETERAN : List.of();
			if (fill(driver, field, answer) || (level != null && fill(driver, field, level))
					|| sameAnswer.stream().anyMatch(a -> !"text".equals(field.kind()) && fill(driver, field, a))
					|| (otherFits && fill(driver, field, "Other"))) {
				filled.add(field.label());
			}
		}
		return filled;
	}
	// ---------------------------------------------------------------- the form

	/** One question on the page. {@code ref} finds it again: a data attribute we put on it. */
	record Field(String ref, String kind, String label, boolean required, boolean empty, List<String> options) {
	}

	@SuppressWarnings("unchecked")
	private static List<Field> scan(WebDriver driver) {
		Object raw = ((JavascriptExecutor) driver).executeScript("""
				const out = []; let n = 0; const groups = new Set();
				const clean = (t) => (t || '').replace(/\\s+/g, ' ').replace(/\\*/g, '').trim().slice(0, 160);
				const labelOf = (el) => {
				  const byFor = el.id && document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
				  const byAria = el.getAttribute('aria-labelledby') && document.getElementById(el.getAttribute('aria-labelledby').split(' ')[0]);
				  const legend = el.closest('fieldset') && el.closest('fieldset').querySelector('legend');
				  return clean((byFor && byFor.innerText) || (byAria && byAria.innerText) || (el.type === 'radio' && legend && legend.innerText)
				    || (el.closest('label') && el.closest('label').innerText) || el.getAttribute('aria-label') || el.placeholder || el.name || '');
				};
				const chosen = (el) => {
				  const box = el.closest('[class*="__control"], [class*="-control"]') || el.closest('[class*="value-container"]');
				  const shown = box && box.querySelector('[class*="single-value"], [class*="singleValue"], [class*="multi-value"]');
				  return !!(shown && shown.textContent.trim());
				};
				for (const el of document.querySelectorAll('input, textarea, select')) {
				  if (el.disabled || el.type === 'hidden' || el.type === 'submit' || el.type === 'button' || /captcha/i.test(el.name + el.id)) continue;
				  if (el.offsetParent === null && el.type !== 'file') continue;
				  const required = el.required || el.getAttribute('aria-required') === 'true';
				  if (el.type === 'radio') {
				    const group = el.name; if (!group || groups.has(group)) continue; groups.add(group);
				    const members = [...document.querySelectorAll('input[type=radio][name="' + CSS.escape(group) + '"]')];
				    const ref = 'nr' + (n++); members.forEach((m, i) => m.setAttribute('data-nr', ref + '-' + i));
				    const fs = el.closest('fieldset'); const legend = fs && fs.querySelector('legend');
				    const q = clean((legend && legend.innerText) || (el.closest('[class*=question], li, .application-question') || {}).innerText || group).split(' ').slice(0, 30).join(' ');
				    out.push({ ref, kind: 'radio', label: q, required: required || members.some(m => m.required), empty: !members.some(m => m.checked),
				      options: members.map(m => clean((m.closest('label') && m.closest('label').innerText) || (m.id && document.querySelector('label[for="' + CSS.escape(m.id) + '"]')?.innerText) || m.value)) });
				    continue;
				  }
				  const ref = 'nr' + (n++); el.setAttribute('data-nr', ref);
				  let kind = el.tagName === 'SELECT' ? 'select' : el.tagName === 'TEXTAREA' ? 'textarea'
				    : el.type === 'file' ? 'file' : el.type === 'checkbox' ? 'checkbox' : el.getAttribute('role') === 'combobox' ? 'combobox' : 'text';
				  const options = kind === 'select' ? [...el.options].map(o => clean(o.text)).filter(t => t && !/^select|^choose|^--/i.test(t)) : [];
				  const empty = kind === 'checkbox' ? !el.checked : kind === 'file' ? !(el.files && el.files.length)
				    : kind === 'select' ? (el.selectedIndex <= 0 && !String(el.value || '').trim()) : (!String(el.value || '').trim() && !chosen(el));
				  out.push({ ref, kind, label: labelOf(el), required, empty, options });
				}
				return out;
				""");
		List<Field> fields = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object item : list) {
				Map<String, Object> m = (Map<String, Object>) item;
				fields.add(new Field((String) m.get("ref"), (String) m.get("kind"), (String) m.get("label"),
						Boolean.TRUE.equals(m.get("required")), Boolean.TRUE.equals(m.get("empty")),
						((List<Object>) m.get("options")).stream().map(String::valueOf).toList()));
			}
		}
		return fields;
	}

	private static String keyFor(String label) {
		String lower = label.toLowerCase(Locale.ROOT);
		for (Map.Entry<Pattern, String> rule : LABELS) {
			if (rule.getKey().matcher(lower).find()) {
				return rule.getValue();
			}
		}
		return null;
	}

	/** The profile's answer in the shape this field wants (lakhs, years only, Yes/No...). */
	private static String answerFor(String key, Field field, Map<String, String> answers) {
		String label = field.label().toLowerCase(Locale.ROOT);
		boolean numeric = label.contains("lakh") || label.contains("lpa") || label.contains("in years") || label.contains("days");
		return switch (key) {
			case "currentCtc" -> label.contains("lakh") || label.contains("lpa") ? answers.get("currentCtcLakhs") : answers.get("currentCtc");
			case "expectedCtc" -> label.contains("lakh") || label.contains("lpa") ? answers.get("expectedCtcLakhs") : answers.get("expectedSalary");
			case "noticePeriod" -> numeric || label.contains("day") ? answers.get("noticePeriodDays") : answers.get("noticePeriod");
			case "experience" -> answers.getOrDefault("totalExperienceYears", answers.get("totalExperience"));
			case "phone" -> answers.getOrDefault("phoneNational", answers.get("phone"));
			case BrowserApplyEngine.RESUME -> "file".equals(field.kind()) ? answers.get(BrowserApplyEngine.RESUME) : null;
			default -> answers.get(key);
		};
	}

	/** Lower case, curly quotes made straight: "Master’s" and "Master's" are the same option. */
	private static String plain(String text) {
		return text.strip().toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
	}

	/** "M.A." -> "Master's Degree", the way degree lists name levels; null if it can't tell. */
	static String degreeLevel(String degree) {
		String d = degree.strip().toLowerCase(Locale.ROOT).replace(".", "");
		if (d.startsWith("phd") || d.startsWith("doctor")) {
			return "Doctor";
		}
		if (d.startsWith("m") || d.contains("master") || d.startsWith("pg") || d.contains("post graduate")) {
			return "Master's Degree";
		}
		if (d.startsWith("b") || d.contains("bachelor") || d.contains("graduate")) {
			return "Bachelor's Degree";
		}
		if (d.contains("diploma")) {
			return "Diploma";
		}
		return null;
	}

	private boolean fill(WebDriver driver, Field field, String answer) {
		try {
			switch (field.kind()) {
				case "radio" -> {
					int best = bestOption(field.options(), answer);
					if (best < 0) {
						return false;
					}
					human.betweenFields();
					WebElement radio = driver.findElement(By.cssSelector("[data-nr='" + field.ref() + "-" + best + "']"));
					human.focus(driver, radio);
				}
				case "select" -> {
					int best = bestOption(field.options(), answer);
					if (best < 0) {
						return false;
					}
					human.betweenFields();
					WebElement el = driver.findElement(By.cssSelector("[data-nr='" + field.ref() + "']"));
					human.focus(driver, el);
					human.brief();
					new Select(el).selectByVisibleText(field.options().get(best));
				}
				case "combobox" -> {
					human.betweenFields();
					WebElement el = driver.findElement(By.cssSelector("[data-nr='" + field.ref() + "']"));
					if (!pick(driver, el, answer)) {
						return false;
					}
				}
				case "text", "textarea" -> {
					human.betweenFields();
					WebElement el = driver.findElement(By.cssSelector("[data-nr='" + field.ref() + "']"));
					human.focus(driver, el);
					el.clear();
					human.type(el, answer);
					if ("text".equals(field.kind())) {
						el.sendKeys(Keys.TAB);
					}
				}
				case "file" -> {
					if (!Files.isRegularFile(Path.of(answer))) {
						return false;
					}
					driver.findElement(By.cssSelector("[data-nr='" + field.ref() + "']")).sendKeys(answer);
					pause(UPLOAD_WAIT_MS);
				}
				default -> {
					return false;
				}
			}
			return true;
		}
		catch (WebDriverException | UnsupportedOperationException | IllegalArgumentException ex) {
			// a field we can't use (hidden behind a styled list, disabled): leave it for the user
			log.debug("Could not fill {}: {}", field.label(), firstLine(ex.getMessage()));
			return false;
		}
	}

	/**
	 * Types into a type-ahead and chooses the option that fits the answer. Never takes just the
	 * first option offered: a wrong school is worse than an empty one.
	 *
	 * @return true if an option is now shown as chosen
	 */
	private boolean pick(WebDriver driver, WebElement el, String answer) {
		human.focus(driver, el);
		List<WebElement> offered = typeAndList(driver, el, answer);
		int best = bestOption(offered.stream().map(WebElement::getText).toList(), answer);
		String firstWord = answer.strip().split("[\\s,'’]+")[0];
		if (best < 0 && firstWord.length() > 2 && !firstWord.equals(answer.strip())) {
			// the list may filter on a spelling of its own ("Master’s" with a curly quote): search wider
			offered = typeAndList(driver, el, firstWord);
			best = bestOption(offered.stream().map(WebElement::getText).toList(), answer);
		}
		if (best < 0) {
			el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, Keys.ESCAPE);
			return false;
		}
		human.click(driver, offered.get(best));
		human.brief();
		pause(300);
		return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("""
				const el = arguments[0];
				const box = el.closest('[class*="__control"], [class*="-control"]') || el.closest('[class*="value-container"]');
				const shown = box && box.querySelector('[class*="single-value"], [class*="singleValue"], [class*="multi-value"]');
				return !!(shown && shown.textContent.trim()) || !!String(el.value || '').trim();
				""", el));
	}

	private List<WebElement> typeAndList(WebDriver driver, WebElement el, String text) {
		el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
		human.type(el, text);
		pause(PICK_WAIT_MS);
		return driver.findElements(By.cssSelector("[role=option], [class*='__option']"));
	}

	private static final Pattern DECLINE = Pattern.compile("(?i)prefer not|decline|don'?t wish|do not wish|not to (say|disclose|answer)");

	private static final List<String> DECLINE_WORDINGS = List.of("Prefer not to say", "I prefer not to say",
			"Prefer not to disclose", "I don't wish to answer", "I do not wish to answer", "Decline to self-identify",
			"Decline to self identify", "I do not want to answer", "I prefer not to answer", "Prefer not to answer");

	private static final List<String> NOT_A_VETERAN = List.of("I am not a protected veteran", "I am not a veteran",
			"I am not a protected veteran.", "Not a veteran", "No");

	private static final Pattern OTHER_FITS = Pattern.compile("school|college|university|institut|degree|discipline|field\\s*of\\s*study|major|hear|know about");

	/** Index of the option that best fits the answer, or -1: exact, then one inside the other, then yes/no. */
	static int bestOption(List<String> options, String answer) {
		String a = plain(answer);
		for (int i = 0; i < options.size(); i++) {
			if (plain(options.get(i)).equals(a)) {
				return i;
			}
		}
		for (int i = 0; i < options.size(); i++) {
			String o = plain(options.get(i));
			if (!o.isEmpty() && (a.startsWith(o) || o.startsWith(a))) {
				return i;
			}
		}
		for (int i = 0; i < options.size(); i++) {
			String o = plain(options.get(i));
			if (o.length() > 2 && (a.contains(o) || o.contains(a))) {
				return i;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- the page

	/**
	 * Closes a cookie banner that sits over the page, the privacy-friendly way: "Reject",
	 * "Only necessary", "Decline". A banner offering only "Accept" is left alone (clicks reach
	 * the page under it anyway): agreeing to tracking is the user's call, not ours.
	 *
	 * @return true if a banner was closed
	 */
	static boolean dismissCookieBanner(WebDriver driver) {
		Object closed = ((JavascriptExecutor) driver).executeScript("""
				const known = document.querySelector('#onetrust-reject-all-handler, #CybotCookiebotDialogBodyButtonDecline,'
				  + ' .cc-deny, [data-testid="uc-deny-all-button"], #truste-consent-required');
				if (known && known.offsetParent !== null) { known.click(); return true; }
				const say = /^(reject|reject all|reject cookies|reject all cookies|decline|decline all|deny|deny all|refuse|refuse all|only necessary|only essential|necessary only|essential only|use necessary cookies only|only necessary cookies|only essential cookies|continue without accepting)$/i;
				const button = [...document.querySelectorAll('button, a[role=button], [role=button], input[type=button]')]
				  .find(b => b.offsetParent !== null && say.test((b.innerText || b.value || '').replace(/\\s+/g, ' ').trim()));
				if (button) { button.click(); return true; }
				return false;
				""");
		return Boolean.TRUE.equals(closed);
	}

	private static final Pattern APPLY_BUTTON = Pattern.compile(
			"(?i)(apply|apply now|apply for this (job|position|role)|apply online|apply here|easy apply|apply to this job|i'?m interested)");

	/** "Apply", "Apply Now", and the same with a screen-reader note ("Apply (opens in new window)"). */
	static boolean isApplyButton(String text) {
		String plain = text == null ? "" : text.replaceAll("\\(.*?\\)", " ").replaceAll("\\s+", " ").strip();
		return APPLY_BUTTON.matcher(plain).matches();
	}

	/**
	 * Application systems that first ask how to apply (Workday: "Autofill with Resume", "Apply
	 * Manually", "Use My Last Application"): take the plain form, or the no-account way in.
	 */
	private static void chooseHowToApply(WebDriver driver) {
		if (!driver.findElements(By.cssSelector("input[type=email], input[type=file], textarea")).isEmpty()) {
			return;
		}
		for (WebElement candidate : driver.findElements(By.cssSelector("a, button, [role=button]"))) {
			String text = candidate.getText().replaceAll("\\s+", " ").strip();
			if (text.matches("(?i)apply manually|apply as (a )?guest|continue as (a )?guest|apply without (an )?account"
					+ "|continue without signing in") && candidate.isDisplayed()) {
				click(driver, candidate);
				settle(driver);
				dismissCookieBanner(driver);
				return;
			}
		}
	}

	/** Some boards show the job first and the form only after "Apply". */
	private static void clickApplyIfNeeded(WebDriver driver) {
		if (!driver.findElements(By.cssSelector("input[type=email], input[name*=email i]")).isEmpty()) {
			return;
		}
		for (WebElement candidate : driver.findElements(By.cssSelector("a, button"))) {
			if (isApplyButton(candidate.getText()) && candidate.isDisplayed()) {
				Set<String> before = driver.getWindowHandles();
				click(driver, candidate);
				settle(driver);
				// "Apply" often opens the form in a new tab (the company's application system): follow it
				driver.getWindowHandles().stream().filter(h -> !before.contains(h)).findFirst()
						.ifPresent(tab -> driver.switchTo().window(tab));
				dismissCookieBanner(driver);
				return;
			}
		}
	}

	static final String LOG_IN = "Log in on this site";

	static final String FIND_FORM = "Open the application form";

	/** A visible password box and no visible application fields: a login page, not the form. */
	private static boolean asksToLogIn(WebDriver driver) {
		Object answer = ((JavascriptExecutor) driver).executeScript("""
				const visible = (el) => el.offsetParent !== null;
				const password = [...document.querySelectorAll('input[type=password]')].some(visible);
				const form = [...document.querySelectorAll('input[type=file], textarea, input[name*=name i], input[id*=name i]')]
				  .some(visible);
				return password && !form;
				""");
		return Boolean.TRUE.equals(answer);
	}

	/**
	 * A CAPTCHA a person has to solve: a visible challenge box. The invisible reCAPTCHA many
	 * forms carry (a small badge in the corner) checks in the background and needs nobody.
	 */
	private static boolean captchaForAPerson(WebDriver driver) {
		return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("""
				return [...document.querySelectorAll("iframe[src*='recaptcha'], iframe[src*='hcaptcha'], iframe[src*='turnstile']")]
				  .some(f => !/size=invisible/.test(f.src) && f.offsetParent !== null && f.offsetWidth > 100 && f.offsetHeight > 50);
				"""));
	}

	/** Scrolls to the submit button and rings it, so the last step is obvious. Never clicks it. */
	private static void highlightSubmit(WebDriver driver) {
		((JavascriptExecutor) driver).executeScript("""
				const buttons = [...document.querySelectorAll('button, input[type=submit]')];
				const b = buttons.find(x => x.type === 'submit' && x.offsetParent !== null)
				  || buttons.find(x => /submit|send application|apply/i.test(x.innerText || x.value || '') && x.offsetParent !== null);
				if (!b) return;
				b.style.outline = '4px solid #7c3aed'; b.style.outlineOffset = '4px'; b.style.boxShadow = '0 0 0 10px rgba(124,58,237,0.25)';
				b.scrollIntoView({ block: 'center' });
				const tip = document.createElement('div');
				tip.textContent = 'NaukriRadar filled this form. Check it, then press Submit yourself.';
				tip.style.cssText = 'position:fixed;top:12px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#7c3aed;color:#fff;'
				  + 'padding:10px 16px;border-radius:12px;font:600 14px system-ui;box-shadow:0 8px 24px rgba(0,0,0,.25)';
				document.body.appendChild(tip);
				""");
	}

	private static void click(WebDriver driver, WebElement element) {
		try {
			element.click();
		}
		catch (ElementClickInterceptedException ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private static Map<String, String> safeAsk(Function<List<Question>, Map<String, String>> ask, List<Question> questions) {
		try {
			Map<String, String> replies = ask.apply(questions);
			return replies == null ? Map.of() : replies;
		}
		catch (RuntimeException ex) {
			log.info("AI answers unavailable: {}", ex.getMessage());
			return Map.of();
		}
	}

	private static Map.Entry<Pattern, String> rule(String regex, String key) {
		return new AbstractMap.SimpleEntry<>(Pattern.compile(regex), key);
	}

	/** Waits until the page says it has loaded (at most a few seconds), then a moment more. */
	private static void settle(WebDriver driver) {
		long deadline = System.currentTimeMillis() + PAGE_LOAD_MAX_MS;
		while (System.currentTimeMillis() < deadline) {
			try {
				if ("complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState"))) {
					break;
				}
			}
			catch (WebDriverException ex) {
				// the page is being replaced: keep waiting
			}
			pause(200);
		}
		pause(PAGE_SETTLE_MS);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "unknown error";
		}
		int end = message.indexOf('\n');
		return end < 0 ? message : message.substring(0, end);
	}

}
