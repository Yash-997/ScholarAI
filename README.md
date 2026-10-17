# SCHOLAR.AI

Scholarship finder for Indian students: a deterministic eligibility matcher, an evidence-grounded chatbot, and a directory of **26 curated schemes** plus programmes discovered automatically (staged for review) (NSP/central, MahaDBT and other state portals, corporate CSR) with an application status that is computed from verified dates only.

Stack: Java 21 / Spring Boot 3.3 + Spring AI, PostgreSQL 16 + pgvector, React 18 (Vite, Tailwind), Nginx, Docker Compose. Chat model: Groq (OpenAI-compatible API).

## Quick start (Docker)

```bash
cp .env.example .env        # then edit: GROQ_API_KEY (optional), ADMIN_API_TOKEN (recommended)
docker compose up -d --build
```

| URL | What |
| :--- | :--- |
| http://localhost:5173 | Web app (Nginx proxies `/api` to the backend) |
| http://localhost:8080/swagger-ui.html | API docs |
| `localhost:5433` | PostgreSQL (`postgres` / `local_dev_password` unless `POSTGRES_PASSWORD` is set) |

Data lives in the named volume `scholarship-rag_scholarship_pgdata_pg16`. `docker compose up/down/build` never deletes it; **do not run `docker compose down -v`** unless you intend to wipe the database.

Without `GROQ_API_KEY` everything still works: the chatbot answers from the records and says the AI model is not configured.

### Secrets

* `.env` is git-ignored. Never commit it, paste it into docs, or share it in screenshots/IDE files.
* **If a Groq key was ever exposed, revoke it at https://console.groq.com/keys and create a new one.** Code cannot revoke keys; this is a manual step.
* Groq retires models. The default is `openai/gpt-oss-120b`; override with `GROQ_MODEL` (list the models your key can use with `GET https://api.groq.com/openai/v1/models`).
* Set `ADMIN_API_TOKEN` on any server others can reach. Admin and write endpoints then require the header `X-Admin-Token`. When unset they are open (a warning is logged at startup).

## How it works

1. **Eligibility is deterministic** (`EligibilityEvaluator`): income <= ceiling, marks >= minimum (both inclusive), plus state / category / course / gender token matching. No model and no vector search can add or remove a scheme. Lowering marks can only remove schemes (covered by a regression test over the real dataset).
2. **Status is computed, never guessed** (`SchemeStatusCalculator`): `OPEN`, `CLOSED`, `UPCOMING`, `UNKNOWN` (no verified dates) or `UNVERIFIED` (record not verified). A deadline counts only when a verification date is recorded. A failed or empty fetch never closes a scheme.
3. **Chatbot**: evidence is PostgreSQL records plus the deterministic match result for the student's details. The model writes the wording; links it invents are stripped; the status/link/verified-date footer is added by code. If the model is missing, slow, errors, or returns junk, the answer is composed from the same records.
4. **Retrieval is keyword matching**, not semantic search (see `docs/RAG_PIPELINE.md`).
5. **Dynamic updates**: a daily job (03:30 IST) fetches the registered official/partner pages politely (robots.txt, rate limits, retries, no CAPTCHA/login bypass), discovers individual scheme pages, reads structured dates and explicit open/closed notices, and updates records with history and provenance. Conflicts, failures and anything ambiguous change nothing and flag the scheme. Closed schemes are hidden by default. See `docs/OPERATIONS.md`.

## Tests

```bash
docker run --rm -v "$PWD:/repo" -v scholar-m2:/root/.m2 -w /repo/backend maven:3.9.8-eclipse-temurin-21 mvn -B test
```

See `docs/TESTING.md`. Known limitations are listed in `docs/LIMITATIONS.md`; read it before relying on the data.

## Layout

```
backend/    Spring Boot service (controllers, services, entities, tests)
frontend/   React app (Eligibility Matcher, AI Chat, Explore)
dataset/    scholarships.csv (source of truth) and source_registry.csv (provenance)
docs/       Architecture, API, schema, operations, testing, limitations
```

Dataset facts (deadlines, income ceilings, rates) change every cycle. Always confirm on the official portal before applying.
