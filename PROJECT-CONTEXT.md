# Auto Apply Job — Project Context

Single reference for this project. Upload this into the Claude Project so every new
chat starts with full context instead of re-deriving it.

Last updated: 2026-09-24

---

## What this is

A job-hunting automation platform. It watches job boards, scores every opening against
the candidate's real profile, and submits applications — automatically where that is
safe, and with everything pre-filled where it is not.

Two repositories, both owned by GitHub user `Satya2008`:

| Repo | Stack | Local path |
|---|---|---|
| `autoapply-backend` | Java 17, Spring Boot 3.2, Gradle | `D:\NaukriRadar\autoapply-backend` |
| `autoapply-frontend` | React 18, Vite, Tailwind | `D:\NaukriRadar\autoapply-frontend` |

**There is one frontend.** Candidates and administrators use the same app; routes under
`/admin` are role-gated (guarded in the router and again with `@PreAuthorize` on the API).

---

## The governing idea

**Nothing that controls behaviour lives in code.** 115 settings sit in the database and
are edited from the admin dashboard. There is no `@Value` annotation anywhere in the
codebase. Changing a threshold, an API key, a cron expression, or which job boards are
polled takes effect on the next call — no restart, no redeploy.

This extends further than settings:

| Task | What it takes |
|---|---|
| Add a job board | One DB row with a JsonPath field mapping |
| Switch AI vendor | One setting (`ai.provider`) |
| Change how the AI reasons | Edit the prompt in the DB |
| Support a new apply portal | One DB row with CSS selectors |
| Repair a portal after a site redesign | Edit those selectors |
| Change any schedule | Edit the cron setting |
| Switch database | Environment variables only |

---

## Running it locally

Three processes. Start them in this order.

```bash
# 1. Redis
/c/Users/Satya/scoop/apps/redis/current/redis-server.exe --port 6379

# 2. Backend  (D:\NaukriRadar\autoapply-backend)
export PATH="/c/Users/Satya/scoop/apps/temurin17-jdk/current/bin:$PATH"
./gradlew bootRun

# 3. Frontend (D:\NaukriRadar\autoapply-frontend)
export PATH="/c/Users/Satya/scoop/apps/nodejs-lts/current:$PATH"
npm run dev
```

| Service | URL |
|---|---|
| App | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| H2 console | http://localhost:8080/h2-console (`jdbc:h2:file:./data/autoapply`, user `sa`, blank password) |
| Redis | localhost:6379 |

**Administrator:** `admin@autoapply.local` / `Admin@12345`
Created on first boot and printed in the log. Still the default — change it from
Preferences or the `admin.bootstrap.password` setting.

---

## Machine constraints

The development laptop is a 4 GB RAM / i3 machine and the Windows user is **not an
administrator**. This shaped several decisions:

- **No MySQL.** Its installer needs the Visual C++ 2022 redistributable, which needs a
  UAC prompt that cannot be approved from an automated session. The app runs on **H2**
  (file-based, zero install). MySQL and PostgreSQL drivers are on the classpath, so
  switching is a matter of `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`.
- **Toolchain installed with Scoop** (user-space, no admin): Node 24, Temurin JDK 17,
  Gradle, Redis.
- **Gradle wrapper pinned to 8.5.** The Scoop-installed Gradle 9.x is incompatible with
  the Spring dependency-management plugin that pairs with Boot 3.2.0. Always use
  `./gradlew`, never the system `gradle`.
- Builds take minutes rather than seconds. Expect that.

---

## How a job becomes an application

```
JOB SOURCES  →  JOB POOL  →  MATCHING  →  RISK CHECK  →  APPLY or QUEUE
```

### 1. Fetching

`JobFetchService` loops the enabled sources. Each source is a DB row describing a JSON
API: base URL, headers, query parameters, and a JsonPath mapping from the response to
our `Job` fields. `GenericRestJobProvider` reads that row and does the call — which is
why a new board needs no code.

Headers can reference a secret with `${setting:some.key}`, so API keys never sit in the
source row itself.

Duplicates are caught by a `fingerprint` (hash of title + company + city), so the same
role posted on two boards is stored once.

Five sources ship configured: **RemoteOK, Arbeitnow, The Muse** (enabled, no key needed)
and **JSearch, Adzuna** (disabled until their keys are set).

### 2. Matching — two stages, to control cost

**Stage one** is a local weighted score, no API call:

| Factor | Default weight |
|---|---|
| Skill overlap | 35 |
| Title relevance | 25 |
| Location fit | 15 |
| Salary fit | 10 |
| Experience fit | 10 |
| Posting recency | 5 |

Anything below `match.keyword.threshold` (default 30) is dropped here and never reaches
the model.

**Stage two** sends the survivors to the configured AI provider. `AiService` loads the
prompt from the database, substitutes `{{placeholders}}`, calls the model, and parses
the JSON back. Four providers implement one interface: **Gemini, OpenAI, Anthropic,
Ollama**.

Exclusions (companies, keywords) are applied before scoring, so an unwanted employer
never costs an AI call.

### 3. Risk check — the part that protects the candidate's accounts

Before anything is submitted, `ApplyRiskAssessor` classifies the apply link:

| Band | Sites | What happens |
|---|---|---|
| **LOW** | Greenhouse, Lever, Workable, Ashby, Recruitee, SmartRecruiters… | Browser submits it |
| **HIGH** | LinkedIn, Naukri, Indeed, Glassdoor, Workday, Taleo, iCIMS, Instahyre… | **Never automated** |
| **MEDIUM** | Anything unrecognised | Queued for the candidate unless `apply.automate.medium.risk` is on |

The reason HIGH sites are never automated: a detected bot there risks the **candidate's
account**, not just one application. Both domain lists are settings, so they can be
corrected as sites change.

### 4. Applying

**Automated path** — `BrowserApplyEngine` drives Chrome using the CSS selectors stored
for that portal. It types with human-like pauses, waits between applications
(3–8 minutes by default), screenshots failures, and confirms success by looking for a
configured element. Nothing about any specific site is hard-coded.

**Assisted path** — `PrefillService` packs every answer into JSON, the application is
saved with status `NEEDS_YOU`, and it appears on the "Needs your click" screen with each
field one tap from the clipboard. The candidate opens the posting, pastes, submits, and
marks it done.

Daily caps, pacing, working hours and weekend skipping are all settings, and a user can
override the cap and minimum score for themselves.

---

## The candidate's path through the app

A checklist on the dashboard walks a new account through setup and disappears once done:

1. **Upload resume** — parsed with PDFBox/POI, skills extracted using a dictionary held
   in settings
2. **Check skills** — at least three
3. **Set target roles** — what every posting is scored against
4. **Turn on auto apply**

Then: Dashboard → Matches → Needs your click → Applications → Profile → Preferences.

---

## Admin dashboard

Nine screens under `/admin`:

| Screen | Purpose |
|---|---|
| Control Center | Live metrics, charts, recent activity |
| Settings | All 115, searchable, typed, secrets masked |
| Users | Roles, limits, lock/unlock, password reset |
| Job Sources | Add or edit any job API, test one live |
| Apply Portals | The CSS selector editor per site |
| AI Prompts | Edit prompts, test the provider |
| Scheduler | Cron per task, run-now, last result |
| Audit Log | Every action, filterable |
| System Health | DB, Redis, AI providers, channels |

`⌘K` opens a command palette from anywhere.

---

## Scheduled work

Seven tasks, each with a cron expression read from settings and re-registered the moment
it changes (`DynamicSchedulerService`):

| Task | Default |
|---|---|
| fetch-jobs | every 6 hours |
| match-jobs | every 6 hours |
| auto-apply | 10:00 daily |
| retry-applications | 10:00 daily |
| cleanup-jobs | 03:30 daily |
| cleanup-audit | 04:00 daily |
| daily-digest | 20:00 daily |

---

## Security

- JWT access tokens plus rotating refresh tokens
- Three roles: USER, ADMIN, SUPER_ADMIN
- Account lockout after repeated failed logins
- Rate limiting per IP, limits configurable
- Maintenance mode that still admits administrators
- Secret settings encrypted at rest with AES-GCM (`CryptoService`); set
  `APP_ENCRYPTION_KEY` before any real deployment
- Audit trail with actor, IP and user agent

---

## What is real, and what is still waiting

| | State |
|---|---|
| Job fetching | Working — 243 real jobs pulled from Arbeitnow in testing |
| Matching | Working — real weighted calculation |
| Assisted queue | Working — 11 applications queued with prefilled answers in testing |
| Admin dashboard | Working — all screens live against real data |
| **AI scoring** | Needs an API key in Settings → AI |
| **Browser applying** | Needs `selenium.enabled = true`, `apply.mode = BROWSER`, and the target portal's `dryRun` turned off |
| Notifications | Needs SMTP or webhook credentials |
| Tests | None written yet |

**Important:** with `apply.mode = SIMULATE` (the default), applications are recorded in
the database but **nothing reaches the employer**. Each such row says so in its message.

---

## Known gaps worth picking up next

- No automated tests anywhere
- Frontend ships as one 740 KB bundle; worth code-splitting
- The daily digest task sends zeros — it does not gather real figures yet
- Application statuses beyond APPLIED (INTERVIEW, OFFER, REJECTED) exist in the model
  but nothing updates them
- No Docker or deployment configuration yet

---

## Working agreements established in this project

- Do not ask for permission before acting; install what is needed and proceed
- Prefer configuration over code for anything that might change
- Default to the safe option when automation could harm the user
- Explanations in Hinglish; code, comments and commit messages in English

---

## File map

**Backend** — `src/main/java/com/autoapply/`

```
common/      ApiResponse, AppException, CryptoService, GlobalExceptionHandler
settings/    SettingsService, SettingCatalog, SettingKeys, SettingsController   ← the brain
config/      SecurityConfig, JwtService, JwtAuthFilter, RateLimitFilter, AppConfig
user/        User, Role, RefreshToken, UserService, ResumeService, AdminBootstrap
jobs/        Job, JobFetchService, source/GenericRestJobProvider, source/JobSourceConfig
matching/    MatchingService, ai/AiService, ai/PromptTemplate, ai/*Provider
apply/       AutoApplyService, engine/BrowserApplyEngine, risk/ApplyRiskAssessor,
             portal/ApplyPortalConfig, service/PrefillService
notification/ NotificationService, EmailChannel, SlackChannel, TelegramChannel, WebhookChannel
scheduler/   DynamicSchedulerService
analytics/   AnalyticsService
audit/       AuditService, AuditLog, AuditAction
admin/       AdminDashboard/User/JobSource/Portal/Operations controllers
```

**Frontend** — `src/`

```
store/AppContext.jsx      auth, theme, toasts
services/api.js           typed API layer with transparent token refresh
components/               ui.jsx (design system), Layout, CommandPalette, Onboarding
pages/                    Login, Dashboard, Matches, Jobs, AssistedApply,
                          Applications, Profile, Settings
pages/admin/              Overview, Settings, Users, JobSources, Portals,
                          Prompts, Scheduler, Audit, Health
```

86 Java files, 25 JS/JSX files.
