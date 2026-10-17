# REST API Reference & Contract Specification

> **Note (2026-10-17):** parts of this page predate the current implementation. Where it conflicts with `README.md`, `docs/RAG_PIPELINE.md`, `docs/OPERATIONS.md` or `docs/LIMITATIONS.md`, those are correct. In particular: retrieval is keyword matching (not semantic), eligibility is evaluated in Java (`EligibilityEvaluator`), status is computed from verified dates, and the chat model is `openai/gpt-oss-120b` by default (`GROQ_MODEL`).

## Base URLs
- **Web App API Proxy (Frontend Port 5173)**: `http://localhost:5173/api/v1`
- **Direct Backend API (Spring Boot Port 8080)**: `http://localhost:8080/api/v1`
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 1. Multimodal AI Scholarship Recommendation

### `POST /api/v1/scholarships/recommend`
Evaluates student eligibility using deterministic rules and semantic vector matching, then uses the Groq LLM to generate reasoned recommendations. Accepts optional document file uploads (income certificate, caste certificate, marksheet).

- **Content-Type**: `multipart/form-data` or `application/x-www-form-urlencoded`
- **Security**: Public / Student Session

#### Request Parameters:
| Field | Type | Required | Description | Example |
| :--- | :--- | :--- | :--- | :--- |
| `query` | `string` | No* | Hidden default or custom question | `"Evaluate eligibility based strictly on profile"` |
| `annualFamilyIncome` | `number` | No | Upper gross family income in INR | `250000` |
| `currentCgpaOrPercentage` | `number` | No | Percentage or 10-point CGPA | `82.5` |
| `stateOfDomicile` | `string` | No | Student's home state | `"Maharashtra"` |
| `collegeState` | `string` | No | State where institute is located | `"Maharashtra"` |
| `socialCategory` | `string` | No | `General`, `OBC`, `SC`, `ST`, `EWS`, `VJNT` | `"OBC"` |
| `gender` | `string` | No | `All`, `Female`, `Male` | `"Female"` |
| `course` | `string` | No | Enrolled course / degree | `"B.Tech / Engineering"` |
| `documentImage` | `file` | No | `.pdf`, `.png`, `.jpg`, `.jpeg` | `income_cert.jpg` |
| `studentId` | `integer` | No | Optional student profile ID | `101` |

*\*Note: If `query` is omitted, the system automatically uses the default evaluation prompt.*

#### cURL Example:
```bash
curl -X POST "http://localhost:8080/api/v1/scholarships/recommend" \
  -F "query=Evaluate eligibility based strictly on the provided profile parameters and detail the financial benefits." \
  -F "annualFamilyIncome=200000" \
  -F "currentCgpaOrPercentage=82.5" \
  -F "stateOfDomicile=Maharashtra" \
  -F "collegeState=Maharashtra" \
  -F "socialCategory=OBC" \
  -F "gender=All" \
  -F "course=B.Tech / Engineering"
```

#### Response Example (`200 OK`):
```json
{
  "student_summary": "OBC engineering student from Maharashtra with family income ₹2,00,000 and 82.5% marks.",
  "document_verification_notes": null,
  "total_eligible_found": 8,
  "general_guidance": "Ensure your Tahsildar income certificate is issued for current fiscal year.",
  "generated_at": "2026-10-17T11:43:57.274Z",
  "recommendations": [
    {
      "scholarship_id": 1,
      "scholarship_name": "Rajarshi Chhatrapati Shahu Maharaj Shikshan Shulkh Shishyavrutti Yojna (EBC)",
      "provider": "Directorate of Technical Education (DTE) Maharashtra",
      "benefits": "50% tuition fees reimbursement and 50% exam fees reimbursement in approved colleges.",
      "deadline": "2025-03-31",
      "maximum_family_income": 800000.00,
      "minimum_marks_or_cgpa": null,
      "application_url": "https://mahadbt.maharashtra.gov.in/",
      "source_url": "https://mahadbt.maharashtra.gov.in/SchemeData/SchemeData?str=E9DDFA370C3C3E0DE190FA9B3BD48EE5",
      "eligibility_reason": "Family income of ₹2.00 LPA is well below the ₹8.00 LPA ceiling, admitted to B.Tech under CAP in Maharashtra.",
      "required_documents": [
        "Domicile Certificate of Maharashtra",
        "Tahsildar Income Certificate (<= 8 Lakh)",
        "CAP Allotment Letter",
        "Class 12 / Diploma Marksheet",
        "Aadhaar Card"
      ],
      "verification_status": "VERIFIED"
    }
  ]
}
```

---

## 2. Direct RAG Recommendation API

### `POST /api/v1/recommendations`
Consumes structured JSON containing either a free-text prompt or a structured student profile.

- **Content-Type**: `application/json`

#### Request Body Schema:
```json
{
  "query": "I am a female student in Pune studying Computer Engineering with 88% marks and 4 Lakh income. Are there corporate or state scholarships for me?",
  "topK": 5
}
```
*Or structured profile input:*
```json
{
  "student": {
    "course": "B.Tech Computer Science",
    "state": "Maharashtra",
    "annualFamilyIncome": 400000,
    "yearOfStudy": 2,
    "category": "General"
  },
  "question": "Which corporate scholarships cover private college tuition fees?",
  "topK": 5
}
```

#### cURL Example:
```bash
curl -X POST "http://localhost:8080/api/v1/recommendations" \
  -H "Content-Type: application/json" \
  -d '{"query": "B.Tech scholarships for Maharashtra students under 2.5 LPA"}'
```

---

## 3. Conversational AI Advisor API

### `POST /api/v1/scholarships/chat`
Enables multi-turn conversational advisory for students with context memory and scheme grounding.

- **Content-Type**: `application/json`

#### Request Schema:
```json
{
  "message": "What documents do I need if I want to apply for the Tata Trust Scholarship?",
  "sessionId": "session-student-4821",
  "state": "Maharashtra"
}
```

#### Response Example (`200 OK`):
```json
{
  "reply": "For the Tata Trusts Women in Tech / Merit Scholarship, you will need:\n1. Proof of admission to an accredited B.Tech program\n2. Previous year academic transcripts (min. 75% or 7.5 CGPA)\n3. Family income certificate (Form 16 or Tahsildar certificate)\n4. Bank account details (cancelled cheque / passbook)\n5. Statement of purpose and fee receipt.",
  "relevant_scholarships": [
    {
      "id": 14,
      "name": "Tata Trusts Medical and Healthcare / Engineering Merit Scholarship",
      "provider": "Tata Trusts Mumbai"
    }
  ],
  "sessionId": "session-student-4821"
}
```

---

## 4. Scholarship Catalog & Query APIs

### `GET /api/v1/scholarships`
Retrieves a paginated list of all verified scholarships across India, supporting multi-criteria filtering.

#### Query Parameters:
| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `page` | `integer` | `0` | Zero-based page index |
| `size` | `integer` | `10` | Records per page |
| `state` | `string` | `null` | Filter by state (e.g. `Maharashtra`, `Karnataka`, `All India`) |
| `category` | `string` | `null` | Filter by category (e.g. `OBC`, `SC`, `ST`, `Open`) |
| `course` | `string` | `null` | Filter by course (e.g. `B.Tech`, `Diploma`) |
| `search` | `string` | `null` | Keyword search in title, benefits, or provider |

#### cURL Example:
```bash
curl -X GET "http://localhost:8080/api/v1/scholarships?page=0&size=5&state=Maharashtra"
```

### `GET /api/v1/scholarships/{id}`
Retrieves complete details of a specific scholarship by ID.

#### cURL Example:
```bash
curl -X GET "http://localhost:8080/api/v1/scholarships/1"
```

---

## 5. Ingestion & Data Management APIs

### `POST /api/v1/scholarships/ingest`
Ingests a single scholarship or batch of scholarships in JSON format. Automatically extracts deterministic rules (`maximum_family_income`, `minimum_marks_or_cgpa`), splits descriptive text into three semantic chunks, and creates vector embeddings in `vector_store`.

- **Content-Type**: `application/json`

#### Request Schema:
```json
{
  "name": "Dr. Panjabrao Deshmukh Vasatigruh Nirvah Bhatta Yojna",
  "provider": "Directorate of Technical Education (DTE) Maharashtra",
  "schemeType": "State Government",
  "eligibility": "Domicile of Maharashtra, CAP admitted in professional technical course, living in hostel.",
  "maximumFamilyIncome": 800000,
  "minimumMarksOrCgpa": 50.0,
  "course": "B.E. / B.Tech / Undergraduate Engineering",
  "state": "Maharashtra",
  "categoryCriteria": "General / Open / EWS / SEBC",
  "benefits": "Hostel allowance up to Rs. 30,000 per year for kids of marginal farmers.",
  "deadline": "2025-03-31",
  "requiredDocuments": "Domicile Certificate, Tahsildar Income Certificate, Rent Agreement",
  "applicationUrl": "https://mahadbt.maharashtra.gov.in/",
  "sourceUrl": "https://mahadbt.maharashtra.gov.in/SchemeData/SchemeData?str=E9DDFA370C3C3E0DC47D93EE5B4C26CA"
}
```

#### cURL Example:
```bash
curl -X POST "http://localhost:8080/api/v1/scholarships/ingest" \
  -H "Content-Type: application/json" \
  -d @new_scheme.json
```


## Changes and additions (2026-10-17)

* `POST /api/v1/scholarships/recommend` items now also carry `status` (OPEN, CLOSED, UPCOMING, UNKNOWN, UNVERIFIED), `status_note`, `deadline_verified_on`, `source_url`, `last_verified`, `needs_review`, `matched_criteria`. `deadline` is informational; rely on `status`. Closed schemes are included, sorted last.
* `GET /api/v1/scholarships` and `/{id}` return the same status fields plus `open_date`, `academic_year`, `last_checked`, `review_reason`, `source_id`. The list only includes VERIFIED records.
* `POST /api/v1/scholarships/chat` accepts an optional `profile` and returns `sources`, `mode` (LLM|FALLBACK), `fallbackReason`, `retrievalMode`, `understoodProfile` in addition to `reply` and `sourceCitations`.
* `POST /api/v1/scholarships/ingest` is idempotent (matched by normalised name) and stores deadlines as unverified.
* New admin endpoints (see `docs/OPERATIONS.md`): `GET /api/v1/admin/sources`, `POST /api/v1/admin/sources/check`, `GET /api/v1/admin/scholarships/review`, `GET /api/v1/admin/scholarships/{id}/deadline-history`, `POST /api/v1/admin/scholarships/{id}/verified-dates`, `POST /api/v1/admin/scholarships/{id}/review/clear`.
* When `ADMIN_API_TOKEN` is set, `/api/v1/admin/**` and every non-GET call under `/api/v1/scholarships` except `/recommend` and `/chat` need header `X-Admin-Token`.
