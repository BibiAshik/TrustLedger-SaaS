@echo off
setlocal

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    mvn %*
    exit /b %ERRORLEVEL%
)

echo Maven is not installed or not available on PATH.
echo Install Maven, or run this project with your IDE's bundled Maven.
exit /b 1
