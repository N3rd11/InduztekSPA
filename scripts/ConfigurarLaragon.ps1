param([string]$RutaLaragon = 'C:/laragon', [switch]$PedirClaveRoot)
$ErrorActionPreference = 'Stop'
$proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$origen = Join-Path $proyecto 'backend/data'
$carpeta = Join-Path $env:LOCALAPPDATA 'InduztekSPA/Laragon'
$perfil = Join-Path $carpeta 'conexion.xml'
$preparacion = Join-Path $carpeta 'conexion-pendiente.xml'
if (Test-Path -LiteralPath $perfil) {
    Write-Output 'La conexion a Laragon ya esta configurada.'
    return
}
$mysql = Get-ChildItem -Path (Join-Path $RutaLaragon 'bin/mysql/*/bin/mysql.exe') -File |
    Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $mysql) { throw 'Falta el cliente de MySQL en Laragon.' }
$archivos = @('usuarios.mv.db','catalogos.mv.db','protocolos.mv.db' | ForEach-Object {
    Join-Path $origen $_
} | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
$rutaBackend = (Join-Path $proyecto 'backend').Replace('\','/') + '/'
$servicios = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $comando = ([string]$_.CommandLine).Replace('\','/')
    $comando.Contains($rutaBackend) -and $comando -match '-jar\s+.+-service-0\.1\.0\.jar'
}
if ($archivos.Count -gt 0 -and $servicios) {
    throw 'Deten los microservicios antes de copiar las bases H2.'
}
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
if (-not (Test-Path -LiteralPath $preparacion)) {
    $bytes = New-Object byte[] 24
    $generador = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generador.GetBytes($bytes) } finally { $generador.Dispose() }
    $clave = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    [pscredential]::new('induztek_app', (ConvertTo-SecureString $clave -AsPlainText -Force)) |
        Export-Clixml -LiteralPath $preparacion
}
$credencial = Import-Clixml -LiteralPath $preparacion
$clave = $credencial.GetNetworkCredential().Password
$anteriorRoot = $env:MYSQL_PWD
if ($PedirClaveRoot) {
    $env:MYSQL_PWD = [Net.NetworkCredential]::new('', (Read-Host 'Clave root de Laragon' -AsSecureString)).Password
}
try {
    @"
CREATE DATABASE IF NOT EXISTS induztek_usuarios CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS induztek_catalogos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS induztek_protocolos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE USER IF NOT EXISTS 'induztek_app'@'localhost' IDENTIFIED BY '$clave' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES ON induztek_usuarios.* TO 'induztek_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES ON induztek_catalogos.* TO 'induztek_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES ON induztek_protocolos.* TO 'induztek_app'@'localhost';
"@ | & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --ssl-mode=REQUIRED --connect-timeout=5
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar los esquemas de Laragon.' }
    $env:MYSQL_PWD = $clave
    & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 --user=$($credencial.UserName) --ssl-mode=REQUIRED --connect-timeout=5 --database=induztek_usuarios --execute='SELECT 1' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo validar el usuario MySQL de Induztek.' }
} finally {
    $env:MYSQL_PWD = $anteriorRoot
}
if ($archivos.Count -eq 0) {
    Move-Item -LiteralPath $preparacion -Destination $perfil
    Write-Output 'Laragon preparado para una instalacion nueva.'
    return
}
$respaldo = Join-Path $carpeta ('respaldo-h2-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $respaldo | Out-Null
foreach ($archivo in $archivos) {
    Copy-Item -LiteralPath $archivo -Destination $respaldo
}
. (Join-Path $PSScriptRoot 'BuscarJava.ps1')
$env:JAVA_HOME = Split-Path (Split-Path (Buscar-Java -RequiereJdk))
$env:INDUZTEK_MYSQL_USUARIO = $credencial.UserName
$env:INDUZTEK_MYSQL_CLAVE = $clave
$env:INDUZTEK_H2_ORIGEN = $respaldo
Push-Location $proyecto
try {
    & ./gradlew.bat -p backend :common:migrarLaragon --no-configuration-cache --console=plain --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'La migracion a Laragon no se completo.' }
    Move-Item -LiteralPath $preparacion -Destination $perfil
} finally {
    Pop-Location
    Remove-Item Env:INDUZTEK_MYSQL_USUARIO,Env:INDUZTEK_MYSQL_CLAVE,Env:INDUZTEK_H2_ORIGEN -ErrorAction SilentlyContinue
}
Write-Output "Conexion a Laragon configurada. Respaldo H2 en $respaldo"
