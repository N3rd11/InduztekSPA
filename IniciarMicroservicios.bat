@echo off
setlocal
call "%~dp0scripts\IniciarBackend.cmd" %*
set "resultado=%ERRORLEVEL%"
echo.
if not "%resultado%"=="0" echo Revisa el error anterior y los archivos en backend\logs.
pause
exit /b %resultado%
