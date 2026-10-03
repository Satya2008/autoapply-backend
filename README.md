# NaukriRadar — Backend

NaukriRadar pulls job postings from job boards, scores each one against a candidate's
profile, and applies where it is safe to automate. Where it is not (LinkedIn, Naukri,
Workday), it prepares every answer so the candidate can submit in one click.

## Architecture

Microservices from the start, each in its own Gradle module with its own database, in
one repository.

```
client ──► gateway :8080 ─┬─► core-api    :8081 ──► MySQL naukriradar_core
                          └─► job-service :8082 ──► MySQL naukriradar_job ──► job boards
```

| Module | Port | What it owns | Status |
|---|---|---|---|
| `gateway` | 8080 | The only public entry point. Routes requests, and serves one Swagger UI for all services | ✅ |
| `core-api` | 8081 | Users, profiles, skills, resumes; later applications | ✅ |
| `job-service` | 8082 | Job boards as configuration; fetching, cleaning and storing jobs | ✅ |
| `matching-service` | 8083 | Scoring jobs against profiles | Phase 5 |
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

## Run locally

1. Start MySQL 8 and create one database per service, plus one for its tests:

   ```sql
   CREATE DATABASE naukriradar_core;       -- development
   CREATE DATABASE naukriradar_core_test;  -- tests only
   CREATE DATABASE naukriradar_job;
   CREATE DATABASE naukriradar_job_test;
   ```

   Credentials default to `root` / `root`; override with `DB_USERNAME` and `DB_PASSWORD`.

2. Start each service in its own terminal:

   ```bash
   ./gradlew :core-api:bootRun
   ./gradlew :job-service:bootRun
   ./gradlew :gateway:bootRun
   ```

   Every service runs with a 256 MB heap, so several fit on a small laptop.

| What | URL |
|---|---|
| Swagger UI (all services) | http://localhost:8080/swagger-ui.html |
| Gateway health | http://localhost:8080/actuator/health |
| core-api health | http://localhost:8081/actuator/health |
| job-service health | http://localhost:8082/actuator/health |

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

Errors come back as [Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
(`application/problem+json`), with field errors under `errors`.

## Tests

```bash
./gradlew test                 # everything
./gradlew :core-api:test       # one service
```

Each service tests against its own `*_test` database, recreated on every run. job-service
uses WireMock in place of real job boards. The gateway's
tests route to a fake service, so they need no database.

## Roadmap

- [x] Phase 0: project setup
- [x] Phase 1: profile (users, profiles, skills)
- [x] Microservices layout: gateway, core-api, shared library
- [x] Phase 2: resume upload and skill extraction
- [x] Phase 3: job-service with the first job board (Arbeitnow)
- [x] Phase 4: fetch many boards in parallel, de-duplicate across boards, search
- [ ] Phase 5: matching-service, scoring jobs against a profile
