param(
    [string]$Reporte = (Join-Path (Split-Path $PSScriptRoot) 'app/build/reports/coverage/test/validacion/report.xml')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Reporte)) {
    throw 'Genera el reporte con :app:createValidacionUnitTestCoverageReport.'
}

[xml]$jacoco = Get-Content -LiteralPath $Reporte -Raw
$cubiertas = 0
$omitidas = 0
$paquetesLogicos = @('data', 'model', 'sync', 'viewmodel')
foreach ($paquete in $paquetesLogicos) {
    $lineas = $jacoco.SelectNodes("/report/package[@name='cl/induztek/app/$paquete']/sourcefile")
    $paqueteCubiertas = 0
    $paqueteOmitidas = 0
    foreach ($archivo in $lineas) {
        if ($archivo.name -like '*_Impl.kt' -or $archivo.name -like '*_Impl.java') { continue }
        $contador = $archivo.SelectSingleNode("counter[@type='LINE']")
        if ($contador) {
            $paqueteCubiertas += [int]$contador.covered
            $paqueteOmitidas += [int]$contador.missed
        }
    }
    $totalPaquete = $paqueteCubiertas + $paqueteOmitidas
    if ($totalPaquete -eq 0) { throw "El reporte no tiene lineas para $paquete." }
    Write-Output ('{0,-10} {1,4}/{2,-4} {3:P2}' -f $paquete, $paqueteCubiertas, $totalPaquete, ($paqueteCubiertas / $totalPaquete))
    $cubiertas += $paqueteCubiertas
    $omitidas += $paqueteOmitidas
}
$total = $cubiertas + $omitidas
Write-Output ('{0,-10} {1,4}/{2,-4} {3:P2}' -f 'Logica', $cubiertas, $total, ($cubiertas / $total))
