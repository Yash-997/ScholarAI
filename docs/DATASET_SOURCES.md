# Pan-India & CSR Scholarship Scheme Directory

> **Note (2026-10-17):** parts of this page predate the current implementation. Where it conflicts with `README.md`, `docs/RAG_PIPELINE.md`, `docs/OPERATIONS.md` or `docs/LIMITATIONS.md`, those are correct. In particular: retrieval is keyword matching (not semantic), eligibility is evaluated in Java (`EligibilityEvaluator`), status is computed from verified dates, and the chat model is `openai/gpt-oss-120b` by default (`GROQ_MODEL`).

## 1. Overview and Provenance Policy

The scholarship knowledge base integrates official schemes from Central Government portals, Indian State Government scholarship portals, and premier Corporate Social Responsibility (CSR) foundation trusts.

### Strict Integrity Policy
- **Zero Fabrication**: Every scheme is verified against official government gazettes, department portals, or CSR foundation annual notifications.
- **Direct Portal Links**: All schemes provide direct links to the official registration portals (e.g., MahaDBT, NSP, SSP Karnataka) rather than third-party aggregators.
- **Continuous Audit**: Records contain a `last_verified` timestamp and explicit `verification_status` (`VERIFIED`, `ACTIVE`, `PENDING_REVIEW`).

---

## 2. Central Government Portals & Schemes

### National Scholarship Portal (NSP)
- **Official Portal**: [https://scholarships.gov.in/](https://scholarships.gov.in/)
- **Administering Authority**: Ministry of Electronics and Information Technology (MeitY) & Ministry of Education, Govt. of India.
- **Key Central Sector Schemes**:
  1. **Central Sector Scheme of Scholarships for College and University Students (PM-USP)**:
     - *Income Limit*: ₹4,50,000 / year
     - *Benefits*: ₹12,000 / year for graduation, ₹20,000 / year for post-graduation
     - *Criteria*: 80th percentile in relevant stream in Class 12 board examination.
  2. **Post Matric Scholarship Schemes for Minorities (MoMA)**:
     - *Income Limit*: ₹2,00,000 / year
     - *Benefits*: Full admission/tuition fee reimbursement plus monthly maintenance allowance.

### AICTE Portals & Schemes
- **Official Portal**: [https://www.aicte-india.org/](https://www.aicte-india.org/)
- **Key National Schemes**:
  1. **AICTE Pragati Scholarship for Girl Students**:
     - *Target*: Female students admitted to 1st year of technical degree or diploma programs in AICTE-approved institutions.
     - *Income Limit*: ₹8,00,000 / year
     - *Benefits*: ₹50,000 per annum for tuition, books, equipment, and laptop.
  2. **AICTE Saksham Scholarship for Specially-Abled Students**:
     - *Target*: Specially-abled students (disability >= 40%) pursuing technical education.
     - *Benefits*: ₹50,000 per annum.
  3. **AICTE Swanath Scholarship Scheme**:
     - *Target*: Orphans, wards of parents who died due to COVID-19, or wards of Armed Forces/Central Paramilitary personnel martyred in action.
     - *Benefits*: ₹50,000 per annum.

---

## 3. State Government Scholarship Portals

### 1. Maharashtra (MahaDBT Portal)
- **Official Portal**: [https://mahadbt.maharashtra.gov.in/](https://mahadbt.maharashtra.gov.in/)
- **Core State Schemes**:
  - *Rajarshi Chhatrapati Shahu Maharaj Shikshan Shulkh Shishyavrutti Yojna (EBC)*: 50% tuition & exam fee waiver for General/EWS students (< ₹8.0 LPA).
  - *Post Matric Scholarship for OBC / VJNT / SBC Students*: 50% to 100% tuition fee waiver based on category.
  - *Dr. Panjabrao Deshmukh Vasatigruh Nirvah Bhatta Yojna*: Hostel maintenance stipend up to ₹30,000 per year for children of marginal farmers.
  - *Government of India Post-Matric Scholarship for SC Students*: 100% tuition and exam fee waiver + monthly stipend.

### 2. Karnataka (State Scholarship Portal - SSP)
- **Official Portal**: [https://ssp.postmatric.karnataka.gov.in/](https://ssp.postmatric.karnataka.gov.in/)
- **Administering Authority**: Department of Social Welfare, Backward Classes Welfare, and Higher Education Karnataka.
- **Core State Schemes**:
  - *Karnataka Post-Matric Scholarship for SC/ST/OBC*: Full tuition fee waiver for eligible students studying in Karnataka.
  - *Vidyasiri / Food & Accommodation Scheme*: Monthly allowance for students residing in hostels or paying guest accommodation.

### 3. Uttar Pradesh (UP Scholarship & Fee Reimbursement)
- **Official Portal**: [https://scholarship.up.gov.in/](https://scholarship.up.gov.in/)
- **Core State Schemes**:
  - *UP Postmatric Inter & Dashmottar Scholarship*: Complete tuition fee reimbursement for SC, ST, OBC, General, and Minority students enrolled in universities and polytechnics.

### 4. Gujarat (Digital Gujarat / MYSY)
- **Official Portal**: [https://mysy.guj.nic.in/](https://mysy.guj.nic.in/) and [https://www.digitalgujarat.gov.in/](https://www.digitalgujarat.gov.in/)
- **Core State Schemes**:
  - *Mukhyamantri Yuva Swavalamban Yojana (MYSY)*: 50% tuition fee grant (up to ₹50,000 per year for engineering) for students scoring >= 80 percentile with family income <= ₹6.0 LPA.

### 5. Bihar (Post Matric Scholarship Portal - PMS Online)
- **Official Portal**: [https://pmsonline.bih.nic.in/](https://pmsonline.bih.nic.in/)
- **Core State Schemes**:
  - *Bihar PMS for BC, EBC, SC, and ST Students*: Direct financial assistance covering tuition, lab fees, and exam fees.

---

## 4. Top Corporate CSR Foundation Scholarships

### 1. Tata Trusts Scholarships
- **Official Portal**: [https://www.tatatrusts.org/](https://www.tatatrusts.org/)
- **Programs**: *Tata Trusts Medical & Healthcare Scholarship*, *Tata Trusts Engineering Scholarship*.
- **Coverage**: Merit-cum-need tuition fee assistance for professional undergraduate students.

### 2. Reliance Foundation Undergraduate Scholarships
- **Official Portal**: [https://www.reliancefoundation.org/](https://www.reliancefoundation.org/)
- **Coverage**: Up to ₹2,00,000 over the duration of the degree for students with family income <= ₹15 LPA (priority to <= ₹2.5 LPA) across all streams of undergraduate study.

### 3. Aditya Birla Capital Foundation Scholarship
- **Official Portal**: [https://www.adityabirlacapital.com/](https://www.adityabirlacapital.com/)
- **Coverage**: Educational grant of up to ₹60,000 for undergraduate general and professional course students.

### 4. HDFC Badhte Kadam Scholarship
- **Official Portal**: [https://www.hdfcbank.com/](https://www.hdfcbank.com/)
- **Coverage**: Financial support up to ₹1,00,000 for students who have suffered personal or family financial crises, with focus on girl students and students with disabilities.

### 5. Infosys Foundation Scholarship (Aarohan)
- **Official Portal**: [https://www.infosys.org/infosys-foundation.html](https://www.infosys.org/infosys-foundation.html)
- **Coverage**: Full tuition assistance and mentorship for economically vulnerable students studying STEM courses.
