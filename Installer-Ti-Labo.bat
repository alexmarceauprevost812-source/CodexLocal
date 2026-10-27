@echo off
title Ti-Labo - installation sur la cle
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0outils\installer.ps1"
if errorlevel 1 (
  echo.
  echo  Un probleme est survenu. Verifie ta connexion Internet et relance.
)
echo.
pause
