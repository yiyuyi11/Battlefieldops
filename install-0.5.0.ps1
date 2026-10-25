$ErrorActionPreference = 'Stop'
$packRoot = (Resolve-Path -LiteralPath 'D:\USTBL\.minecraft\versions\Mechanomania-航空学').Path
$modsPath = (Resolve-Path -LiteralPath (Join-Path $packRoot 'mods')).Path
$sourceJar = 'D:\GTMchange\BattlefieldOps\build\libs\battlefield-ops-0.5.0-neoforge-1.21.1.jar'
$backupPath = [IO.Path]::GetFullPath((Join-Path $packRoot ('bfops-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))))
if (-not $modsPath.StartsWith($packRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid mods directory' }
if (-not $backupPath.StartsWith($packRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid backup directory' }
if (-not (Test-Path -LiteralPath $sourceJar)) { throw "Missing build output: $sourceJar" }
$null = New-Item -ItemType Directory -Path $backupPath
$movedFiles = @()
$copiedNew = $false
try {
    foreach ($jarName in @('battlefield-ops-0.1.0-neoforge-1.21.1.jar','battlefield-ops-0.2.0-neoforge-1.21.1.jar','battlefield-ops-0.3.0-neoforge-1.21.1.jar','battlefield-ops-0.4.0-neoforge-1.21.1.jar')) {
        $oldPath = [IO.Path]::GetFullPath((Join-Path $modsPath $jarName))
        if (-not $oldPath.StartsWith($modsPath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid mod path' }
        if (Test-Path -LiteralPath $oldPath) {
            Move-Item -LiteralPath $oldPath -Destination (Join-Path $backupPath $jarName)
            $movedFiles += $jarName
        }
    }
    $newPath = Join-Path $modsPath 'battlefield-ops-0.5.0-neoforge-1.21.1.jar'
    Copy-Item -LiteralPath $sourceJar -Destination $newPath
    $copiedNew = $true
    if ((Get-FileHash -LiteralPath $sourceJar).Hash -ne (Get-FileHash -LiteralPath $newPath).Hash) { throw 'Installed JAR checksum mismatch' }
    Write-Output "Installed: $newPath"
    Write-Output "Backup: $backupPath"
} catch {
    if ($copiedNew -and (Test-Path -LiteralPath $newPath)) {
        Move-Item -LiteralPath $newPath -Destination (Join-Path $backupPath 'failed-install.jar')
    }
    foreach ($jarName in $movedFiles) {
        Copy-Item -LiteralPath (Join-Path $backupPath $jarName) -Destination (Join-Path $modsPath $jarName) -Force
    }
    throw
}
