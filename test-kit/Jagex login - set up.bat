@echo off
title Jagex login - set up
if exist "%~dp0jagex-login.ps1" goto start
echo.
echo The zip file is not extracted yet. Right-click the zip file, choose "Extract All...", then "Extract".
echo Then double-click "Jagex login - set up" in the extracted folder.
echo.
pause
exit /b 1
:start
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0jagex-login.ps1" -Mode setup
pause
