@echo off
echo ============================================
echo  Indian Traffic Simulation Engine
echo  Starting Backend + Frontend...
echo ============================================

:: Start backend in a new window
start "Backend - Spring Boot" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"

:: Wait for backend to start
echo Waiting for backend to start...
timeout /t 8 /nobreak > nul

:: Start frontend in a new window
start "Frontend - Next.js" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:3000
echo.
echo Close both windows to stop the application.
pause
