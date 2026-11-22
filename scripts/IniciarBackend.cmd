@echo off
setlocal
if /I "%~1"=="--sin-compilar" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0IniciarBackend.ps1" -SinCompilar %2 %3 %4 %5 %6 %7 %8 %9
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0IniciarBackend.ps1" %*
)
exit /b %ERRORLEVEL%
