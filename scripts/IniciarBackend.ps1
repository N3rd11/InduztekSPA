param([switch]$SinCompilar)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendPath = Join-Path $projectPath 'backend'
$javaPath = 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { $javaPath = Join-Path $env:JAVA_HOME 'bin/java.exe' }
if (-not (Test-Path -LiteralPath $javaPath)) { $javaPath = (Get-Command java -ErrorAction Stop).Source }
if (-not $SinCompilar) {
    Push-Location $projectPath
    try {
        $env:JAVA_HOME = Split-Path (Split-Path $javaPath)
        & ./gradlew.bat -p backend :catalog-service:bootJar :protocol-service:bootJar --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el backend' }
    } finally { Pop-Location }
}
$logsPath = Join-Path $backendPath 'logs'
New-Item -ItemType Directory -Path $logsPath -Force | Out-Null
foreach ($service in @(@{Nombre='catalog-service';Puerto=8081}, @{Nombre='protocol-service';Puerto=8082})) {
    $existente = Get-NetTCPConnection -LocalPort $service.Puerto -State Listen -ErrorAction SilentlyContinue
    if ($existente) { Write-Output ("Puerto {0} ya está ocupado; no se inicia otra instancia." -f $service.Puerto); continue }
    $jarPath = Join-Path $backendPath ($service.Nombre + '/build/libs/' + $service.Nombre + '-0.1.0.jar')
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "No existe $jarPath" }
    $marca = Get-Date -Format 'yyyyMMdd-HHmmss'
    $process = Start-Process -FilePath $javaPath -ArgumentList @('-jar',('"'+$jarPath+'"')) -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logsPath ($service.Nombre+'-'+$marca+'.log')) `
        -RedirectStandardError (Join-Path $logsPath ($service.Nombre+'-'+$marca+'-error.log'))
    Write-Output ($service.Nombre + ' iniciado. PID: ' + $process.Id + '; puerto: ' + $service.Puerto)
}
$adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (Test-Path -LiteralPath $adbPath) {
    & $adbPath reverse tcp:8081 tcp:8081
    & $adbPath reverse tcp:8082 tcp:8082
    Write-Output 'Túneles ADB configurados para las direcciones predeterminadas de la aplicación.'
}
Write-Output 'Los datos se conservan en backend/data. Los servicios siguen ejecutándose en segundo plano.'
