# ==============================================================================
# Scholarship Recommendation Platform - One-Click PowerShell Startup Script
# ==============================================================================

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "   Starting AI Scholarship Platform (All Services) " -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# 1. Start pgvector Database
Write-Host "`n[1/3] Starting pgvector Database (Docker on port 5433)..." -ForegroundColor Yellow
$containerStatus = docker inspect -f '{{.State.Running}}' scholarship-pgvector 2>$null
if ($containerStatus -ne "true") {
    docker start scholarship-pgvector 2>$null
    if ($LASTEXITCODE -ne 0) {
        docker compose -f "$PSScriptRoot\docker-compose.yml" up -d
    }
}
Write-Host " [OK] PostgreSQL with pgvector is running on port 5433." -ForegroundColor Green

# 2. Start Java Spring Boot AI Backend
Write-Host "`n[2/3] Starting Spring Boot AI Backend (Port 8080)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\backend'; java -jar target\scholarship-backend-1.0.0.jar"
Write-Host " [OK] Backend server process launched in dedicated terminal window." -ForegroundColor Green

# 3. Start Vite Frontend
Write-Host "`n[3/3] Starting React Vite Frontend (Port 5173)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\frontend'; npm run dev"
Write-Host " [OK] Frontend dev server process launched in dedicated terminal window." -ForegroundColor Green

Write-Host "`n===================================================" -ForegroundColor Cyan
Write-Host "   All services are up and running!" -ForegroundColor Green
Write-Host "   Frontend  : http://localhost:5173" -ForegroundColor White
Write-Host "   Backend   : http://localhost:8080" -ForegroundColor White
Write-Host "   Swagger   : http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "===================================================" -ForegroundColor Cyan
