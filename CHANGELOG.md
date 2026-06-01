# Changelog

## 2026-06-01

- Add retrieval slotting, prompt budget allocation, and memory-layer routing to make prompt assembly and retrieval planning more adaptive under budget limits.
- Introduce pipeline runtime execution, recovery, and trace persistence support for the orchestrator, with richer stage metadata across prompt and retrieval flows.
- Reorganize dashboard, flashcard, and admin services into dedicated packages and add learning recommendation plus trace analytics support.
- Refine interview, review, dashboard, and knowledge flows to use the new DTO layout, retrieval plan models, and updated orchestration context.
- Expand test coverage and fix compatibility issues so the current orchestrator, API smoke, dashboard, MQ post-process, and profile merge tests pass end to end.

## 2026-05-28

- Refactor the interview orchestrator into a clearer staged pipeline, and add orchestration metrics plus richer result metadata.
- Add and refine AI agents for follow-up questioning, scoring, resume/profile extraction, graph extraction, community reporting, and drift detection.
- Improve prompt construction with reusable builders and richer prompt context handling.
- Strengthen GraphRAG retrieval with retrieval planning, HYDE query expansion, local search adjustments, and retriever/query-classifier updates.
- Expand profile and interview domain support, including patch DTOs, merge rules/modes, transcript and resume fields, and dashboard/service updates.
- Add interview post-processing MQ configuration and status handling, plus related producer/consumer updates.
- Add database migrations, cache support, and test coverage for the new orchestration and post-processing flow.
