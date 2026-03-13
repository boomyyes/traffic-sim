@echo off
title Indian Traffic Simulation Engine
echo ============================================
echo   Indian Traffic Simulation Engine
echo ============================================
echo.
echo Starting application...
echo.
mvn javafx:run -f "%~dp0pom.xml"
if errorlevel 1 (
    echo.
    echo [!] Failed to start. Make sure Maven and Java 21+ are installed.
    echo     You can also try: mvn spring-boot:run
)
pause
