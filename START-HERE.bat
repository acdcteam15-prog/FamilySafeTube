@echo off
setlocal
title FamilySafeTube - setup
cd /d "%~dp0"
echo.
echo  FamilySafeTube setup wizard. Keep this window open.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup\Setup-FamilySafeTube.ps1" -Mode menu
echo.
pause
