@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run-gradle.ps1" %*
exit /b %ERRORLEVEL%
