# AgentCareer

AgentCareer is a Spring Boot backend for an AI interview and career coaching workflow. The repository keeps the runtime governance code that makes the system observable and resilient, including trace persistence, prompt runtime metrics, fallback handling, and stage-level orchestration telemetry.

## What stays in the repo

- Core application code, tests, and migration scripts
- Runtime observability code such as `TraceContext`, `TracePersistenceService`, `TraceAnalyticsService`, `PromptRuntimeTracker`, and orchestration metrics
- Benchmark definitions and reusable datasets that explain how retrieval, runtime trace, and multi-agent decision experiments are evaluated

## What does not stay in the repo

- Generated benchmark run logs such as `*_run_log.csv`
- One-off benchmark result summaries produced from local experiments

Benchmark tests are written so the repository remains self-contained even when generated run logs are absent. If you want to run a real experiment, place the generated output files back under `src/main/resources/data/**` locally without committing them.

## Run locally

```bash
./mvnw test
./mvnw spring-boot:run
```

Use `application.yml`, Docker services, and the database migrations in `src/main/resources/db/migration` to wire up the required infrastructure.
