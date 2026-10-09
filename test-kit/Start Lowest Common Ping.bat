@echo off
title Lowest Common Ping (test build)
if exist "%~dp0start.ps1" goto start
echo.
echo The zip file is not extracted yet. Right-click the zip file, choose "Extract All...", then "Extract".
echo Then double-click "Start Lowest Common Ping" in the extracted folder.
echo.
pause
exit /b 1
:start
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
if not "%errorlevel%"=="0" pause
