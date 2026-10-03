# NaukriRadar — Backend

NaukriRadar pulls job postings from job boards, scores each one against a candidate's
profile, and applies where it is safe to automate. Where it is not (LinkedIn, Naukri,
Workday), it prepares every answer so the candidate can submit in one click.

## Architecture

Microservices from the start, each in its own Gradle module with its own database, in
one repository.

```
client ──► gateway :8080 ──► core-api :8081 ──► MySQL naukriradar_core
```

| Module | Port | What it owns | Status |
|---|---|---|---|
| `gateway` | 8080 | The only public entry point. Routes requests, and serves one Swagger UI for all services | ✅ |
| `core-api` | 8081 | Users, profiles, skills; later resumes and applications | ✅ |
| `job-service` | 8082 | Fetching, de-duplicating and searching jobs | Phase 3 |
| `matching-service` | 8083 | Scoring jobs against profiles | Phase 5 |
| `apply-worker` | 8084 | Browser automation for low-risk portals | Phase 15 |
| `notification-service` | 8085 | Email, Telegram, daily digest | Phase 16 |
| `libs/common-web` | — | Shared Problem Details errors and the `X-User-Id` caller lookup | ✅ |

## Tech stack

- Java 21, Spring Boot 4.1, Spring Cloud Gateway 2025.1, Gradle (wrapper)
- MySQL 8, one database per service
- Spring Data JPA, Bean Validation, Actuator, springdoc OpenAPI, Lombok

## Run locally

1. Start MySQL 8 and create core-api's databases:

   ```sql
   CREATE DATABASE naukriradar_core;       -- development
   CREATE DATABASE naukriradar_core_test;  -- tests only
   ```

   Credentials default to `root` / `root`; override with `DB_USERNAME` and `DB_PASSWORD`.

2. Start each service in its own terminal:

   ```bash
   ./gradlew :core-api:bootRun
   ./gradlew :gateway:bootRun
   ```

   Every service runs with a 256 MB heap, so several fit on a small laptop.

| What | URL |
|---|---|
| Swagger UI (all services) | http://localhost:8080/swagger-ui.html |
| Gateway health | http://localhost:8080/actuator/health |
| core-api health | http://localhost:8081/actuator/health |

## Try the API

There is no login yet. The caller is identified by an `X-User-Id` header. From Phase 8 the
gateway verifies a JWT and sets this header itself.

1. `POST /api/v1/dev/users` with `{"email": "you@example.com"}` and copy the returned `id`.
2. In Swagger UI, click **Authorize** and paste that id.
3. Call `GET/PUT /api/v1/me/profile` and `GET/PUT /api/v1/me/skills`.

Errors come back as [Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
(`application/problem+json`), with field errors under `errors`.

## Tests

```bash
./gradlew test                 # everything
./gradlew :core-api:test       # one service
```

core-api's tests run against `naukriradar_core_test`, recreated on every run. The gateway's
tests route to a fake service, so they need no database.

## Roadmap

- [x] Phase 0: project setup
- [x] Phase 1: profile (users, profiles, skills)
- [x] Microservices layout: gateway, core-api, shared library
- [ ] Phase 2: resume upload and skill extraction
