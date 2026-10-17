# Operations: sources, discovery, status, refresh, review

## Where data comes from

* `dataset/scholarships.csv`: curated scheme content (26 schemes). Source of truth for eligibility criteria.
* `dataset/source_registry.csv`: the sources scheme dates and pages are read from. Columns: `source_id, organization, portal_name, source_url, document_title, access_date, schemes_covered, verification_notes, kind, adapter, trust, item_match, auto_apply, discover, link_filter`.

| Column | Meaning |
| :--- | :--- |
| `kind` | `PORTAL` (landing/listing page), `SCHEME_PAGE` (one scheme), `BRAND_PAGE` (a provider page listing several programme cycles), `SITEMAP` (discovery only) |
| `adapter` | `HTML` (generic text extraction) or `B4S` (structured JSON embedded in Buddy4Study programme pages) |
| `trust` | `OFFICIAL`, `PARTNER` (the provider's application partner) or `AGGREGATOR` (never updates records) |
| `schemes_covered` | dataset ids this source maps to (one scheme maps to one source) |
| `item_match` | regex choosing the programme on a multi-programme page (for `SITEMAP`: which child sitemaps to read) |
| `auto_apply` | whether dates read here may update a VERIFIED record without a human |
| `discover` / `link_filter` | follow links (or sitemap entries) matching the filter to find scheme pages |

* `source_snapshots` holds fetch state for registry rows **and** for pages found by discovery (`discovered=true`).

## Discovery

`SourceDiscoveryService` finds individual scheme pages, not homepages:
* `SITEMAP` sources: reads the sitemap (child sitemaps selected by `item_match`), keeps `/page/` URLs on the same host matching `link_filter`, registers each as a `BRAND_PAGE` read with the structured adapter. Registered: Buddy4Study (programme pages of Tata, HDFC, Cummins, Siemens, Reliance, Kotak, Aditya Birla, SBI, ONGC, Infosys, Legrand, Amazon, JK Tyre, Mahindra, Bajaj, LIC, JSW, Vedanta, Wipro, Hero, TCS, L&T where present; loan pages excluded).
* Portals with `discover=true` (MahaDBT): same-host anchors matching `link_filter` become `SCHEME_PAGE` sources (HTML adapter, never auto-applying). PDFs and off-site links are skipped.
* Idempotent (a URL registers once; curated URLs are never re-registered) and capped (`INGESTION_MAX_DISCOVERED`, default 60 per source).
* Only pages robots.txt allows are fetched. No logins, CAPTCHAs or challenge pages are attempted; those report `BLOCKED` / `AUTH_REQUIRED`.

## Fetching

`PoliteHttpFetcher`: identifies itself as `ScholarAI-SourceMonitor/1.0`; honours robots.txt (a non-robots body such as an SPA shell means "no rules"); spaces requests to the same host (`INGESTION_MIN_INTERVAL_MS`, default 1500); retries timeouts, 429 and 5xx with exponential backoff and `Retry-After` (`INGESTION_MAX_ATTEMPTS`, default 3); never retries other 4xx or TLS errors; timeouts `app.ingestion.fetch-timeout-ms`.

## What gets extracted

* **Buddy4Study (B4S)**: for every programme cycle on the page: title, academic year, application launch date, deadline, eligibility text, benefits, documents, application link.
* **HTML pages**: title, academic year, application link, a date found next to deadline wording (the nearest date per phrase; several different dates = ambiguous, nothing applied), and explicit notices ("applications are now closed", "rolling basis").

## Refresh rules (`SchemeRefreshService`)

1. Only `auto_apply` sources that are not `AGGREGATOR` may change a VERIFIED record. Others record a PROPOSED date and flag the scheme.
2. A date a reviewer verified (`deadline_origin=ADMIN`) is never overwritten; a differing source date is proposed and flagged.
3. Conflicting deadlines for the same programme and cycle, an ambiguous page, or a "closed" notice contradicting a future verified deadline: **nothing changes**, the scheme is flagged, the last valid data stays.
4. Older cycles than the one on record are ignored; the newest cycle wins.
5. An applied change writes `scholarship_deadline_history` (state `APPLIED`, origin `SOURCE_AUTO`, the field/wording it came from and the URL), sets `deadline_verified_on` = fetch date, `deadline_source_url`, `deadline_fetched_at`, then re-indexes the scheme's vector chunks after the transaction commits.
6. A fetch failure, block, empty page, or page without structured data changes **no** scheme data. After 3 consecutive failures the covered schemes are flagged.
7. Programmes found on partner pages but not in the dataset become `PENDING_REVIEW` records (never in `/recommend`, never labelled open). Eligibility numbers are parsed from free text and are not trusted until a person approves the record (`POST /api/v1/admin/scholarships/{id}/approve`). They are listed at `GET /api/v1/scholarships/discovered` and in the UI's "Discovered, unreviewed" tab.
8. Each source is processed in one transaction; only one run executes at a time (an overlapping trigger returns `SKIPPED`).

## Status

`OPEN`, `CLOSED`, `UPCOMING`, `UNKNOWN`, `UNVERIFIED`, computed per request in IST from verified dates (see README). A "rolling" window counts as OPEN, and an official "closed" notice as CLOSED, only when the official page states it and the evidence is stored (`window_state`, `window_evidence`) with a verification date. `/recommend` and `GET /api/v1/scholarships` hide CLOSED by default (`include_closed=true` / `includeClosed=true`).

## Schedule and manual runs

Daily at 03:30 IST (`INGESTION_CRON`; `INGESTION_ENABLED=false` disables). Manually: `POST /api/v1/admin/sources/check` (synchronous; a full run with discovery takes several minutes because of per-host spacing). State: `GET /api/v1/admin/sources`.

## Review workflow (set `ADMIN_API_TOKEN`; send `X-Admin-Token`)

* `GET /api/v1/admin/scholarships/review`: flagged schemes and why. `GET /api/v1/admin/scholarships/{id}/deadline-history`.
* `POST /api/v1/admin/scholarships/{id}/verified-dates`: a reviewer's verified dates (origin `ADMIN`; protected from automatic overwrite).
* `POST /api/v1/admin/scholarships/{id}/review/clear`; `POST /api/v1/admin/scholarships/{id}/approve` for discovered programmes (indexes them and makes them VERIFIED; set category/state/course/gender columns first, because discovered records have none and would otherwise match everyone).

## Embeddings

Default: lexical keyword vectors. For genuine semantic retrieval set `EMBEDDING_PROVIDER=openai-compatible`, `EMBEDDING_BASE_URL`, `EMBEDDING_API_KEY`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` (the model's output size). The startup loader recreates the derived `vector_store` table when the dimension changes and rebuilds it from PostgreSQL. `retrievalMode` in chat responses is `semantic` only in that configuration. Not exercised against a real embedding provider in this repository's tests (a local stub server verifies the client).

## Rate limits and security

`/chat` 20 and `/recommend` 60 requests per minute per client (`RATE_LIMIT_*`); 429 with `Retry-After`. `ADMIN_API_TOKEN` guards admin and write endpoints. Secrets only from environment variables.

## Backups

`docker exec scholarship-pgvector pg_dump -U postgres -d scholarship_db -Fc > backup.dump`. Dumps made before the two large changes are in `backups/` (git-ignored).
