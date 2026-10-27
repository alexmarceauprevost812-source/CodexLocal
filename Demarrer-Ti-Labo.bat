@echo off
title Ti-Labo - bureau du laboratoire
cd /d "%~dp0"
chcp 65001 >nul

if exist "%~dp0outils\python\python.exe" (
  "%~dp0outils\python\python.exe" "%~dp0app\server.py"
  goto fin
)

rem Pas de Python sur la cle : on essaie celui du PC
where py >nul 2>nul
if not errorlevel 1 (
  py -3 "%~dp0app\server.py"
  goto fin
)
where python >nul 2>nul
if not errorlevel 1 (
  python "%~dp0app\server.py"
  goto fin
)
echo.
echo  Python est absent de la cle. Lance d'abord Installer-Ti-Labo.bat
echo.

:fin
pause
