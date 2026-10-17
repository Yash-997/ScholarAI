@echo off
title Scholarship Recommendation System - Startup
echo ===================================================
echo   Starting AI Scholarship Platform (All Services)
echo ===================================================

echo [1/3] Starting pgvector Database (Docker)...
docker start scholarship-pgvector >nul 2>&1
if errorlevel 1 (
    echo Starting container via docker compose...
    docker compose up -d
)
echo Database started on port 5433.

echo.
echo [2/3] Starting Spring Boot AI Backend (Port 8080)...
start "Scholarship Backend (Spring AI + Groq)" cmd /k "cd /d %~dp0backend && java -jar target\scholarship-backend-1.0.0.jar"

echo.
echo [3/3] Starting React Vite Frontend (Port 5173)...
start "Scholarship Frontend (React + Vite)" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo ===================================================
echo   All services have been launched!
echo   Frontend : http://localhost:5173
echo   Backend  : http://localhost:8080
echo   Swagger  : http://localhost:8080/swagger-ui.html
echo ===================================================
pause
