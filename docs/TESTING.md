# Testing

## Backend (JUnit 5, Mockito, H2 in PostgreSQL mode)

```bash
docker run --rm -v "$PWD:/repo" -v scholar-m2:/root/.m2 -w /repo/backend maven:3.9.8-eclipse-temurin-21 mvn -B test
```

Mount the whole repository (not only `backend/`) so the dataset-based regression test can read `dataset/scholarships.csv`; it skips itself if the file is unreachable.

Last full run: **170 backend tests, 0 failures, 0 skipped**; **21 frontend tests** (`cd frontend && npm test`) and a clean `npm run build`.

| Area | Test class | What it pins down |
| :--- | :--- | :--- |
| Eligibility boundaries | `EligibilityEvaluatorTest` | income == ceiling eligible, +1 not; marks == minimum eligible, below not; synonyms, separators, wildcards; **lowering marks never adds a scheme** (synthetic set and every state x category x gender x course combination over the real dataset, marks 100 to 0 in 0.5 steps) |
| Status | `SchemeStatusCalculatorTest` | deadline day is OPEN, next day CLOSED; UPCOMING; unverified or absent dates are UNKNOWN, never CLOSED; non-verified records are UNVERIFIED |
| Expired schemes, ordering | `ScholarshipRagServiceTest` | closed schemes returned as CLOSED and sorted last; unverified past deadline is not closed; no silent result cap; deterministic reason lists passed criteria |
| Ingestion / duplicates | `ScholarshipUpsertServiceTest`, `ScholarshipDataLoaderTest` | same scheme twice = one row; spelling variants collapse; reload is idempotent; CSV edit updates in place and logs deadline history; reviewer-verified dates survive restart; legacy rows archived not deleted; pre-migration rows matched |
| Source monitoring | `SourceMonitorServiceTest`, `DeadlineExtractorTest` | baseline/unchanged/changed; changes flag for review and never touch the live deadline; failed fetch never changes data or status; failure threshold; unreadable pages; ambiguous sources propose no dates; counters ignored |
| Review | `ScholarshipReviewServiceTest` | verifying dates produces OPEN/CLOSED/UPCOMING; history only on real change |
| Chat | `ScholarshipChatServiceTest` | fallbacks for NOT_CONFIGURED/TIMEOUT/ERROR/EMPTY/REJECTED; no-match admits it and skips the model; invented links stripped, footer from records; prompt marks unverified dates; recommendations respect the profile; follow-ups; remembered/explicit profile; hostile or huge input |
| Retrieval, guards | `ChatSupportTest` | tokenizer, relevance, determinism, "keyword not semantic" documented by a test; URL guard incl. Markdown-wrapped links; profile extraction |
| Security | `AdminTokenFilterTest` | token required for admin/write paths, public paths open, off when unset |

## Not covered by automated tests

Real Groq calls (the model is mocked), real source websites (the fetcher is faked; live behaviour is described in `LIMITATIONS.md`), the React components (no frontend test runner is configured), and the full Docker stack. For those, run the stack and check manually:

```bash
docker compose up -d --build
curl -s -X POST localhost:5173/api/v1/scholarships/recommend -H 'Content-Type: application/json' \
  -d '{"annualFamilyIncome":300000,"marks":85,"state":"Maharashtra","category":"General","course":"Engineering","gender":"Female"}'
curl -s -X POST localhost:5173/api/v1/scholarships/chat -H 'Content-Type: application/json' -d '{"message":"Which documents do I need for Pragati?"}'
```

## Added for dynamic updates

| Area | Test class | What it pins down |
| :--- | :--- | :--- |
| Fetching | `PoliteHttpFetcherTest` (real local HTTP server) | retries 503 then succeeds, gives up after N, no retry on 4xx, robots.txt disallow is never requested, bot challenge/CAPTCHA = BLOCKED and not retried, 401 not attempted, connection failure returned not thrown, per-host spacing |
| Extraction | `ExtractorsTest` | structured Buddy4Study data (dates, year, text cleaned), empty/invalid pages yield nothing, closed/rolling notices, one date vs ambiguous dates, window-state status rules |
| Refresh policy | `SchemeRefreshServiceTest` | new deadline applied with history/provenance/reindex, idempotent rerun, extension, expired and missing deadlines, newest cycle wins, conflicts/ambiguity change nothing and flag, ADMIN dates protected, non-auto and aggregator sources only propose, closed/rolling notices, discovered programmes staged not published, no duplicates |
| Discovery and flow | `SourceFlowTest` | sitemap and link discovery filters/caps/idempotence, full fetch-to-record-to-index flow, failed fetch preserves last valid data, unreadable page, unchanged rerun does not reindex, overlapping runs skipped |
| Embeddings, limits | `RemoteEmbeddingAndRateLimitTest` | remote embeddings client (batching, retry, dimension mismatch), retrieval mode wording, per-client rate limits |
| Approval safety | `ApproveDiscoveredTest` | discovered records cannot be approved with empty criteria |
| Chat | `ScholarshipChatServiceTest` | provenance in sources, closed schemes not recommended, semantic only when configured |
| Frontend | `schemes.test.js`, `views.test.jsx` | status sections and ordering, closed hidden by default and opt-in, unverified dates labelled, provenance text, discovered tab, safe chat rendering (links, no raw HTML), history/profile sending |
