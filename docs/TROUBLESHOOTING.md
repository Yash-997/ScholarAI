# Enterprise Troubleshooting & Diagnostics Manual

## 1. Container Name Conflict ("Already in Use")

### Symptom:
```text
Error response from daemon: Conflict. The container name "/scholarship-pgvector" is already in use by container "cfc69ac2...".
```

### Root Cause:
An orphaned or stopped container with the same name was created by a previous run outside Docker Compose.

### Resolution:
Remove the stale container using Docker CLI:
```bash
docker rm -f scholarship-pgvector scholarship-backend scholarship-frontend
docker compose up -d
```

---

## 2. Port Conflicts (5432 vs 5433)

### Symptom:
Connection refused or:
```text
FATAL: password authentication failed for user "postgres"
```
Or native Windows PostgreSQL responds instead of the Docker pgvector container.

### Root Cause:
Local PostgreSQL installations on Windows bind to `5432`.

### Resolution:
Our Docker setup maps `5433:5432` on the host:
- Inside Docker Network: `jdbc:postgresql://db:5432/scholarship_db`
- From Host Tools (DBeaver/pgAdmin/Local App): `jdbc:postgresql://localhost:5433/scholarship_db`

To check port usage on Windows:
```powershell
Get-NetTCPConnection -LocalPort 5432, 5433 -ErrorAction SilentlyContinue | Select-Object LocalAddress, LocalPort, OwningProcess, State
```

---

## 3. Groq API Key Issues & Diagnosis

### Symptoms:
- Backend logs show: `401 Unauthorized` or `Invalid API Key` when invoking `/api/v1/scholarships/recommend`.
- Recommendations return fallback or generic template reasoning.

### Resolution:
1. Verify that your Groq API key is valid at [https://console.groq.com/keys](https://console.groq.com/keys).
2. Set the key in your terminal session or root `.env` file:
   ```bash
   # Windows PowerShell
   $env:GROQ_API_KEY="<your-groq-key>"
   docker compose up -d --build backend
   ```
3. Confirm the backend container received the variable:
   ```bash
   docker exec -it scholarship-backend sh -c "echo \$GROQ_API_KEY"
   ```

---

## 4. Frontend Reverse Proxy & CORS Issues

### Symptom:
- Network error in browser console: `Failed to fetch /api/v1/scholarships/recommend` or CORS policy error.

### Resolution:
1. **Docker Deployment**: The frontend container runs Nginx, which internally routes `/api/` to `http://backend:8080/api/`. Ensure both `frontend` and `backend` containers are on `scholarship-network`:
   ```bash
   docker compose ps
   ```
2. **Local Standalone Vite Dev**: If running `npm run dev` outside Docker, Vite proxies `/api` to `http://localhost:8080` via `vite.config.js`. If the proxy fails, `EligibilityView.jsx` includes an automatic fallback to direct `http://localhost:8080/api/v1/scholarships/recommend`.
3. The Spring Boot backend includes global CORS configuration in `WebCorsConfig.java` permitting requests from `http://localhost:5173`, `http://localhost:3000`, and `http://127.0.0.1:5173`.

---

## 5. Clean Rebuild & Volume Reset

When making deep schema changes or resetting the database to the initial seed dataset:

```bash
# 1. Stop all containers and delete volumes
docker compose down -v

# 2. Rebuild all images from scratch
docker compose up -d --build

# 3. Stream backend startup logs to confirm bootstrapping
docker compose logs -f backend
```
