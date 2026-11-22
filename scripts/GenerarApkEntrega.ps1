$ErrorActionPreference = 'Stop'
$proyecto = Split-Path $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:/Program Files/Android/Android Studio/jbr' }
$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$apksigner = Get-ChildItem -Path (Join-Path $androidSdk 'build-tools/*/apksigner.bat') -File |
    Sort-Object { [version]($_.Directory.Name -split '-')[0] } -Descending | Select-Object -First 1
if (-not $apksigner) { throw 'Instala Android SDK Build-Tools para verificar la firma.' }

$firma = Join-Path $env:LOCALAPPDATA 'InduztekSPA/FirmaParcial4'
New-Item -Path $firma -ItemType Directory -Force | Out-Null
$almacen = Join-Path $firma 'induztek-parcial4.jks'
$clave = Join-Path $firma 'almacen.dpapi'
$claveAlias = Join-Path $firma 'alias.dpapi'

function Guardar-Clave([string]$ruta) {
    $aleatorio = [byte[]]::new(24)
    $generador = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generador.GetBytes($aleatorio) } finally { $generador.Dispose() }
    ConvertTo-SecureString ([Convert]::ToBase64String($aleatorio)) -AsPlainText -Force |
        ConvertFrom-SecureString | Set-Content -LiteralPath $ruta -Encoding ASCII
}

$env:JAVA_HOME = $jdk
$env:INDUZTEK_KEYSTORE = $almacen
Push-Location $proyecto
try {
    if (-not (Test-Path -LiteralPath $almacen)) {
        Guardar-Clave $clave
        Guardar-Clave $claveAlias
    }
    $env:INDUZTEK_STORE_PASSWORD = [Net.NetworkCredential]::new('', (Get-Content -LiteralPath $clave | ConvertTo-SecureString)).Password
    $env:INDUZTEK_KEY_PASSWORD = [Net.NetworkCredential]::new('', (Get-Content -LiteralPath $claveAlias | ConvertTo-SecureString)).Password
    if (-not (Test-Path -LiteralPath $almacen)) {
        & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -keystore $almacen -storetype JKS -alias induztek-parcial4 `
            -storepass:env INDUZTEK_STORE_PASSWORD -keypass:env INDUZTEK_KEY_PASSWORD -keyalg RSA -keysize 2048 `
            -validity 10000 -dname 'CN=InduztekSPA Parcial 4, O=Entrega academica, C=CL'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la llave de la entrega.' }
    }
    & ./gradlew.bat :app:assembleEntrega --no-configuration-cache --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el APK de entrega.' }
    $apk = Join-Path $proyecto 'app/build/outputs/apk/entrega/app-entrega.apk'
    & $apksigner.FullName verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'La verificacion de la firma del APK fallo.' }
    Write-Output "APK $apk"
    Write-Output "Llave $almacen"
} finally {
    Pop-Location
    Remove-Item Env:INDUZTEK_KEYSTORE,Env:INDUZTEK_STORE_PASSWORD,Env:INDUZTEK_KEY_PASSWORD -ErrorAction SilentlyContinue
}
