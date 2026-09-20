package com.autoapply.settings;

/**
 * Every tunable knob in the system. Nothing in the codebase should read configuration
 * from anywhere else - changing behaviour means changing a row in app_settings.
 */
public final class SettingKeys {

    private SettingKeys() {
    }

    // ---------- general ----------
    public static final String APP_NAME = "app.name";
    public static final String APP_PUBLIC_URL = "app.public.url";
    public static final String APP_SUPPORT_EMAIL = "app.support.email";
    public static final String APP_MAINTENANCE_MODE = "app.maintenance.mode";
    public static final String APP_MAINTENANCE_MESSAGE = "app.maintenance.message";
    public static final String APP_TIMEZONE = "app.timezone";
    public static final String APP_REGISTRATION_ENABLED = "app.registration.enabled";
    public static final String APP_DEFAULT_COUNTRY = "app.default.country";

    // ---------- security ----------
    public static final String SECURITY_JWT_SECRET = "security.jwt.secret";
    public static final String SECURITY_JWT_EXPIRY_MINUTES = "security.jwt.expiry.minutes";
    public static final String SECURITY_REFRESH_EXPIRY_DAYS = "security.refresh.expiry.days";
    public static final String SECURITY_PASSWORD_MIN_LENGTH = "security.password.min.length";
    public static final String SECURITY_PASSWORD_REQUIRE_SPECIAL = "security.password.require.special";
    public static final String SECURITY_MAX_LOGIN_ATTEMPTS = "security.max.login.attempts";
    public static final String SECURITY_LOCKOUT_MINUTES = "security.lockout.minutes";
    public static final String SECURITY_CORS_ORIGINS = "security.cors.allowed.origins";
    public static final String SECURITY_PUBLIC_PATHS = "security.public.paths";

    // ---------- rate limiting ----------
    public static final String RATELIMIT_ENABLED = "ratelimit.enabled";
    public static final String RATELIMIT_REQUESTS_PER_MINUTE = "ratelimit.requests.per.minute";
    public static final String RATELIMIT_AUTH_PER_MINUTE = "ratelimit.auth.requests.per.minute";

    // ---------- ai / matching ----------
    public static final String AI_PROVIDER = "ai.provider";
    public static final String AI_ENABLED = "ai.enabled";
    public static final String AI_TIMEOUT_SECONDS = "ai.timeout.seconds";
    public static final String AI_MAX_RETRIES = "ai.max.retries";
    public static final String AI_TEMPERATURE = "ai.temperature";
    public static final String AI_MAX_TOKENS = "ai.max.tokens";
    public static final String AI_FALLBACK_SCORE = "ai.fallback.score";
    public static final String AI_CACHE_HOURS = "ai.cache.hours";

    public static final String GEMINI_API_KEY = "ai.gemini.api.key";
    public static final String GEMINI_BASE_URL = "ai.gemini.base.url";
    public static final String GEMINI_MODEL = "ai.gemini.model";

    public static final String OPENAI_API_KEY = "ai.openai.api.key";
    public static final String OPENAI_BASE_URL = "ai.openai.base.url";
    public static final String OPENAI_MODEL = "ai.openai.model";

    public static final String ANTHROPIC_API_KEY = "ai.anthropic.api.key";
    public static final String ANTHROPIC_BASE_URL = "ai.anthropic.base.url";
    public static final String ANTHROPIC_MODEL = "ai.anthropic.model";
    public static final String ANTHROPIC_VERSION = "ai.anthropic.version";

    public static final String OLLAMA_BASE_URL = "ai.ollama.base.url";
    public static final String OLLAMA_MODEL = "ai.ollama.model";

    // ---------- matching ----------
    public static final String MATCH_KEYWORD_THRESHOLD = "match.keyword.threshold";
    public static final String MATCH_RECOMMEND_THRESHOLD = "match.recommend.threshold";
    public static final String MATCH_MAX_JOBS_PER_RUN = "match.max.jobs.per.run";
    public static final String MATCH_TITLE_WEIGHT = "match.weight.title";
    public static final String MATCH_SKILL_WEIGHT = "match.weight.skills";
    public static final String MATCH_LOCATION_WEIGHT = "match.weight.location";
    public static final String MATCH_SALARY_WEIGHT = "match.weight.salary";
    public static final String MATCH_EXPERIENCE_WEIGHT = "match.weight.experience";
    public static final String MATCH_RECENCY_WEIGHT = "match.weight.recency";

    // ---------- job fetching ----------
    public static final String JOBS_FETCH_ENABLED = "jobs.fetch.enabled";
    public static final String JOBS_FETCH_CRON = "jobs.fetch.cron";
    public static final String JOBS_DEFAULT_QUERIES = "jobs.default.queries";
    public static final String JOBS_PAGES_PER_QUERY = "jobs.pages.per.query";
    public static final String JOBS_RETENTION_DAYS = "jobs.retention.days";
    public static final String JOBS_CLEANUP_CRON = "jobs.cleanup.cron";
    public static final String JOBS_DEDUP_ENABLED = "jobs.dedup.enabled";
    public static final String JSEARCH_API_KEY = "jobs.jsearch.api.key";
    public static final String ADZUNA_APP_ID = "jobs.adzuna.app.id";
    public static final String ADZUNA_APP_KEY = "jobs.adzuna.app.key";

    // ---------- auto apply ----------
    public static final String APPLY_ENABLED = "apply.enabled";
    public static final String APPLY_CRON = "apply.cron";
    public static final String APPLY_MODE = "apply.mode";
    public static final String APPLY_MAX_DAILY = "apply.max.daily.per.user";
    public static final String APPLY_MIN_DELAY_SECONDS = "apply.delay.min.seconds";
    public static final String APPLY_MAX_DELAY_SECONDS = "apply.delay.max.seconds";
    public static final String APPLY_MIN_SCORE = "apply.min.match.score";
    public static final String APPLY_MAX_RETRIES = "apply.max.retries";
    public static final String APPLY_RETRY_BACKOFF_MINUTES = "apply.retry.backoff.minutes";
    public static final String APPLY_WORKING_HOURS_ONLY = "apply.working.hours.only";
    public static final String APPLY_WORKING_HOUR_START = "apply.working.hour.start";
    public static final String APPLY_WORKING_HOUR_END = "apply.working.hour.end";
    public static final String APPLY_SKIP_WEEKENDS = "apply.skip.weekends";

    // ---------- selenium ----------
    public static final String SELENIUM_ENABLED = "selenium.enabled";
    public static final String SELENIUM_BROWSER = "selenium.browser";
    public static final String SELENIUM_HEADLESS = "selenium.headless";
    public static final String SELENIUM_REMOTE_URL = "selenium.remote.url";
    public static final String SELENIUM_PAGE_TIMEOUT_SECONDS = "selenium.page.timeout.seconds";
    public static final String SELENIUM_ELEMENT_TIMEOUT_SECONDS = "selenium.element.timeout.seconds";
    public static final String SELENIUM_WINDOW_SIZE = "selenium.window.size";
    public static final String SELENIUM_USER_AGENT = "selenium.user.agent";
    public static final String SELENIUM_SCREENSHOT_ON_FAILURE = "selenium.screenshot.on.failure";
    public static final String SELENIUM_HUMANIZE = "selenium.humanize.typing";

    // ---------- resume ----------
    public static final String RESUME_UPLOAD_DIR = "resume.upload.dir";
    public static final String RESUME_MAX_SIZE_MB = "resume.max.size.mb";
    public static final String RESUME_ALLOWED_TYPES = "resume.allowed.types";
    public static final String RESUME_AUTO_EXTRACT_SKILLS = "resume.auto.extract.skills";
    public static final String RESUME_SKILL_DICTIONARY = "resume.skill.dictionary";

    // ---------- notifications ----------
    public static final String NOTIFY_ENABLED = "notify.enabled";
    public static final String NOTIFY_CHANNELS = "notify.channels";
    public static final String NOTIFY_ON_APPLY = "notify.on.apply";
    public static final String NOTIFY_ON_MATCH = "notify.on.match";
    public static final String NOTIFY_DIGEST_CRON = "notify.digest.cron";
    public static final String NOTIFY_DIGEST_ENABLED = "notify.digest.enabled";

    public static final String MAIL_HOST = "notify.mail.host";
    public static final String MAIL_PORT = "notify.mail.port";
    public static final String MAIL_USERNAME = "notify.mail.username";
    public static final String MAIL_PASSWORD = "notify.mail.password";
    public static final String MAIL_FROM = "notify.mail.from";
    public static final String MAIL_STARTTLS = "notify.mail.starttls";

    public static final String SLACK_WEBHOOK_URL = "notify.slack.webhook.url";
    public static final String TELEGRAM_BOT_TOKEN = "notify.telegram.bot.token";
    public static final String TELEGRAM_CHAT_ID = "notify.telegram.chat.id";
    public static final String GENERIC_WEBHOOK_URL = "notify.webhook.url";
    public static final String GENERIC_WEBHOOK_HEADERS = "notify.webhook.headers";

    // ---------- audit / housekeeping ----------
    public static final String AUDIT_ENABLED = "audit.enabled";
    public static final String AUDIT_RETENTION_DAYS = "audit.retention.days";
    public static final String AUDIT_CLEANUP_CRON = "audit.cleanup.cron";

    // ---------- admin bootstrap ----------
    public static final String ADMIN_BOOTSTRAP_EMAIL = "admin.bootstrap.email";
    public static final String ADMIN_BOOTSTRAP_PASSWORD = "admin.bootstrap.password";
    public static final String ADMIN_FIRST_USER_IS_ADMIN = "admin.first.user.is.admin";
}
