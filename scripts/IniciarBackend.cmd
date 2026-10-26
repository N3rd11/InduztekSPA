@echo off
setlocal
set "INDUZTEK_PROJECT=%~dp0.."
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Configura JAVA_HOME con un JDK 17 o superior.
  exit /b 1
)
if /I "%~1"=="--sin-compilar" goto iniciar
call "%INDUZTEK_PROJECT%\gradlew.bat" -p "%INDUZTEK_PROJECT%\backend" :catalog-service:bootJar :protocol-service:bootJar --console=plain
if errorlevel 1 exit /b 1
:iniciar
pushd "%INDUZTEK_PROJECT%\backend"
if not exist logs mkdir logs
netstat -ano | findstr /R /C:":8081 .*LISTENING" >nul
if errorlevel 1 (
  start "" /b "%JAVA_HOME%\bin\java.exe" -jar catalog-service\build\libs\catalog-service-0.1.0.jar >logs\catalog-service.log 2>logs\catalog-service-error.log
  echo Iniciando catalogos en 8081.
) else (
  echo Puerto 8081 ya ocupado; no se inicia otra instancia.
)
netstat -ano | findstr /R /C:":8082 .*LISTENING" >nul
if errorlevel 1 (
  start "" /b "%JAVA_HOME%\bin\java.exe" -jar protocol-service\build\libs\protocol-service-0.1.0.jar >logs\protocol-service.log 2>logs\protocol-service-error.log
  echo Iniciando protocolos en 8082.
) else (
  echo Puerto 8082 ya ocupado; no se inicia otra instancia.
)
popd
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
  "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" reverse tcp:8081 tcp:8081
  "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" reverse tcp:8082 tcp:8082
)
echo Datos conservados en backend\data. Logs en backend\logs.
endlocal
