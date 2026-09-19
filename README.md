# AutoApply AI - Backend

Spring Boot backend for AutoApply AI platform.

## Tech Stack
- Java 17
- Spring Boot 3.x
- PostgreSQL
- Redis
- JSearch API (Job Fetching)
- Gemini API (AI Matching)
- Selenium (Auto Apply)

## Modules
- `user` - Auth, Profile Management
- `jobs` - JSearch Integration, Job Storage
- `matching` - AI-based Job Matching
- `apply` - Auto Apply Engine

## Setup
1. Configure `application.yml`
2. Run PostgreSQL & Redis
3. `mvn spring-boot:run`
