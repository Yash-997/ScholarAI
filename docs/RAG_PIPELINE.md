# Recommendation and Chat Pipeline

## Principle

Eligibility is a rules question, so rules answer it. Retrieval and the language model only order, word and explain results. Neither can add a scheme to, or remove one from, an eligibility result.

## Recommendations (`POST /api/v1/scholarships/recommend`)

1. **Eligibility** (`EligibilityService` -> `EligibilityEvaluator`, pure Java). Loads `VERIFIED` schemes and keeps those the student passes. A profile field left empty is not filtered on. A scheme column left empty or set to `ALL` / `All India` is unrestricted. All numeric boundaries are inclusive. Each match carries the list of criteria that were checked and passed (`matched_criteria`).
2. **Ordering**: keyword relevance from the vector store, restricted to the eligible ids, then a stable sort by status group (OPEN, UPCOMING, UNKNOWN, UNVERIFIED, CLOSED last). There is no result cap below 200, so the list never re-windows when a filter changes.
3. **Wording**: if the model is usable it writes one "why you match" sentence per scheme from the passed criteria; otherwise a deterministic sentence is used. Facts, status and matched criteria always come from the database and the rules.

## Chat (`POST /api/v1/scholarships/chat`)

Request: `message`, optional `history` (last 6 turns are used), optional `profile` (details from the Eligibility form).

1. Student details are read from the history, the optional `profile`, and the current message (`ChatProfileExtractor`). Unreadable details stay "not specified".
2. Intent is detected (recommend, documents, deadline, benefits, apply, eligibility, general).
3. Records are chosen: schemes named in the message; if none, schemes named in recent turns for short or pronoun follow-ups ("what documents do I need for it?"); for "which scholarships can I get?" the eligible schemes; otherwise keyword retrieval above a similarity threshold. If a scheme is named, only that scheme is used.
4. Each record is rendered as evidence including rules, benefits, documents, links, computed status, deadline (marked "reported, NOT verified" when unverified), last-verified date, and the deterministic match result, including "Cannot confirm" for details the student has not given.
5. `LlmGateway` calls Groq with a timeout (`LLM_TIMEOUT_SECONDS`, default 25) and never throws. The system prompt requires: use only the evidence, say "not on record / not verified" instead of guessing, cite `[S#]`, copy names and document lists exactly.
6. Output checks: URLs not in the evidence are replaced; unusable output (blank, tiny, raw JSON/error) is rejected. A status/link/last-verified footer is appended from the records.
7. Fallback (`mode: FALLBACK`, with `fallbackReason` NOT_CONFIGURED, TIMEOUT, ERROR, EMPTY or REJECTED) composes the answer from the same records and tells the student the AI model was not used. When no record matches (`NO_RECORDS`) it says so and points to NSP / MahaDBT; it does not call the model.

Response fields: `reply`, `sourceCitations` (names), `sources` (id, name, url, status, lastVerified), `mode`, `fallbackReason`, `retrievalMode` (always `keyword`), `understoodProfile`.

## Retrieval is keyword matching

`LexicalHashEmbeddingModel` builds 768-dimension hashed bag-of-words vectors (unigrams + bigrams, stop words removed, L2-normalised) stored in pgvector and searched by cosine distance. It matches shared words only: "girls" does not match "female" (a test documents this). It is deterministic and needs no network.

To use real embeddings, configure an embedding model that your provider offers (Groq currently does not serve text embeddings), replace the `@Primary` embedding bean, set `spring.ai.vectorstore.pgvector.dimensions` to the model's dimension, and let the startup loader rebuild the store (it clears and re-indexes `vector_store` on every start). Until then do not describe retrieval as semantic.

## Index hygiene

Chunks are replaced, not appended: `indexSingleScholarship` deletes the scheme's chunks first, deleting a scheme removes its chunks, and the loader rebuilds the store at startup (3 chunks per scheme: eligibility, benefits, documents/procedure).

## Configuration

```yaml
spring.ai.openai:
  api-key: ${GROQ_API_KEY:not-configured}
  base-url: https://api.groq.com/openai/v1
  chat.options.model: ${GROQ_MODEL:openai/gpt-oss-120b}
  chat.options.temperature: 0.2
```
