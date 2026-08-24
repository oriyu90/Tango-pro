[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RuntimeRoot
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RuntimeRoot)
foreach ($file in 'runtime.lock.json','components.lock.json','app.lock.json','payload\TangoPro.apk','runtime\adb\adb.exe','runtime\emulator\emulator.exe','runtime\viewer\TangoView.exe','runtime\android\apksigner.bat') {
    $path = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing runtime component: $path" }
}
$appLock = Get-Content -LiteralPath (Join-Path $root 'app.lock.json') -Raw | ConvertFrom-Json
if ($appLock.apk -ne 'payload/TangoPro.apk') { throw 'app.lock.json must point to payload/TangoPro.apk.' }
$actual = (Get-FileHash -LiteralPath (Join-Path $root $appLock.apk) -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $appLock.sha256.ToLowerInvariant()) { throw 'Payload SHA-256 does not match app.lock.json.' }
& (Join-Path $root 'runtime\android\apksigner.bat') verify --verbose --print-certs (Join-Path $root 'payload\TangoPro.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner rejected the bundled payload.' }
Write-Output 'Runtime layout and payload hash verified.'
