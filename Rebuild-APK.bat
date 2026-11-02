@echo off
setlocal
title FamilySafeTube - rebuild APK
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup\Setup-FamilySafeTube.ps1" -Mode rebuild
echo.
pause
