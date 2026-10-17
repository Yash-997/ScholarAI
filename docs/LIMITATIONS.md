# Known limitations

Read this before relying on the data.

## Data

* 26 curated schemes (plus 8 staged, unreviewed discovered programmes). The dataset is curated by hand, not complete, and amounts/ceilings change every cycle.
* (Superseded: dates now come from source pages, see below.) **Earlier only two deadlines were marked verified** (Reliance 2026-10-05, Tata Capital 2026-10-26), carried over from `source_registry.csv` notes ("accessed 2026-10-17"). They were **not re-verified live** during this work. Cummins' 2026-09-30 is a secondary-source date and shows as UNKNOWN. Every other scheme has no dates, so its status is UNKNOWN.
* `last_verified` is the registry's access date, not a fresh check.
* Some criteria are simplified in the dataset (for example Karnataka's per-category income ceilings use the higher bound; Cummins is location-restricted but modelled as state). A scheme may show as a match when a finer rule excludes the student.
* A student has one category. Someone who is both OBC and PwD matches only schemes for the single category chosen.
* An unspecified gender matches female-only schemes (the field is simply not filtered).
* 19 older rows from a previous seed were archived (`ARCHIVED_LEGACY`), not deleted. They are hidden from results.

## Source monitoring (live run of 2026-10-17, from the Docker container)

* Registered 20 sources; discovery added 83 more pages (55 MahaDBT scheme pages, 28 Buddy4Study programme pages). Of 103 sources: 13 refreshed records, 28 unchanged, 55 baselined (MahaDBT pages: no dates published there, nothing applied), 3 unreadable (JavaScript-rendered, including Karnataka SSP and the Kotak home page), 3 failed: UP portal (TLS certificate chain not trusted by the JRE), West Bengal SVMCM (connect timeout), Cummins (bot challenge, not bypassed). Failures only update fetch bookkeeping.
* **Corporate dates come from Buddy4Study programme pages** (the providers' application partner, `trust=PARTNER`), not from each company's own site. Siemens is read from siemens.com (an explicit "closed" notice). Reliance, SBI Asha, ONGC, Cummins, Kotak and UP/ West Bengal/Karnataka official sites are JavaScript apps, blocked or unreachable, so they cannot be read directly.
* MahaDBT, NSP and AICTE publish no machine-readable per-scheme deadlines on the pages fetched, so those schemes stay UNKNOWN. This is the main reason most of the directory is not "verified open".
* Only the discovery filter's provider list is searched; this is not every Indian scholarship and no claim of full coverage is made. Discovery reads two of Buddy4Study's eleven child sitemaps (the only ones that list programme pages); if that site renumbers them, discovery finds fewer pages (visible as "found 0").
* Discovered programmes (8 on first run) are staged `PENDING_REVIEW`: eligibility numbers parsed from free text are untrusted, they are not used by `/recommend`, and approval is refused until state/course/category/gender are set.
* Change detection and date extraction on plain HTML are heuristic; B4S structured data is far more reliable but is a third party's representation of the provider's dates. Dates are re-read daily, so a wrong source date would be wrong here until the source is corrected or a reviewer verifies (`ADMIN` dates are never overwritten automatically).
* Programme tracks on a page that are not in the dataset (for example Tata's school-level track) are ignored for curated sources; the SBI Asha SC/ST relaxation and NAAC/NIRF institution rule cannot be modelled in the eligibility columns.
* The Playwright dependency exists but the Docker image has no browsers, so JavaScript-only pages are not rendered.

## Chatbot

* Retrieval is keyword matching unless `EMBEDDING_PROVIDER=openai-compatible` is configured. The semantic path is implemented and unit-tested against a local stub server but **has not been run against a real embedding provider** (none was available; Groq has no embeddings endpoint).
* The model can still paraphrase loosely or embellish. Mitigations: strict prompt (tested live: a first prompt let it add "(self & parents)" to a document list; the rule was tightened), link filtering, and a status/link footer written by code. There is no automatic claim verifier, so treat the wording as a helpful summary and the footer and the records as the facts.
* Profile extraction from free text is pattern-based ("3 lakh", "78%", state names, SC/ST in capitals). It can misread unusual phrasing; the response shows `understoodProfile` so the student can see what was understood.
* No streaming and no authentication on `/chat` and `/recommend`; both are rate-limited per client address (20 and 60 per minute).

## Security

* Admin/write endpoints are open unless `ADMIN_API_TOKEN` is set.
* **A Groq key found in `.env` is valid as of 2026-10-17.** If it is the one that was exposed earlier it has not been revoked; do that in the Groq console. A different literal key was found in `.idea/workspace.xml` and redacted.
* CORS allows only localhost origins; adjust `WebCorsConfig` for a real deployment. The database port is published to the host for local development.

## Testing

* Browser verification was limited to headless Edge screenshots and DOM inspection of the three pages; interactive flows (typing in the chat box, moving sliders) were verified at the API level, not by driving a browser.
* The live chatbot's model output varies between runs; automated tests mock the model.
