# NaukriRadar — Backend

NaukriRadar pulls job postings from job boards, scores each one against a candidate's
profile, and applies where it is safe to automate. Where it is not (LinkedIn, Naukri,
Workday), it prepares every answer so the candidate can submit in one click.

## Architecture

Microservices from the start, each in its own Gradle module with its own database, in
one repository.

```
client ──► gateway :8080 ─┬─► core-api         :8081 ──► MySQL naukriradar_core
                          ├─► job-service      :8082 ──► MySQL naukriradar_job ──► job boards
                          └─► matching-service :8083 ──► MySQL naukriradar_matching
                                (calls core-api and job-service on /internal APIs)
```

| Module | Port | What it owns | Status |
|---|---|---|---|
| `gateway` | 8080 | The only public entry point. Routes requests, and serves one Swagger UI for all services | ✅ |
| `core-api` | 8081 | Users, profiles, skills, resumes, applications | ✅ |
| `job-service` | 8082 | Job boards as configuration; fetching, cleaning and storing jobs | ✅ |
| `matching-service` | 8083 | Scoring jobs against a profile, keeping the best matches | ✅ |
| `apply-worker` | 8084 | Browser automation for low-risk portals | Phase 15 |
| `notification-service` | 8085 | Email, Telegram, daily digest | Phase 16 |
| `libs/common-web` | — | Shared Problem Details errors and the `X-User-Id` caller lookup | ✅ |

## Tech stack

- Java 21, Spring Boot 4.1, Spring Cloud Gateway 2025.1, Gradle (wrapper)
- MySQL 8, one database per service
- Spring Data JPA, Bean Validation, Actuator, springdoc OpenAPI, Lombok
- Apache PDFBox and POI for reading resumes
- RestClient and JsonPath for job boards; WireMock in tests
- Virtual threads for parallel fetching, MySQL FULLTEXT search, keyset pagination
- Redis: shared locks, a two-level cache (Caffeine + Redis), pub/sub, rate limits; ShedLock
- Resilience4j (retry with jitter, circuit breaker, bulkhead); Anthropic, OpenAI, Gemini and Ollama APIs

## Run locally

1. Start MySQL 8 and create one database per service, plus one for its tests:

   ```sql
   CREATE DATABASE naukriradar_core;       -- development
   CREATE DATABASE naukriradar_core_test;  -- tests only
   CREATE DATABASE naukriradar_job;
   CREATE DATABASE naukriradar_job_test;
   CREATE DATABASE naukriradar_matching;
   CREATE DATABASE naukriradar_matching_test;
   ```

   Credentials default to `root` / `root`; override with `DB_USERNAME` and `DB_PASSWORD`.

   Start Redis on `localhost:6379` too (override with `REDIS_HOST` / `REDIS_PORT`). Tests
   use its database 1.

2. Start everything at once (Windows PowerShell), then walk the main flow:

   ```powershell
   .\scripts\start-local.ps1      # builds, starts all four services, waits until healthy
   python scripts\smoke-test.py   # user -> profile -> fetch jobs -> match -> apply run
   .\scripts\stop-local.ps1       # stops them
   ```

   Logs are in `logs/`. Or start each service in its own terminal:

   ```bash
   ./gradlew :core-api:bootRun
   ./gradlew :job-service:bootRun
   ./gradlew :matching-service:bootRun
   ./gradlew :gateway:bootRun
   ```

   Every service runs with a 256 MB heap, so several fit on a small laptop.

| What | URL |
|---|---|
| Swagger UI (all services) | http://localhost:8080/swagger-ui.html |
| Gateway health | http://localhost:8080/actuator/health |
| core-api health | http://localhost:8081/actuator/health |
| job-service health | http://localhost:8082/actuator/health |
| matching-service health | http://localhost:8083/actuator/health |

## Try the API

There is no login yet. The caller is identified by an `X-User-Id` header. From Phase 8 the
gateway verifies a JWT and sets this header itself.

1. `POST /api/v1/dev/users` with `{"email": "you@example.com"}` and copy the returned `id`.
2. In Swagger UI, click **Authorize** and paste that id.
3. Call `GET/PUT /api/v1/me/profile` and `GET/PUT /api/v1/me/skills`.
4. Upload a resume with `POST /api/v1/me/resume` (multipart field `file`, PDF or DOCX, up
   to 10 MB). The response lists the skills found; they are added to the profile without
   touching skills you entered yourself.

| Resume endpoint | What it does |
|---|---|
| `POST /api/v1/me/resume` | Upload or replace; returns skills found |
| `GET /api/v1/me/resume` | File name, size, upload time |
| `GET /api/v1/me/resume/file` | Download the file |
| `DELETE /api/v1/me/resume` | Remove the resume and its file |

The file type is checked from the file's bytes, not its name, so a renamed `.exe` is
refused. Files are kept under `core-api/data/files` in dev
(`naukriradar.storage.local-dir`).

### Job boards

A job board is a row, not code. Each source says where to call, which query params and
headers to send, where the list of jobs is in the response (`resultsPath`) and where each
field is (`fieldMappings`, as JsonPath). Arbeitnow is created on first start from
`job-service/src/main/resources/sources/default-sources.json`.

| Admin endpoint | What it does |
|---|---|
| `GET /api/v1/admin/job-sources` | All sources with last run status and job count |
| `POST /api/v1/admin/job-sources` | Add a board (validated: URLs, JsonPaths, required fields) |
| `PUT /api/v1/admin/job-sources/{id}` | Change a board's config |
| `DELETE /api/v1/admin/job-sources/{id}` | Remove a board; its jobs stay |
| `POST /api/v1/admin/job-sources/{id}/test` | Call the board and show what would be saved, without saving |
| `POST /api/v1/admin/job-sources/{id}/fetch` | Fetch now and save |

- API keys go in headers as `${setting:key}` and are read from `naukriradar.settings.key`
  (env `NAUKRIRADAR_SETTINGS_KEY`), so they never sit in the database. Values typed in
  literally are masked in responses.
- A failing board is recorded on the source (`lastRunStatus: FAILED`) and switched off
  after 5 failures in a row; it never fails the request.
- Sources can't point at localhost or private networks unless
  `naukriradar.jobs.allow-private-hosts` is on.

### Fetching every board, and search

| Endpoint | What it does |
|---|---|
| `POST /api/v1/admin/jobs/fetch-runs` | Fetch every enabled board now; returns **202** and the run |
| `GET /api/v1/admin/jobs/fetch-runs/{id}` | Run status, totals and a line per board |
| `GET /api/v1/admin/jobs/fetch-runs` | Recent runs |
| `POST /api/v1/admin/jobs/cleanup` | Close jobs unseen for 7 days, delete ones unseen for 60 |
| `GET /api/v1/jobs?q=&location=&remote=&postedWithinDays=&source=&limit=&cursor=` | Search active jobs, newest first |
| `GET /api/v1/jobs/{id}` | One job with its description |

- Each board runs on its own virtual thread with its own deadline, so a slow or dead
  board doesn't hold up the rest. A run fetches every 6 hours; cleanup runs nightly.
- The same posting on two boards is stored once. Its fingerprint is a hash of title,
  company and city after removing "(m/w/d)", "GmbH", "Pvt Ltd", accents and punctuation,
  and it is a unique key, so MySQL decides what's a duplicate even when boards save at the same time.
- Search uses a MySQL FULLTEXT index. Short or symbol terms such as `go`, `c#` or `.net`,
  which FULLTEXT can't index, fall back to a whole-word match on the title.
- Paging is keyset: pass the `nextCursor` you got back.

### Matching

| Endpoint | What it does |
|---|---|
| `POST /api/v1/me/matches/runs` | Score fresh jobs for you; returns **202**, or 409 if a run is already going |
| `GET /api/v1/me/matches/runs/{id}` | Run status and counts |
| `GET /api/v1/me/matches?minScore=&limit=&cursor=` | Your matches, best first |
| `GET /api/v1/me/matches/{id}` | One match with the reason behind each part of its score |

A run takes your profile from core-api, asks job-service for the ~300 most relevant jobs
(FULLTEXT on your skills and target roles), drops excluded companies and keywords, and
scores each job 0-100 from six factors:

| Factor | Default weight | Scores |
|---|---|---|
| skills | 35 | your skills the posting mentions (whole words: `java` isn't found in `javascript`) |
| title | 25 | how close the title is to a target role, ignoring "Senior", "(m/w/d)" and so on |
| location | 15 | one of your cities, or remote if you're open to it |
| salary | 10 | whether the top of the range reaches your expectation (INR only) |
| experience | 10 | your years against "3-5 years", "5+ yrs" or the title's seniority |
| recency | 5 | newer postings first |

Missing data scores 0.5 ("can't tell") instead of 0. Weights are configuration
(`naukriradar.matching.weights.*`) and are checked at startup. Matches under 20 aren't
kept. Running again refreshes scores instead of adding rows. Runs use a bounded pool of
two workers; when it's full the API answers 503. Scoring 5,000 postings takes about 0.8 s
on a 4 GB laptop.

### Applications

| Endpoint | What it does |
|---|---|
| `POST /api/v1/me/applications/runs` | Turn your matches into applications; **202**, or 409 if a run is going |
| `GET /api/v1/me/applications/runs/{id}` | Run status and counts |
| `GET /api/v1/me/applications?status=&limit=&cursor=` | Your applications, newest first |
| `GET /api/v1/me/applications/needs-you` | The ones to send yourself, with answers ready to copy |
| `GET /api/v1/me/applications/{id}` | One application with its full timeline |
| `POST /api/v1/me/applications/{id}/done` | You applied yourself (safe to repeat) |
| `POST /api/v1/me/applications/{id}/skip` | Not applying (safe to repeat) |
| `PATCH /api/v1/me/applications/{id}/status` | Report INTERVIEW, OFFER or REJECTED |
| `GET /api/v1/me/applications/stats` | Counts per status, sent, today's automatic count |
| `GET/POST/PUT/DELETE /api/v1/admin/portals` | Per-site risk overrides (and browser selectors later) |

Each match at or above your minimum score becomes an application, routed by the risk of
its apply link:

- **HIGH** (LinkedIn, Naukri, Indeed, Workday and others that ban bots) and **MEDIUM**
  (unknown sites): never automated. They wait in "needs you" with your answers prepared.
- **LOW** (Greenhouse, Lever, Workable and other applicant tracking systems): sent by the
  apply engine, if auto apply is on and today's limit has room.

The engine runs in **simulate mode**: it records the application and sends nothing.
Statuses follow a fixed set of moves (SKIPPED can never become OFFER), and every move is
kept in the timeline. Planning locks the profile row, so two runs for the same user can't
apply to a job twice.

### Runtime settings, audit log and jobs

| Endpoint | What it does |
|---|---|
| `GET /api/v1/admin/settings?category=` | Every setting with its value, default and who last changed it |
| `PUT /api/v1/admin/settings/{key}` | Change one; validated, used on the next read, no restart |
| `POST /api/v1/admin/settings/{key}/reset` | Back to the default |
| `GET /api/v1/admin/audit?actor=&action=&targetType=&cursor=` | Who did what, from where, and whether it worked |
| `GET /api/v1/admin/scheduler` | Background jobs: schedule, next run, last result |
| `POST /api/v1/admin/scheduler/{job}/run` | Run a job now |

- Settings cover the risk lists, apply limits and retries, the job schedules, and API keys.
  Unknown keys and bad values (a cron with five fields, `0` attempts) are refused.
- Reads are cached with Caffeine; a change clears its entry after it commits.
- Secret settings are encrypted with AES-256-GCM (fresh IV each time, the setting's key as
  associated data) and are never returned. The service won't start without
  `NAUKRIRADAR_SECURITY_ENCRYPTION_KEY` (32 random bytes, base64).
- Admin actions are audited by an `@Audited` annotation and an aspect; entries are written
  off the request thread and never contain secret values.
- Jobs: `auto-apply` (10:00 India time) runs applying for everyone with auto apply on;
  `retry-failed` (every 30 minutes) retries failed automatic applications. Changing a
  job's cron moves it straight away.

### Redis: running more than one instance

Every service can run as several instances behind the gateway.

| Endpoint | What it does |
|---|---|
| `GET /api/v1/admin/cache/{service}` | That service's caches: hits in memory, hits in Redis, misses, average load time |
| `DELETE /api/v1/admin/cache/{service}/{cache}` | Empty one cache everywhere |

- **Locks**: `SET NX PX` with a random token; release and renewal are Lua scripts that only
  touch our own token. A watchdog renews held locks, so long work keeps its lock and a
  crashed holder frees it within one lease. One fetch run, one fetch per board and one run
  of each core-api job at a time, across all instances.
- **Run leases**: a run being worked on holds a lease. Each instance sweeps every two
  minutes and closes runs left RUNNING without one, so a crash no longer needs a restart
  to clean up, and a restart no longer closes another instance's live runs.
- **Two-level cache**: memory (30 s) then Redis (10 min) then the database, for job details
  and match pages. Evictions go to every instance over pub/sub. Concurrent misses for one
  key load once (stampede guard). If Redis is down the cache just misses.
  Measured on two job-service instances: database 15.8 ms, Redis hit 8.4 ms, memory 4.5 ms.
- **Settings**: a change is announced over Redis; every core-api instance drops it from its
  cache and moves its cron jobs. Only the key is sent, never the value.
- **Cron jobs**: job-service uses ShedLock; core-api's jobs (whose cron is a setting) use the
  lock above and keep it at least 30 s, so instances with slightly different clocks don't
  run the same trigger twice.
- **Rate limits** at the gateway: a token bucket in Lua, per user (or IP), using Redis' own
  clock. Runs: 3 then one per 10 minutes; resume upload: 5 per minute; everything else: 120
  then 2 per second. Refusals are `429` with `Retry-After`. If Redis is down, requests pass.

### Resilience and AI

| Endpoint | What it does |
|---|---|
| `GET /api/v1/admin/resilience/{service}` | Each dependency of that service: circuit state, recent failures, free slots |
| `GET /api/v1/admin/ai/provider-types` | Kinds of provider that can be added, with usual address and example models |
| `GET /api/v1/admin/ai/providers` | Providers in the order they are tried; the first is the primary |
| `POST /api/v1/admin/ai/providers` | Add one: name, type, key, model, optional base URL and prices |
| `PATCH /api/v1/admin/ai/providers/{name}` | Change key, model, address, prices; switch on or off |
| `DELETE /api/v1/admin/ai/providers/{name}` | Remove one |
| `POST /api/v1/admin/ai/providers/{name}/primary` | Use it first; the rest become fallbacks |
| `PUT /api/v1/admin/ai/providers/order` | Set the whole fallback order |
| `GET /api/v1/admin/ai/providers/{name}/models` | The models that account can use, from the vendor |
| `GET /api/v1/admin/prompts` | Every prompt version |
| `POST /api/v1/admin/prompts/{code}/versions` | Add a version (inactive, except a prompt's first) |
| `POST /api/v1/admin/prompts/{code}/versions/{v}/activate` | Use that version; activating an old one is the rollback |
| `POST /api/v1/admin/ai/test` | Try the AI setup with a tiny prompt, optionally one provider |
| `GET /api/v1/admin/ai/usage?from=&to=&groupBy=` | Calls, tokens and cost by provider, model, purpose, user or day |

- Every call to a job board, another service or an AI provider goes through
  retry(circuit breaker(bulkhead(call))). Only transient failures (5xx, 429, network) are
  retried, with exponential backoff and jitter; an open circuit fails at once.
- AI lives in matching-service and is not tied to any vendor or model. Providers are added
  and chosen at runtime through the API above: Anthropic, OpenAI, Gemini, Ollama, and any
  OpenAI-compatible service (Groq, OpenRouter, DeepSeek, Together...) by giving its base URL.
  The model is free text. Ready providers are tried in order until one answers; a change
  applies from the next call. Keys are encrypted (AES-GCM) and never returned, only their
  last four characters. On the very first start, `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`,
  `GEMINI_API_KEY` or `OLLAMA_URL`, if set, are added as providers. With none, the app runs
  without AI.
- Prompts are versioned in the database, with the JSON Schema their answer must match.
  Answers are checked against it: out-of-range numbers are clamped, wrong shapes rejected
  and the next provider tried.
- Every answer is recorded with tokens, cost and latency. Each user has a daily AI budget
  ($0.50 by default); past it, AI is skipped for them.

Errors come back as [Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
(`application/problem+json`), with field errors under `errors`.

## Tests

```bash
./gradlew test                 # everything
./gradlew :core-api:test       # one service
```

Each service tests against its own `*_test` database, recreated on every run. job-service
uses WireMock in place of real job boards. The gateway's
tests route to a fake service, so they need no database. Tests need Redis running.

## Roadmap

- [x] Phase 0: project setup
- [x] Phase 1: profile (users, profiles, skills)
- [x] Microservices layout: gateway, core-api, shared library
- [x] Phase 2: resume upload and skill extraction
- [x] Phase 3: job-service with the first job board (Arbeitnow)
- [x] Phase 4: fetch many boards in parallel, de-duplicate across boards, search
- [x] Phase 5: matching-service, scoring jobs against a profile
- [x] Phase 6: applications, with risk checks and a "needs your click" queue (simulate mode)
- [x] Phase 7: runtime settings, encrypted secrets, audit log, schedulable jobs
- [x] Phase 10: Redis (locks, two-level cache, rate limits, settings sync, ShedLock)
- [x] Phase 11: resilience (retry, circuit breaker, bulkhead) and the AI foundation
- [ ] Phase 12: AI parsing and re-ranking
- [ ] Phase 9: Docker, Flyway, Testcontainers
- [ ] Phases 13–19
- [ ] Phase 8, last: security (JWT at the gateway, roles, Google sign-in). Nothing is
      deployed publicly before it.
