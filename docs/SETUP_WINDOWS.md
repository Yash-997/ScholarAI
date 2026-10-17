# Windows Setup & Execution Guide

This document provides exact, copy-pasteable commands for Windows Command Prompt (`cmd.exe`) and PowerShell to run the 100% unified Java Spring Boot backend with PostgreSQL pgvector.

---

## 1. Prerequisites Verification

### Windows PowerShell / CMD
```powershell
# 1. Verify Docker (Docker Desktop must be running)
docker ps

# 2. Verify Java (Requires JDK 21 or later)
java -version

# 3. Optional: Set your Groq API Key (optional)
$env:GROQ_API_KEY="your-groq-key"
```

---

## 2. Start PostgreSQL + pgvector Docker Container

Start the pgvector database container on port `5433`:

```powershell
cd E:\scholarship-rag
docker compose up -d
```

Verify the container is healthy:
```powershell
docker ps --filter "name=scholarship-pgvector"
```

> **Port Mapping Note**: Host port `5433` maps to container port `5432`. This avoids conflicts with any native PostgreSQL instances that may exist on default port 5432.

---

## 3. Build & Run the Java Spring Boot Backend

### A. Run via Maven Wrapper
```powershell
cd E:\scholarship-rag\backend
.\mvnw.cmd spring-boot:run
```

### B. Or Package and Run Executable JAR
```powershell
cd E:\scholarship-rag\backend
# Build executable fat JAR
.\mvnw.cmd package -DskipTests

# Run the backend
java -jar target\scholarship-backend-1.0.0.jar
```

On startup, the `ScholarshipDataLoader` automatically checks the database and indexes all scholarships into the `vector_store` table if they are not already indexed.

---

## 4. Access the Application

Once running, access the headless interactive Swagger UI:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 5. Quick Verification with cURL

### Test 1: Ingest a New Scholarship Scheme
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/scholarships/ingest" `
  -H "Content-Type: application/json" `
  -d '{
    "scholarshipName": "AICTE Pragati Scholarship for Girls",
    "provider": "AICTE",
    "targetCourse": "B.Tech",
    "state": "All India",
    "maximumFamilyIncome": 800000.00,
    "minimumMarksOrCgpa": 60.0,
    "benefits": "Rs. 50,000 per annum"
  }'
```

### Test 2: Multimodal Recommendation Request
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/scholarships/recommend" `
  -F "query=I need financial help for B.Tech in Maharashtra" `
  -F "annualFamilyIncome=200000" `
  -F "currentCgpaOrPercentage=82.5" `
  -F "stateOfDomicile=Maharashtra" `
  -F "socialCategory=OBC"
```
