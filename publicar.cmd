@echo off
rem Atalho para rodar o publicar.ps1 sem mexer na politica de execucao do Windows.
rem Uso: publicar.cmd -Resumo "minha mudanca" [-Versao 1.10] [-Simulacao]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publicar.ps1" %*
exit /b %ERRORLEVEL%
