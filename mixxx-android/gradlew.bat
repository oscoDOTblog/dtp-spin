@echo off
set DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%gradlew.ps1" %*
