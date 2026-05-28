# Changelog

## 2026-05-28

- Refactor the interview orchestrator into a clearer staged pipeline, and add orchestration metrics plus richer result metadata.
- Add and refine AI agents for follow-up questioning, scoring, resume/profile extraction, graph extraction, community reporting, and drift detection.
- Improve prompt construction with reusable builders and richer prompt context handling.
- Strengthen GraphRAG retrieval with retrieval planning, HYDE query expansion, local search adjustments, and retriever/query-classifier updates.
- Expand profile and interview domain support, including patch DTOs, merge rules/modes, transcript and resume fields, and dashboard/service updates.
- Add interview post-processing MQ configuration and status handling, plus related producer/consumer updates.
- Add database migrations, cache support, and test coverage for the new orchestration and post-processing flow.
