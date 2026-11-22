function Buscar-Java([switch]$RequiereJdk) {
    $ErrorActionPreference = 'Continue'
    $candidatos = @()
    if ($env:JAVA_HOME) { $candidatos += Join-Path $env:JAVA_HOME 'bin/java.exe' }
    $candidatos += 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
    $candidatos += @(Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Source)
    foreach ($java in ($candidatos | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
        if ($RequiereJdk -and -not (Test-Path -LiteralPath (Join-Path (Split-Path $java) 'javac.exe'))) { continue }
        $version = & $java --version 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -and $version -match '(?m)^(?:openjdk|java) (\d+)' -and [int]$Matches[1] -ge 17) {
            return $java
        }
    }
    throw 'Instala Java 17 o posterior (JDK para compilar). Configura JAVA_HOME o agrega java al PATH.'
}
