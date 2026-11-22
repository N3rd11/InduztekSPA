param(
    [switch]$SinCompilar, [switch]$ConfigurarJefe, [switch]$Detener,
    [ValidateSet('Auto','H2','Laragon')][string]$Base = 'Auto',
    [string]$RutaLaragon = 'C:/laragon', [switch]$PedirClaveRoot,
    [ValidateRange(1024,65532)][int]$PuertoBase = 8081, [switch]$SinAdb
)
$ErrorActionPreference = 'Stop'
$proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $proyecto 'backend'
$servicios = @(
    @{Nombre='catalog-service';Puerto=$PuertoBase;Ruta='api/servicios/catalogos';Datos='catalogos'},
    @{Nombre='protocol-service';Puerto=($PuertoBase+1);Ruta='api/servicios/protocolos';Datos='protocolos'},
    @{Nombre='access-service';Puerto=($PuertoBase+2);Ruta='api/session';Datos='usuarios'},
    @{Nombre='report-service';Puerto=($PuertoBase+3);Ruta='api/servicios/actas'}
)
function Buscar-Servicio($servicio) {
    $esperado = [regex]::Escape($servicio.Jar.Replace('\','/'))
    $runtime = [regex]::Escape((Join-Path $backend 'runtime').Replace('\','/'))
    $archivo = [regex]::Escape((Split-Path $servicio.Jar -Leaf))
    $rutas = "(?:$esperado|$runtime/[a-f0-9]{64}/$archivo)"
    $listeners = Get-NetTCPConnection -LocalPort $servicio.Puerto -State Listen -ErrorAction SilentlyContinue
    foreach ($idProceso in ($listeners.OwningProcess | Sort-Object -Unique)) {
        $proceso = Get-CimInstance Win32_Process -Filter "ProcessId=$idProceso"
        if (-not $proceso) { continue }
        if ($proceso.Name -ne 'java.exe' -or ([string]$proceso.CommandLine).Replace('\','/') -notmatch "(?:^|\s)-jar\s+(?:\x22$rutas\x22|$rutas)(?=\s|$)") {
            throw "Puerto $($servicio.Puerto) ocupado por otro programa o copia del proyecto (PID $idProceso). Puedes indicar -PuertoBase 18081."
        }
        $proceso
    }
}
function Preparar-Jar($servicio) {
    # Gradle puede reemplazar build/libs durante una compilacion. La JVM usa una copia fija.
    $huella = (Get-FileHash -LiteralPath $servicio.Jar -Algorithm SHA256).Hash.ToLowerInvariant()
    $carpeta = Join-Path $backend "runtime/$huella"
    $destino = Join-Path $carpeta (Split-Path $servicio.Jar -Leaf)
    if (-not (Test-Path -LiteralPath $destino -PathType Leaf)) {
        New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
        $temporal = Join-Path $carpeta ([guid]::NewGuid().ToString('N') + '.tmp')
        try {
            Copy-Item -LiteralPath $servicio.Jar -Destination $temporal
            if ((Get-FileHash -LiteralPath $temporal -Algorithm SHA256).Hash.ToLowerInvariant() -ne $huella) {
                throw 'El JAR cambio durante la copia. Espera a que termine Gradle y vuelve a iniciar.'
            }
            try { [IO.File]::Move($temporal, $destino) }
            catch [IO.IOException] { if (-not (Test-Path -LiteralPath $destino -PathType Leaf)) { throw } }
        } finally {
            if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal }
        }
    }
    return $destino
}
function Cerrar-Servicio($instancia) {
    if ($instancia -and -not $instancia.HasExited) {
        Stop-Process -InputObject $instancia
        if (-not $instancia.WaitForExit(10000)) { throw "El proceso $($instancia.Id) sigue activo." }
    }
}

foreach ($servicio in $servicios) {
    $servicio.Jar = Join-Path $backend "$($servicio.Nombre)/build/libs/$($servicio.Nombre)-0.1.0.jar"
    $servicio.Activos = @(Buscar-Servicio $servicio)
}
if ($Detener) {
    foreach ($servicio in $servicios) {
        foreach ($proceso in $servicio.Activos) {
            Cerrar-Servicio (Get-Process -Id $proceso.ProcessId -ErrorAction SilentlyContinue)
            Write-Output "$($servicio.Nombre) detenido."
        }
    }
    return
}

$perfilLaragon = Join-Path $env:LOCALAPPDATA 'InduztekSPA/Laragon/conexion.xml'
$configurado = Test-Path -LiteralPath $perfilLaragon
$modoAnterior = if ($configurado) { 'Laragon' } else { 'H2' }
$modo = if ($Base -eq 'Auto') { $modoAnterior } else { $Base }
foreach ($servicio in $servicios | Where-Object Datos) {
    foreach ($proceso in $servicio.Activos) {
        $modoActivo = if ($proceso.CommandLine -match '-Dinduztek.arranque.base=(H2|Laragon)') { $Matches[1] } else { $modoAnterior }
        if ($modoActivo -ne $modo) {
            throw 'El backend ya usa otra base. Ejecuta DetenerMicroservicios.bat antes de cambiarla.'
        }
    }
}
. (Join-Path $PSScriptRoot 'BuscarJava.ps1')
$java = Buscar-Java -RequiereJdk:(-not $SinCompilar)
$env:JAVA_HOME = Split-Path (Split-Path $java)

if ($modo -eq 'Laragon') {
    if (-not (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue)) {
        $laragon = Join-Path $RutaLaragon 'laragon.exe'
        if (-not (Test-Path -LiteralPath $laragon -PathType Leaf)) {
            throw 'Instala Laragon con MySQL 8.4 o indica su carpeta con -RutaLaragon.'
        }
        if (-not (Get-Process laragon -ErrorAction SilentlyContinue)) {
            Start-Process -FilePath $laragon -WorkingDirectory $RutaLaragon
        }
        Write-Output 'Pulsa Start All en Laragon. Esperando a MySQL en 127.0.0.1:3306...'
        $limiteMySql = (Get-Date).AddSeconds(45)
        while (-not (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue)) {
            if ((Get-Date) -ge $limiteMySql) { throw 'MySQL de Laragon no respondio en el puerto 3306.' }
            Start-Sleep -Seconds 1
        }
    }
    if (-not $configurado) {
        & (Join-Path $PSScriptRoot 'ConfigurarLaragon.ps1') -RutaLaragon $RutaLaragon -PedirClaveRoot:$PedirClaveRoot
    }
    $credencial = Import-Clixml -LiteralPath $perfilLaragon
}
if (-not $SinCompilar) {
    & (Join-Path $proyecto 'gradlew.bat') -p $backend bootJar --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el backend.' }
}
foreach ($servicio in $servicios) {
    if (-not (Test-Path -LiteralPath $servicio.Jar -PathType Leaf)) {
        throw "Falta $($servicio.Jar). Ejecuta el BAT sin -SinCompilar."
    }
}
$logs = Join-Path $backend 'logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
if ($modo -eq 'H2') { New-Item -ItemType Directory -Path (Join-Path $backend 'data') -Force | Out-Null }
$usuarios = $servicios | Where-Object Nombre -eq 'access-service'
if ($ConfigurarJefe) {
    $correo = 'respinoza@induztek.ejemplo'
    Write-Output "Configura la cuenta $correo. La clave reemplaza la anterior para esta base."
    $clave = [Net.NetworkCredential]::new('', (Read-Host 'Nueva clave' -AsSecureString)).Password
    $confirmacion = [Net.NetworkCredential]::new('', (Read-Host 'Repite la clave' -AsSecureString)).Password
    if ([string]::IsNullOrWhiteSpace($clave) -or $clave.Length -lt 8 -or
        [Text.Encoding]::UTF8.GetByteCount($clave) -gt 72 -or $clave -cne $confirmacion) {
        throw 'Las claves deben coincidir. Usa al menos 8 caracteres y hasta 72 bytes.'
    }
    foreach ($proceso in @(Buscar-Servicio $usuarios)) {
        Cerrar-Servicio (Get-Process -Id $proceso.ProcessId -ErrorAction SilentlyContinue)
    }
}
$iniciados = @()
try {
    foreach ($servicio in $servicios) {
        if (Buscar-Servicio $servicio) {
            Write-Output "$($servicio.Nombre) ya esta activo en $($servicio.Puerto)."
            continue
        }
        $jarEjecucion = Preparar-Jar $servicio
        $argumentos = @("-Dinduztek.arranque.base=$modo", '-jar', ('"' + $jarEjecucion + '"'),
            "--server.port=$($servicio.Puerto)", '--server.address=127.0.0.1',
            "--induztek.catalog-url=http://127.0.0.1:$PuertoBase",
            "--induztek.protocol-url=http://127.0.0.1:$($PuertoBase+1)",
            "--induztek.access-url=http://127.0.0.1:$($PuertoBase+2)")
        $variables = @{}
        if ($servicio.Datos -and $modo -eq 'Laragon') {
            $variables.SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/induztek_$($servicio.Datos)?sslMode=REQUIRED"
            $variables.SPRING_DATASOURCE_USERNAME = $credencial.UserName
            $variables.SPRING_DATASOURCE_PASSWORD = $credencial.GetNetworkCredential().Password
        } elseif ($servicio.Datos -and $Base -eq 'H2') {
            $argumentos += "--spring.datasource.url=jdbc:h2:file:./data/$($servicio.Datos)"
            $argumentos += '--spring.datasource.username=sa','--spring.datasource.password='
        }
        if ($ConfigurarJefe -and $servicio.Nombre -eq 'access-service') { $variables.INDUZTEK_CLAVE_JEFE1 = $clave }
        $anteriores = @{}
        $marca = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        $prefijo = Join-Path $logs "$($servicio.Nombre)-$marca"
        try {
            foreach ($nombre in $variables.Keys) {
                $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre)
                [Environment]::SetEnvironmentVariable($nombre, $variables[$nombre])
            }
            $proceso = Start-Process -FilePath $java -ArgumentList $argumentos -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput "$prefijo.log" -RedirectStandardError "$prefijo-error.log"
            $iniciados += $proceso
            $servicio.Iniciado = $proceso
        } finally {
            foreach ($nombre in $anteriores.Keys) { [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre]) }
        }
    }

    $limite = (Get-Date).AddSeconds(60)
    foreach ($servicio in $servicios) {
        $url = "http://127.0.0.1:$($servicio.Puerto)/$($servicio.Ruta)"
        do {
            $listo = $false
            try {
                $listo = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200
            } catch {
                $respuesta = $_.Exception.Response
                $listo = $respuesta -and [int]$respuesta.StatusCode -eq 401 -and $respuesta.Headers['WWW-Authenticate'] -like 'Basic*'
            }
            if (-not $listo) { Start-Sleep -Milliseconds 400 }
        } until ($listo -or (Get-Date) -ge $limite -or ($servicio.Iniciado -and $servicio.Iniciado.HasExited))
        if (-not $listo -or -not (Buscar-Servicio $servicio)) { throw "No respondio $($servicio.Nombre). Revisa $logs." }
        Write-Output "$($servicio.Nombre) responde en $($servicio.Puerto)."
    }
    if ($ConfigurarJefe) {
        $basica = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($correo + ':' + $clave)))
        $perfil = Invoke-RestMethod -Uri "http://127.0.0.1:$($usuarios.Puerto)/api/session" -Headers @{Authorization="Basic $basica"} -TimeoutSec 5
        if ($perfil.id -ne 'jefe1' -or $perfil.rol -ne 'JEFE_TECNICO' -or -not $perfil.activo) {
            throw 'Usuarios devolvio un perfil distinto al jefe tecnico.'
        }
        Write-Output "Clave confirmada. Ingresa en la app con $correo."
    }
} catch {
    foreach ($proceso in $iniciados) { Cerrar-Servicio $proceso }
    throw
}

if (-not $SinAdb) {
    $rutasSdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))
    $adb = $rutasSdk | Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools/adb.exe' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    if (-not $adb) { $adb = (Get-Command adb.exe -ErrorAction SilentlyContinue).Source }
    if ($adb) {
        $dispositivos = @(& $adb devices | ForEach-Object { if ($_ -match '^(\S+)\s+device$') { $Matches[1] } })
        if ($env:ANDROID_SERIAL) { $dispositivos = @($dispositivos | Where-Object { $_ -eq $env:ANDROID_SERIAL }) }
        if ($dispositivos.Count -eq 1) {
            $tuneles = foreach ($puerto in 8081,8082,8083,8084) {
                & $adb -s $dispositivos[0] reverse "tcp:$puerto" "tcp:$($PuertoBase+$puerto-8081)" | Out-Null
                $LASTEXITCODE -eq 0
            }
            if ($false -in $tuneles) { Write-Warning 'Algunos tuneles ADB no se pudieron crear.' }
            else { Write-Output "Tuneles ADB configurados para $($dispositivos[0])." }
        } else {
            Write-Warning 'Conecta un dispositivo ADB o define ANDROID_SERIAL y vuelve a ejecutar el BAT con -SinCompilar.'
        }
    } else { Write-Output 'ADB es opcional. Instala Android SDK Platform-Tools para conectar el emulador o un telefono por USB.' }
}
$destino = if ($modo -eq 'Laragon') { 'MySQL de Laragon' } else { 'H2 en backend/data' }
Write-Output "Backend activo en segundo plano. Base de datos: $destino."
