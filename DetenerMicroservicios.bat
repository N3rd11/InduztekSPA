@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\IniciarBackend.ps1" -Detener %*
set "resultado=%ERRORLEVEL%"
echo.
pause
exit /b %resultado%
