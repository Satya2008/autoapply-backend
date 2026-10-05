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
| `apply-worker` | 8084 | Fills and submits application forms in headless Chrome, for low-risk portals | ✅ |
| `notification-service` | 8085 | Email and Telegram messages, each sent once | ✅ |
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

   Tests also need an S3-compatible store on `localhost:8333` with the key `test`/`test`.
   [SeaweedFS](https://github.com/seaweedfs/seaweedfs/releases) is a single binary:

   ```bash
   weed server -dir=./s3data -s3 -s3.port=8333 -s3.config=scripts/seaweedfs-s3.json -volume.port=8380 -volume.max=40
   ```

   The app itself keeps files on local disk unless `naukriradar.storage.type=S3`.

   Kafka on `localhost:9092` carries the events between services (tests need it too). On
   Windows without Docker, unpack Apache Kafka and run `scripts/start-kafka.ps1` (single
   node, KRaft, 256 MB heap). Without Kafka the services still run; events wait in the
   outbox until it is back.

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

### AI features: parse once, score many

| Endpoint | What it does |
|---|---|
| `GET /api/v1/jobs/{id}` | Now with `requirements`: required skills, minimum years, seniority, work mode |
| `POST /api/v1/admin/jobs/reparse` | Parse every active job again (after the prompt improved), in the background |
| `POST /api/v1/me/resume/parse` | Read the resume with AI again, in the background |
| `GET /api/v1/me/matches/{id}` | Now with `aiScore`, `aiReasons` and who scored it |
| `POST /api/v1/me/applications/{id}/cover-letter?regenerate=` | Write (or rewrite) a cover letter |

- **Jobs are parsed once**, by job-service after each fetch and every 15 minutes, and every
  user's matching reuses the result. The local scorer then compares skills against the
  job's actual requirements ("has 3 of the 4 skills the job asks for; missing Kafka")
  and uses the parsed minimum years.
- **AI reviews only the top 10 local matches** per run, not every job. For 100 users and
  1,000 new jobs that is about 1,000 + 100 x 10 = 2,000 AI calls instead of 100,000 if
  every user-job pair went to the AI: 50 times fewer.
- **Answers are cached** in Redis by prompt version and a hash of the exact text sent, so
  the same profile and job are never paid for twice; "regenerate" skips the cache.
- **Cheap vs strong**: parsing and scoring use each provider's everyday model; cover
  letters use its `strongModel` when one is set.
- **Prompt injection**: postings and resumes are untrusted. They go inside tags with an
  instruction to treat them as data, and the answer must match a JSON Schema, so an
  instruction hidden in a posting can at most produce a wrong score, never an action.
- The resume is read with AI after upload, in the background: skills the dictionary missed
  are added with years; skills the candidate typed are never changed.
- **Without AI everything still works**: matching keeps its local scores and says why AI
  was skipped, jobs stay unparsed until AI is back, and a cover letter comes back as a plain
  draft from a template, filled only with the candidate's own facts.
- AI lives in matching-service; job-service and core-api ask it through
  `POST /internal/v1/ai/run`, so providers, fallback, cache, budgets and cost tracking stay
  in one place.

### Semantic matching, RAG and evals

| Endpoint | What it does |
|---|---|
| `GET /api/v1/me/skill-gap` | The skills missing from your profile that would bring the most extra matches |
| `POST /api/v1/me/applications/{id}/cover-letter` | Now built on the parts of your resume that fit the job, with a claim check |
| `POST /api/v1/me/applications/{id}/screening-answers` | Answers a form's questions from your profile and resume |
| `GET /api/v1/admin/ai/embeddings` | Which embedding model is in use and how many jobs have vectors |
| `POST /api/v1/admin/ai/embeddings/reindex` | Embed recent jobs that have no vector for the current model |
| `POST /api/v1/admin/ai/batch-matching/run` | The nightly batch, now: index catch-up, then rematch active users |
| `GET/POST/DELETE /api/v1/admin/evals/cases` | The golden set: hand-scored (profile, job) pairs |
| `POST /api/v1/admin/evals/runs` | Run an eval (202): `MATCHER` (keyword vs hybrid) or `PROMPT` (one prompt version) |
| `GET /api/v1/admin/evals/runs/{id}` | Its numbers: MAE, Spearman, precision, recall, per-case results |

- **Hybrid matching.** The shortlist is FULLTEXT keyword search plus the jobs nearest in
  meaning from a vector index, merged by reciprocal rank fusion. A new `semantic` factor
  scores how close the job's meaning is to your roles and skills, so "Spring Microservices
  Engineer" now counts for a "Java Backend Developer".
- **Any embedding model, or none.** Set `embeddingModel` on any provider (OpenAI-compatible,
  Gemini, Ollama) and it is used; with none, a built-in local embedder (feature hashing plus
  concept groups, no network, no cost) is. If the chosen provider fails, the local one stands
  in for that call. Vectors of different models are never compared.
- **Measured, not guessed.** On the built-in golden set (16 cases), keyword-only scoring is off
  by 22.3 points on average and finds 3 of 10 real matches; hybrid with the local embedder is
  off by 19.9 and finds 6 of 10 (F1 0.46 to 0.75). Run `MATCHER` again after changing models.
- **Prompt changes need numbers.** A new version of `job-fit` can be activated only after a
  `PROMPT` eval of that version passes (average miss at most 20 points, at least 90% usable
  answers). Going back to a version that was live before needs no new eval.
- **RAG.** The resume is cut into chunks by section; for each job or question the best chunks
  are picked (70% meaning, 30% BM25 keywords) and only those go into the prompt.
- **Claim check.** Skills, numbers ("5 years", "40%") and employers in an AI letter are checked
  against the resume and profile; a letter claiming something they don't back is written again,
  naming what to leave out, and any claim still unbacked is listed in `unsupportedClaims`.
- **Screening answers.** Questions a profile field answers (notice period, years, location,
  expected salary) are answered from it, free and exact; the rest go to AI in one call, which
  must say "not answerable" rather than guess; what's left is marked for the user.
- **Nightly batch** (02:30 IST): vector index catch-up and a rematch of every active user.
  Anthropic prompts mark their fixed system part for prompt caching.
- Why vectors live in MySQL and are searched in memory: [ADR 0001](docs/adr/0001-vector-store.md).

### File storage

| Endpoint | What it does |
|---|---|
| `POST /api/v1/me/resume/upload-url` | A short-lived link to PUT the resume straight into storage |
| `POST /api/v1/me/resume/confirm` | After that upload: check, parse and save it as the resume |
| `GET /api/v1/me/resume/file` | With S3, a redirect to a short-lived download link |
| `GET /api/v1/me/resume/download-url` | The download link itself |
| `POST /api/v1/admin/storage/migrate` | Copy files from local disk into S3 after switching; safe to rerun |

- `naukriradar.storage.type`: `LOCAL` (disk, one instance) or `S3` (any S3-compatible
  store: AWS S3, Cloudflare R2, SeaweedFS... set `storage.s3.endpoint`, `bucket`, keys).
  Container disks are temporary and two instances don't share one, so production uses S3.
- Presigned links are signed for one key, one method and one content type, and expire in
  10 minutes. Upload keys live under `uploads/<user>/`; confirming someone else's key is a
  404. The confirmed file goes through the same checks as a normal upload (real type from
  its bytes, size, parsing) and the pending upload is deleted.
- The multipart `POST /api/v1/me/resume` still works with either storage.

### Events (Kafka + outbox)

```
fetch run --jobs.ingested--> parse with AI --jobs.parsed--> rematch active users
   --match.created (key: user)--> auto apply --apply.completed--> live screen (SSE)
```

| Endpoint | What it does |
|---|---|
| `GET /api/v1/me/applications/stream` | Server-Sent Events: the user's apply runs as they finish |
| `GET /api/v1/admin/events/{service}/dlq?topic=` | Events that failed every retry, with the error |
| `POST /api/v1/admin/events/{service}/dlq/{id}/replay` | Send one again after the fix |
| `GET /api/v1/admin/events/{service}/outbox` | Pending events and how long the oldest has waited |

- **Outbox**: an event is a row written in the same transaction as the change it describes,
  so a commit and its event can't get separated (the dual-write problem). A relay sends the
  rows to Kafka in order; one instance relays at a time.
- **At least once, processed once**: the relay may send twice after a crash, so consumers
  record each event id in `processed_events`, together with their work, or for long work
  after it, relying on business keys (one run per user, one application per user and job).
- **Ordering**: user events are keyed by user id, so one user's events stay in order on one
  partition while different users are handled in parallel.
- **Dead letters**: a failing event is retried twice, then saved with its error and sent
  to `<topic>.dlq`; the consumer moves on. Replay keeps the original event id.
- **Envelope**: event id, type, version, time, source, key, payload, so payloads can change
  shape without breaking older consumers.
- Measured locally: one fetch run reached the user's live screen 20 s later, 16 s of it the
  board fetch itself; nothing was called by hand in between.

### Applying in a browser (apply-worker)

`naukriradar.applications.mode` decides how queued applications are sent:

- `SIMULATE` (the default): nothing reaches any employer; the application is recorded only.
- `BROWSER`: core-api hands the application to the apply worker, which fills the portal's
  form in headless Chrome and submits it. Only LOW-risk portals are ever queued, as before.

| Endpoint | What it does |
|---|---|
| `POST /api/v1/admin/portals/{id}/dry-run` | Fill a portal's form with clearly fake answers in the worker's browser; press nothing |
| `GET /internal/v1/portals?domain=` | core-api, for the worker: how to fill a portal's form (not routed by the gateway) |

```
QUEUED --dispatch--> SENDING --Redis delay queue, 3-8 min apart per user--> apply.requested
   --> apply-worker: headless Chrome fills and submits --> apply.completed
   --> SUBMITTED | FAILED (retried later) | NEEDS_YOU (the candidate finishes it)
```

- A separate service because a browser is heavy (a few hundred MB) and can crash: the worker
  can be scaled, restarted or killed without core-api noticing; applications wait in Kafka.
- The worker knows no site. Each portal has selectors set by an admin: form field to CSS
  selector, plus `submit` and `success` (something that only shows once it went through).
- One browser at a time by default (`naukriradar.browser.pool-size`); each application gets a
  fresh Chrome that is closed right after.
- A field it can't find, or no success check, never becomes "submitted": the application goes
  to the candidate with the reason. Failed attempts leave a screenshot.
- Never twice: every attempt is recorded before the browser starts. If the worker dies after
  pressing submit and the event is delivered again, the worker reports "unknown, please
  check" instead of submitting a second application.
- Applications of one user go out in order (the topic is keyed by user) and paced: a Redis
  sorted set holds each one until its slot, instead of a thread sleeping.

### Notifications (notification-service)

| Endpoint | What it does |
|---|---|
| `GET/PUT /api/v1/me/notification-preferences` | Email, Telegram, daily digest, apply-run updates on or off |
| `POST /api/v1/me/telegram/link` | A one-time code; send `/start CODE` to the bot to link the chat |
| `POST /api/v1/admin/notifications/test` | Send a test email or Telegram message now |
| `POST /api/v1/telegram/webhook` | Telegram's updates for the bot; refused without the webhook secret |

- core-api knows the users, so it decides who gets a message and where (preferences,
  addresses) and puts `notify.requested` in its outbox. notification-service only renders
  the Mustache template and sends it on each channel: the fan-out.
- Each (event, channel) is logged: a repeated event sends nothing, and if email failed but
  Telegram worked, a retry sends only the email.
- SMTP down: the event is retried, then kept in the dead letters with the error; a replay
  after SMTP is back sends what's missing. Nothing upstream waits on any of this.
- The daily digest (09:00, a runtime setting) uses real numbers: sent in the last 24 hours,
  waiting for the user, new picks, the top three waiting. Users with nothing new get nothing.
- Email needs `SMTP_HOST`/`SMTP_PORT` (and credentials); Telegram needs `TELEGRAM_BOT_TOKEN` and
  `TELEGRAM_WEBHOOK_SECRET`. Without them those channels are simply off.

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
- [x] Phase 12: AI parsing of jobs and resumes, AI review of top matches, cover letters
- [x] Phase 13: object storage (S3-compatible), presigned upload and download, migration
- [x] Phase 14: Kafka events with transactional outbox, idempotent consumers, dead letters, SSE
- [x] Phase 15: apply-worker, applying in headless Chrome with pacing and crash-safe attempts
- [x] Phase 16: notification-service, email and Telegram with per-channel dedup and a real daily digest
- [x] Phase 18: semantic matching (hybrid shortlist, any embedding model or a local one), RAG cover letters
  and screening answers with a claim check, skill gap, evals that gate prompt changes, nightly batch
- [ ] Phase 9: Docker, Flyway, Testcontainers
- [ ] Phases 17–19
- [ ] Phase 8, last: security (JWT at the gateway, roles, Google sign-in). Nothing is
      deployed publicly before it.
