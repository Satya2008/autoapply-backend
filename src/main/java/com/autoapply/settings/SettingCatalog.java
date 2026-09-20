package com.autoapply.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.autoapply.settings.SettingKeys.*;
import static com.autoapply.settings.SettingType.*;

/**
 * The full catalogue of runtime settings. Adding a knob to the product means adding one
 * entry here - it then appears in the admin dashboard automatically, validated and typed.
 */
public final class SettingCatalog {

    private SettingCatalog() {
    }

    private static final List<SettingDefinition> DEFINITIONS = new ArrayList<>();
    private static final Map<String, SettingDefinition> BY_KEY = new LinkedHashMap<>();

    private static void def(String key, String defaultValue, SettingType type, String category,
                            String description, String allowedValues, boolean secret, int order) {
        DEFINITIONS.add(SettingDefinition.builder()
                .key(key).defaultValue(defaultValue).type(type).category(category)
                .description(description).allowedValues(allowedValues)
                .secret(secret).editable(true).requiresRestart(false).displayOrder(order)
                .build());
    }

    static {
        // ------------------------------------------------------------- General
        def(APP_NAME, "AutoApply AI", STRING, "General", "Product name shown in the UI and emails", null, false, 10);
        def(APP_PUBLIC_URL, "http://localhost:3000", URL, "General", "Public base URL used in email links", null, false, 20);
        def(APP_SUPPORT_EMAIL, "support@autoapply.local", EMAIL, "General", "Reply-to address for outgoing mail", null, false, 30);
        def(APP_TIMEZONE, "Asia/Kolkata", STRING, "General", "Timezone used for schedules and reports", null, false, 40);
        def(APP_DEFAULT_COUNTRY, "IN", STRING, "General", "Default country code for job searches", null, false, 50);
        def(APP_REGISTRATION_ENABLED, "true", BOOLEAN, "General", "Allow new users to sign up", null, false, 60);
        def(APP_MAINTENANCE_MODE, "false", BOOLEAN, "General", "Block all non-admin API traffic", null, false, 70);
        def(APP_MAINTENANCE_MESSAGE, "We are performing scheduled maintenance. Please try again shortly.",
                TEXT, "General", "Message returned while maintenance mode is on", null, false, 80);

        // ------------------------------------------------------------ Security
        def(SECURITY_JWT_SECRET, "change-me-to-a-long-random-string-at-least-32-chars", PASSWORD, "Security",
                "Signing key for access tokens. Changing it logs everyone out.", null, true, 10);
        def(SECURITY_JWT_EXPIRY_MINUTES, "1440", INTEGER, "Security", "Access token lifetime in minutes", null, false, 20);
        def(SECURITY_REFRESH_EXPIRY_DAYS, "30", INTEGER, "Security", "Refresh token lifetime in days", null, false, 30);
        def(SECURITY_PASSWORD_MIN_LENGTH, "8", INTEGER, "Security", "Minimum password length at signup", null, false, 40);
        def(SECURITY_PASSWORD_REQUIRE_SPECIAL, "false", BOOLEAN, "Security", "Require a symbol in passwords", null, false, 50);
        def(SECURITY_MAX_LOGIN_ATTEMPTS, "8", INTEGER, "Security", "Failed logins before the account locks", null, false, 60);
        def(SECURITY_LOCKOUT_MINUTES, "15", INTEGER, "Security", "How long an account stays locked", null, false, 70);
        def(SECURITY_CORS_ORIGINS, "http://localhost:3000,http://localhost:5173", TEXT, "Security",
                "Comma separated list of browser origins allowed to call the API", null, false, 80);
        def(SECURITY_PUBLIC_PATHS, "/api/auth/login,/api/auth/register,/api/auth/refresh,/api/public/**,/actuator/health,/swagger-ui/**,/v3/api-docs/**",
                TEXT, "Security", "Endpoints reachable without a token", null, false, 90);

        // --------------------------------------------------------- Rate limits
        def(RATELIMIT_ENABLED, "true", BOOLEAN, "Rate Limiting", "Throttle abusive clients", null, false, 10);
        def(RATELIMIT_REQUESTS_PER_MINUTE, "120", INTEGER, "Rate Limiting", "General requests allowed per minute per client", null, false, 20);
        def(RATELIMIT_AUTH_PER_MINUTE, "10", INTEGER, "Rate Limiting", "Login/register attempts per minute per client", null, false, 30);

        // ------------------------------------------------------------------ AI
        def(AI_ENABLED, "true", BOOLEAN, "AI", "Use an AI provider for job matching", null, false, 10);
        def(AI_PROVIDER, "gemini", ENUM, "AI", "Which AI backend to score matches with",
                "gemini,openai,anthropic,ollama,local", false, 20);
        def(AI_TIMEOUT_SECONDS, "30", INTEGER, "AI", "Per-request timeout for AI calls", null, false, 30);
        def(AI_MAX_RETRIES, "2", INTEGER, "AI", "Retries on a failed AI call", null, false, 40);
        def(AI_TEMPERATURE, "0.2", DECIMAL, "AI", "Sampling temperature (lower is more consistent)", null, false, 50);
        def(AI_MAX_TOKENS, "1024", INTEGER, "AI", "Maximum tokens in the AI response", null, false, 60);
        def(AI_FALLBACK_SCORE, "50", INTEGER, "AI", "Score used when the AI call fails", null, false, 70);
        def(AI_CACHE_HOURS, "24", INTEGER, "AI", "How long to reuse an AI score for the same user/job pair", null, false, 80);

        def(GEMINI_API_KEY, "", PASSWORD, "AI · Gemini", "Google AI Studio API key", null, true, 10);
        def(GEMINI_BASE_URL, "https://generativelanguage.googleapis.com/v1beta", URL, "AI · Gemini", "Gemini API base URL", null, false, 20);
        def(GEMINI_MODEL, "gemini-1.5-flash", STRING, "AI · Gemini", "Model name", null, false, 30);

        def(OPENAI_API_KEY, "", PASSWORD, "AI · OpenAI", "OpenAI API key", null, true, 10);
        def(OPENAI_BASE_URL, "https://api.openai.com/v1", URL, "AI · OpenAI", "OpenAI compatible base URL", null, false, 20);
        def(OPENAI_MODEL, "gpt-4o-mini", STRING, "AI · OpenAI", "Model name", null, false, 30);

        def(ANTHROPIC_API_KEY, "", PASSWORD, "AI · Anthropic", "Anthropic API key", null, true, 10);
        def(ANTHROPIC_BASE_URL, "https://api.anthropic.com/v1", URL, "AI · Anthropic", "Anthropic API base URL", null, false, 20);
        def(ANTHROPIC_MODEL, "claude-sonnet-5", STRING, "AI · Anthropic", "Model name", null, false, 30);
        def(ANTHROPIC_VERSION, "2023-06-01", STRING, "AI · Anthropic", "anthropic-version header", null, false, 40);

        def(OLLAMA_BASE_URL, "http://localhost:11434", URL, "AI · Ollama", "Local Ollama server URL", null, false, 10);
        def(OLLAMA_MODEL, "llama3", STRING, "AI · Ollama", "Local model name", null, false, 20);

        // ------------------------------------------------------------ Matching
        def(MATCH_KEYWORD_THRESHOLD, "30", INTEGER, "Matching",
                "Minimum cheap keyword score before spending an AI call", null, false, 10);
        def(MATCH_RECOMMEND_THRESHOLD, "70", INTEGER, "Matching", "Score at or above which a job is recommended", null, false, 20);
        def(MATCH_MAX_JOBS_PER_RUN, "200", INTEGER, "Matching", "Jobs evaluated per matching run per user", null, false, 30);
        def(MATCH_TITLE_WEIGHT, "25", INTEGER, "Matching", "Weight of job title relevance", null, false, 40);
        def(MATCH_SKILL_WEIGHT, "35", INTEGER, "Matching", "Weight of skill overlap", null, false, 50);
        def(MATCH_LOCATION_WEIGHT, "15", INTEGER, "Matching", "Weight of location fit", null, false, 60);
        def(MATCH_SALARY_WEIGHT, "10", INTEGER, "Matching", "Weight of salary fit", null, false, 70);
        def(MATCH_EXPERIENCE_WEIGHT, "10", INTEGER, "Matching", "Weight of experience fit", null, false, 80);
        def(MATCH_RECENCY_WEIGHT, "5", INTEGER, "Matching", "Weight of how recently the job was posted", null, false, 90);

        // ----------------------------------------------------------- Job fetch
        def(JOBS_FETCH_ENABLED, "true", BOOLEAN, "Jobs", "Run scheduled job fetching", null, false, 10);
        def(JOBS_FETCH_CRON, "0 0 */6 * * *", CRON, "Jobs", "When to fetch jobs (Spring cron, 6 fields)", null, false, 20);
        def(JOBS_DEFAULT_QUERIES, "Java Developer in India,Spring Boot Developer in India,Backend Developer Java India",
                TEXT, "Jobs", "Comma separated searches used by the scheduler", null, false, 30);
        def(JOBS_PAGES_PER_QUERY, "1", INTEGER, "Jobs", "Result pages to pull per query", null, false, 40);
        def(JOBS_RETENTION_DAYS, "60", INTEGER, "Jobs", "Delete jobs older than this many days", null, false, 50);
        def(JOBS_CLEANUP_CRON, "0 30 3 * * *", CRON, "Jobs", "When to purge expired jobs", null, false, 60);
        def(JOBS_DEDUP_ENABLED, "true", BOOLEAN, "Jobs", "Skip jobs that look identical to one already stored", null, false, 70);
        def(JSEARCH_API_KEY, "", PASSWORD, "Jobs · Credentials",
                "RapidAPI key for the JSearch source. Paste it here and enable the source - nothing else needed.",
                null, true, 10);
        def(ADZUNA_APP_ID, "", STRING, "Jobs · Credentials", "Adzuna application id", null, false, 20);
        def(ADZUNA_APP_KEY, "", PASSWORD, "Jobs · Credentials", "Adzuna application key", null, true, 30);

        // ---------------------------------------------------------- Auto apply
        def(APPLY_ENABLED, "true", BOOLEAN, "Auto Apply", "Master switch for automatic applying", null, false, 10);
        def(APPLY_CRON, "0 0 10 * * *", CRON, "Auto Apply", "When the auto-apply sweep runs", null, false, 20);
        def(APPLY_MODE, "SIMULATE", ENUM, "Auto Apply",
                "SIMULATE records applications without touching the portal. BROWSER drives a real browser.",
                "SIMULATE,BROWSER", false, 30);
        def(APPLY_MAX_DAILY, "15", INTEGER, "Auto Apply", "Maximum applications per user per day", null, false, 40);
        def(APPLY_MIN_DELAY_SECONDS, "180", INTEGER, "Auto Apply", "Minimum pause between two applications", null, false, 50);
        def(APPLY_MAX_DELAY_SECONDS, "480", INTEGER, "Auto Apply", "Maximum pause between two applications", null, false, 60);
        def(APPLY_MIN_SCORE, "70", INTEGER, "Auto Apply", "Only apply to matches scoring at least this", null, false, 70);
        def(APPLY_MAX_RETRIES, "3", INTEGER, "Auto Apply", "Retries for a failed application", null, false, 80);
        def(APPLY_RETRY_BACKOFF_MINUTES, "30", INTEGER, "Auto Apply", "Wait before retrying a failed application", null, false, 90);
        def(APPLY_WORKING_HOURS_ONLY, "true", BOOLEAN, "Auto Apply", "Only apply during working hours", null, false, 100);
        def(APPLY_WORKING_HOUR_START, "9", INTEGER, "Auto Apply", "Working hours start (0-23)", null, false, 110);
        def(APPLY_WORKING_HOUR_END, "19", INTEGER, "Auto Apply", "Working hours end (0-23)", null, false, 120);
        def(APPLY_SKIP_WEEKENDS, "true", BOOLEAN, "Auto Apply", "Do not apply on Saturday and Sunday", null, false, 130);

        // ------------------------------------------------------------ Selenium
        def(SELENIUM_ENABLED, "false", BOOLEAN, "Browser Automation",
                "Allow the browser engine to run. Requires a browser on the host.", null, false, 10);
        def(SELENIUM_BROWSER, "chrome", ENUM, "Browser Automation", "Which browser to drive", "chrome,firefox,edge", false, 20);
        def(SELENIUM_HEADLESS, "true", BOOLEAN, "Browser Automation", "Run without a visible window", null, false, 30);
        def(SELENIUM_REMOTE_URL, "", STRING, "Browser Automation",
                "Selenium Grid URL. Leave blank to drive a local browser.", null, false, 40);
        def(SELENIUM_PAGE_TIMEOUT_SECONDS, "45", INTEGER, "Browser Automation", "Page load timeout", null, false, 50);
        def(SELENIUM_ELEMENT_TIMEOUT_SECONDS, "15", INTEGER, "Browser Automation", "How long to wait for an element", null, false, 60);
        def(SELENIUM_WINDOW_SIZE, "1440,900", STRING, "Browser Automation", "Browser window size as width,height", null, false, 70);
        def(SELENIUM_USER_AGENT, "", TEXT, "Browser Automation", "Override the browser user agent", null, false, 80);
        def(SELENIUM_SCREENSHOT_ON_FAILURE, "true", BOOLEAN, "Browser Automation", "Capture a screenshot when applying fails", null, false, 90);
        def(SELENIUM_HUMANIZE, "true", BOOLEAN, "Browser Automation", "Type with human-like pauses", null, false, 100);

        // -------------------------------------------------------------- Resume
        def(RESUME_UPLOAD_DIR, "./data/resumes", STRING, "Resume", "Where uploaded resumes are stored", null, false, 10);
        def(RESUME_MAX_SIZE_MB, "10", INTEGER, "Resume", "Maximum upload size in megabytes", null, false, 20);
        def(RESUME_ALLOWED_TYPES, "pdf,doc,docx", STRING, "Resume", "Allowed file extensions", null, false, 30);
        def(RESUME_AUTO_EXTRACT_SKILLS, "true", BOOLEAN, "Resume", "Parse the resume and fill in the user's skills", null, false, 40);
        def(RESUME_SKILL_DICTIONARY,
                "java,spring,spring boot,hibernate,jpa,sql,mysql,postgresql,mongodb,redis,kafka,rabbitmq,docker,kubernetes,"
                        + "aws,azure,gcp,microservices,rest,graphql,git,maven,gradle,junit,mockito,jenkins,ci/cd,linux,"
                        + "javascript,typescript,react,angular,vue,node,express,next.js,html,css,tailwind,python,django,"
                        + "flask,fastapi,pandas,numpy,machine learning,data structures,algorithms,system design",
                TEXT, "Resume", "Skills the parser looks for in an uploaded resume", null, false, 50);

        // ------------------------------------------------------- Notifications
        def(NOTIFY_ENABLED, "false", BOOLEAN, "Notifications", "Master switch for outgoing notifications", null, false, 10);
        def(NOTIFY_CHANNELS, "email", TEXT, "Notifications",
                "Comma separated active channels: email, slack, telegram, webhook", null, false, 20);
        def(NOTIFY_ON_APPLY, "true", BOOLEAN, "Notifications", "Notify the user after an application is submitted", null, false, 30);
        def(NOTIFY_ON_MATCH, "false", BOOLEAN, "Notifications", "Notify the user when new matches are found", null, false, 40);
        def(NOTIFY_DIGEST_ENABLED, "false", BOOLEAN, "Notifications", "Send a daily summary email", null, false, 50);
        def(NOTIFY_DIGEST_CRON, "0 0 20 * * *", CRON, "Notifications", "When to send the daily digest", null, false, 60);

        def(MAIL_HOST, "smtp.gmail.com", STRING, "Notifications · Email", "SMTP server host", null, false, 10);
        def(MAIL_PORT, "587", INTEGER, "Notifications · Email", "SMTP port", null, false, 20);
        def(MAIL_USERNAME, "", STRING, "Notifications · Email", "SMTP username", null, false, 30);
        def(MAIL_PASSWORD, "", PASSWORD, "Notifications · Email", "SMTP password or app password", null, true, 40);
        def(MAIL_FROM, "noreply@autoapply.local", EMAIL, "Notifications · Email", "From address", null, false, 50);
        def(MAIL_STARTTLS, "true", BOOLEAN, "Notifications · Email", "Use STARTTLS", null, false, 60);

        def(SLACK_WEBHOOK_URL, "", PASSWORD, "Notifications · Slack", "Slack incoming webhook URL", null, true, 10);
        def(TELEGRAM_BOT_TOKEN, "", PASSWORD, "Notifications · Telegram", "Telegram bot token", null, true, 10);
        def(TELEGRAM_CHAT_ID, "", STRING, "Notifications · Telegram", "Telegram chat id to post into", null, false, 20);
        def(GENERIC_WEBHOOK_URL, "", URL, "Notifications · Webhook", "POST notifications as JSON to this URL", null, false, 10);
        def(GENERIC_WEBHOOK_HEADERS, "{}", JSON, "Notifications · Webhook", "Extra headers as a JSON object", null, false, 20);

        // --------------------------------------------------------------- Audit
        def(AUDIT_ENABLED, "true", BOOLEAN, "Audit", "Record admin and user actions", null, false, 10);
        def(AUDIT_RETENTION_DAYS, "180", INTEGER, "Audit", "How long audit entries are kept", null, false, 20);
        def(AUDIT_CLEANUP_CRON, "0 0 4 * * *", CRON, "Audit", "When to purge old audit entries", null, false, 30);

        // --------------------------------------------------------------- Admin
        def(ADMIN_BOOTSTRAP_EMAIL, "admin@autoapply.local", EMAIL, "Admin",
                "Administrator account created on first start", null, false, 10);
        def(ADMIN_BOOTSTRAP_PASSWORD, "Admin@12345", PASSWORD, "Admin",
                "Password for the bootstrap administrator. Change it after first login.", null, true, 20);
        def(ADMIN_FIRST_USER_IS_ADMIN, "false", BOOLEAN, "Admin",
                "Promote the very first registered user to administrator", null, false, 30);

        DEFINITIONS.forEach(d -> BY_KEY.put(d.key(), d));
    }

    public static List<SettingDefinition> all() {
        return List.copyOf(DEFINITIONS);
    }

    public static SettingDefinition find(String key) {
        return BY_KEY.get(key);
    }
}
