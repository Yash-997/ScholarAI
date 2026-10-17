# Enterprise AI Scholarship Platform — Documentation Handbook

> **Note (2026-10-17):** parts of this page predate the current implementation. Where it conflicts with `README.md`, `docs/RAG_PIPELINE.md`, `docs/OPERATIONS.md` or `docs/LIMITATIONS.md`, those are correct. In particular: retrieval is keyword matching (not semantic), eligibility is evaluated in Java (`EligibilityEvaluator`), status is computed from verified dates, and the chat model is `openai/gpt-oss-120b` by default (`GROQ_MODEL`).

Welcome to the comprehensive technical documentation for the **RAG-Based AI Scholarship Recommendation & Multi-Agent Platform**.

This enterprise-grade application bridges Indian students with hundreds of central, state, and corporate CSR scholarships through a **deterministic-first, hybrid RAG pipeline** powered by Java 21, Spring Boot 3.3, Groq LLM (`openai/gpt-oss-120b`), PostgreSQL 16 with `pgvector`, and an ultra-modern React 18+ (Vite) frontend.

---

## 📑 Documentation Index

| Document | Purpose | Key Topics Covered |
| :--- | :--- | :--- |
| [**Architecture Overview**](ARCHITECTURE.md) | Enterprise System Architecture | Multi-container design, Spring Boot 3.3, Groq LLM integration, Spring AI, component boundaries |
| [**API Reference**](API_REFERENCE.md) | Complete REST API Specification | Swagger UI, endpoints, request/response DTOs, cURL commands, status codes |
| [**RAG Pipeline & AI Engine**](RAG_PIPELINE.md) | Deterministic-First RAG Pipeline | Two-stage retrieval, deterministic SQL guardrails, 768-dim embeddings, Groq grounded synthesis |
| [**Database Schema**](DATABASE_SCHEMA.md) | Data Architecture & PGVector | `scholarships`, `scholarship_chunks`, `vector_store`, `student_profiles`, HNSW cosine index |
| [**Frontend Guide**](FRONTEND_GUIDE.md) | Modern UI/UX Architecture | React 18, Vite, Tailwind CSS, Framer Motion, dynamic grid layout, automated hidden RAG triggers |
| [**Docker Deployment Guide**](DOCKER_DEPLOYMENT.md) | Container Orchestration | Multi-stage Dockerfiles, Docker Compose, network isolation, port mapping, environment variables |
| [**Dataset & Scheme Sources**](DATASET_SOURCES.md) | Pan-India & CSR Schemes | MahaDBT, NSP, SSP Karnataka, Digital Gujarat, UP Portal, Tata Trusts, Reliance, Aditya Birla |
| [**Code Flow & Sequences**](CODE_FLOW.md) | Request Lifecycle | Sequence diagrams for recommendation, ingestion, chat, and document verification |
| [**Testing & Quality Assurance**](TESTING.md) | Verification & Test Suite | Unit tests, Mockito integration tests, WebMvc tests, build verification |
| [**Troubleshooting Manual**](TROUBLESHOOTING.md) | Diagnostics & Solutions | Port conflicts (5432/5433), Groq API keys, pgvector extension, CORS, container health |

---

## ⚡ Quick Start: Running with Single Command

The entire system (PostgreSQL with pgvector, Spring Boot backend, and React/Vite Nginx frontend) runs via Docker Compose:

### 1. Configure Environment
Set your Groq API key in your environment or in an `.env` file in the project root:
```bash
# Windows PowerShell
$env:GROQ_API_KEY="your_groq_api_key_here"

# Linux / macOS
export GROQ_API_KEY="your_groq_api_key_here"
```

### 2. Launch the Application
```bash
docker compose up -d --build
```

### 3. Access Services
- **Student Web Portal**: [http://localhost:5173](http://localhost:5173)
- **Spring Boot Backend API**: [http://localhost:8080/api/v1/scholarships](http://localhost:8080/api/v1/scholarships)
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **PostgreSQL Database**: `localhost:5433` (Database: `scholarship_db`, User: `postgres`, Password: `postgrespassword`)

---

## 🎯 Core Architectural Principles

1. **Deterministic SQL Guardrails First**:
   - Vector similarity alone **never** grants eligibility.
   - If a student's family income exceeds the scholarship's cap (e.g. ₹2.5 LPA) or their percentage is below the threshold, the scheme is disqualified by PostgreSQL indexed queries **before** context reaches the LLM.
2. **Strict Privacy & Zero Clutter**:
   - Technical badges, backend framework names, and LLM model identifiers (`openai/gpt-oss-120b`) are completely shielded from the consumer UI.
3. **Automated Reactive RAG**:
   - Sliders and dropdowns in the frontend automatically debounce and trigger RAG evaluations behind the scenes using hidden standardized prompts without requiring manual user typing.
4. **Equal-Height Responsive Dashboard Grid**:
   - Schemes are rendered in a modern CSS grid (`grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3`) with uniform card heights and sticky action footers.
