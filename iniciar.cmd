@echo off
rem Inicia API + front-end e abre o navegador (detalhes em iniciar.ps1).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar.ps1"
if errorlevel 1 pause
