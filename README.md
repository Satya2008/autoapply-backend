# NaukriRadar — Backend

NaukriRadar pulls job postings from job boards, scores each one against a candidate's
profile, and applies where it is safe to automate. Where it is not (LinkedIn, Naukri,
Workday), it prepares every answer so the candidate can submit in one click.

## Tech stack

- Java 21, Spring Boot 4.1, Gradle (wrapper)
- MySQL 8
- Spring Data JPA, Bean Validation, Actuator, springdoc OpenAPI

## Run locally

1. Start MySQL 8 and create the two databases:

   ```sql
   CREATE DATABASE naukriradar;       -- development
   CREATE DATABASE naukriradar_test;  -- tests only
   ```

2. Set credentials if they differ from the local default (`root` / `root`):

   ```bash
   export DB_USERNAME=...
   export DB_PASSWORD=...
   ```

3. Start the app (the `dev` profile is the default):

   ```bash
   ./gradlew bootRun
   ```

| What | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |

## Tests

```bash
./gradlew test
```

Tests run with the `test` profile against `naukriradar_test`, and the schema is recreated
on every run.

## Roadmap

The backend is built in 20 phases, starting as a modular monolith. Later phases add Redis,
Kafka, AI matching and payments, and split it into microservices.

**Current: Phase 0, project setup.**
