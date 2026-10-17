# Scholarship Dataset

`scholarships.csv` is the single source of truth. `ScholarshipDataLoader` upserts it into PostgreSQL at startup (idempotent, by normalised name; unchanged rows are skipped; missing rows are archived, not deleted) and re-indexes the keyword-vector `vector_store`.
`source_registry.csv` records provenance and open verification gaps.

## `scholarships.csv` (17 fields)

| Field | Format |
| :--- | :--- |
| `id` | Integer, stable |
| `name`, `provider` | Text |
| `course` | `;`-separated tokens from: `Engineering`, `Diploma`, `Medical`, `Pharmacy`, `Degree` |
| `year` | Years of study covered, e.g. `1`, `1-4` (informational) |
| `min_marks` | Minimum percentage; empty = none |
| `max_income` | Annual family income ceiling in INR; empty = none |
| `state` | `ALL` or `;`-separated domicile states |
| `category` | `ALL` or `;`-separated tokens from: `GEN`, `EWS`, `OBC`, `VJNT`, `SC`, `ST`, `MINORITY`, `PWD`, `ORPHAN` |
| `gender` | `ALL` or `Female` |
| `benefits` | Text |
| `deadline` | `YYYY-MM-DD`; empty = check portal |
| `docs` | `;`-separated list |
| `urls` | `application_url` or `application_url|source_url` |
| `open_date` | `YYYY-MM-DD`; empty = unknown |
| `deadline_verified` | `YYYY-MM-DD` the deadline/open date was checked against the official source; **empty = unverified, so status is UNKNOWN** |
| `academic_year` | e.g. `2026-27`; informational |

Deadlines and rates change every cycle. Confirm on the official portal before applying; open gaps are listed in `source_registry.csv`.

Docker/Render: the backend image is built from `backend/`, so it ships `backend/dataset/` (a mirror of this folder). After editing the files here, copy them to `backend/dataset/`.
