@echo off
chcp 65001 >nul
title Installation de l IA (technique)
rem Installation manuelle. Normalement inutile : le bureau installe l IA tout seul.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0preparer.ps1"
echo.
pause
