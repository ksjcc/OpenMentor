# Changelog

## 2026-11-25

### Review training — rebuilt around daily practices

- The review page is now day-based: `#/review` lists the next 7 days (题量 / 已完成 / 正确率 / 错误次数), and clicking a day opens that day's practice at `#/review?date=YYYY-MM-DD`.
- A card belongs to the day of its `next_review_time`; overdue cards roll into today so they can still be cleared. Answering reschedules the card, which removes it from that day's list.
- Every answer writes a `review_record` row (with `practice_date` + topic snapshot), so each practice reports 正确率、答对/答错数, and the day's summary survives a page reload.
- Each question has a 「关联知识点」 button (top-right) that opens a modal with related historical flashcards: same topic first, then topics that are one hop away in the knowledge graph.
- Flashcards now carry a `topic` (from the interview topic, falling back to the first weakness). Duplicate questions are no longer inserted twice by repeated post-processing.
- Backend: new `ReviewPracticeService`, endpoints `GET /api/review/calendar`, `GET /api/review/practice`, `GET /api/review/related`, and `POST /api/review/submit` now accepts `userId` to return the day's running totals. Migration `V20261125_02` adds `flashcard.topic`, `review_record.topic`/`practice_date` + index.
- Removed the sample-data fallback from the review page (weak-point card now shows a real empty state).

### Interview setup (To C)

- Remove the 「这一场会怎么进行」 explainer column from the interview setup page — it was internal onboarding copy, not something a candidate needs.
- Make 面试主题 optional: leaving it blank no longer blocks starting an interview, and `interview_session.topic` stays NULL. The default draft topic is now 「JAVA八股拷打」. Record/report lists show 「通用面试」 when a session has no topic.
- Add a 「把这次面试记入长期记忆」 switch (default on). When off, the interview still produces a full report but skips profile merge / memory chunks / flashcards / graph indexing; the session records `post_process_status = SKIPPED`.
- Surface the choice: the running session panel shows 记忆沉淀 已开启/本场不记入, and the report page shows 已记入/未记入长期记忆.

### Backend

- `POST /api/interview/start` accepts optional `remember` (default true) and optional `topic`; new column `interview_session.remember_in_memory` (migration `V20261125_01`).
- `InterviewReportDTO` / `InterviewSummaryDTO` expose `remembered` so the UI can render the choice.
- `IdempotencyGuard.remember` no longer prints a full stack trace when Redis is down (it degrades to the in-process cache; the stack spam made every "start interview" click look like a failure).

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
