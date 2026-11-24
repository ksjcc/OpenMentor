# Changelog

## 2026-11-23

### Backend

- Fix the build/startup blockers: `InterviewController` now passes `Idempotency-Key` through to `InterviewService`, `InterviewFallbackService`, `IdempotencyGuard` and `LlmConcurrencyGuard` are real Spring beans (`@Component`, `@Slf4j`, correct record accessor/`ttl`), and the redundant flagless `RedisTemplate` is replaced with an injected one. Verified by actually booting the app: every startup blocker above was found that way.
- Add a resume library: `resume` table + `Resume`/`ResumeMapper`/`ResumeService`/`ResumeController`, multi-resume per user, default resume, ownership checks.
- Support uploading `pdf` / `docx` / `txt` resumes: `ResumeTextExtractor` (PDFBox + POI + UTF-8/GB18030 fallback) with a dedicated unit test. Adds `pdfbox`, `poi-ooxml` and a newer `commons-io` (POI 5.2.3 ships 2.11, which lacks `IOIterator` — the first test run failed with `NoClassDefFoundError`).
- Reject "empty shell" profile parses: when the model is unavailable the agent layer still returns a valid JSON with every field empty, which used to be stored as a successful parse. `ResumeService.isMeaningfulProfile` now treats that as a failure so the UI no longer shows 「已解析」 on an empty profile.
- Inject the selected resume into the interviewer context: `InterviewHarnessService.CandidateContext` carries interview type, target role, topic, resume and project summary, and every turn's user message is prefixed with a「本场面试设定」block. The interviewer system prompt documents that convention so questions build on the resume instead of reciting it.
- `resumeId = 0` from the client means "explicitly no resume"; leaving it out falls back to the user's default resume.
- Fix profile initialisation: `ProfileService.normalizeInitialProfile` returned an empty string, so `readTree("")` made every `/api/profile/init` call fail.
- Persist per-answer scores (`interview_message.score`/`score_detail`) and the session average (`interview_session.score`); the review report now exposes real dimensions.
- Add `GET /api/interview` (interview history) and `GET /api/interview/{sessionId}` (full report: transcript, score, extraction, flashcards, next plan).
- Add `GET /api/memory/recent` and enrich the dashboard with `totalSessions`, `averageScore`, `scoreTrend`/`trendLabels` plus topic/score/turns/weakness on recent interviews.
- `POST /api/interview/{id}/answer` and `POST /api/knowledge/ask` accept plain-text bodies again (quoted JSON bodies are stripped).

### Database

- New migration `V20261123_01__add_resume_library.sql` (resume table + interview_session/interview_message columns), written idempotently because it must be applied manually — the project has no Flyway runtime. `V20260526_01` had never been applied on the dev database, so `interview_session` was missing its `post_process_*` columns and every interview query failed.

### Frontend

- Remove all backend-connectivity surface: sidebar health indicator, status bar, `baseUrl` setting, "test connection" action, endpoint cheat-sheet card, and the demo-mode switch/badge.
- Make the console same-origin only (`/api/**` relative paths); `frontend/serve.mjs` now reverse-proxies `/api` to the backend for local development.
- Drop the `demo` flag from persisted settings and strip legacy `baseUrl` / `demo` keys on load.
- New 「简历库」page (upload / paste / parse / set default / delete) and a resume picker on the interview setup form.
- Interview report page now reads the real report API, shows the transcript, and no longer falls back to sample data.
- Overview / knowledge graph / memory pages are wired to the real DTOs (no more sample data fallback); review training still keeps its plan fallback.

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
