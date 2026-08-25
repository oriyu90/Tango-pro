[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RuntimeRoot,
    [Parameter(Mandatory)]
    [string]$ApksignerPath
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RuntimeRoot)
foreach ($file in 'runtime.lock.json','components.lock.json','sdk-bootstrap.lock.json','app.lock.json','payload\TangoPro.apk','runtime\viewer\TangoView.exe','runtime\setup-android-runtime.ps1') {
    $path = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing runtime component: $path" }
}
$bootstrap = Get-Content -LiteralPath (Join-Path $root 'sdk-bootstrap.lock.json') -Raw | ConvertFrom-Json
if ($bootstrap.commandLineTools.downloadedDirectlyByUser -ne $true) { throw 'SDK bootstrap must be downloaded directly by the user.' }
foreach ($prohibited in 'runtime\android-sdk', 'runtime\adb', 'runtime\emulator', 'runtime\android', 'runtime\system-images', 'runtime\cmdline-tools') {
    if (Test-Path -LiteralPath (Join-Path $root $prohibited)) { throw "Google Android SDK content must not be packaged: $prohibited" }
}
$forbiddenFiles = @('emulator.exe', 'adb.exe', 'sdkmanager.bat', 'avdmanager.bat', 'package.xml', 'system.img', 'vendor.img', 'userdata.img')
$foundForbidden = Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object { $forbiddenFiles -contains $_.Name } | Select-Object -First 1
if ($foundForbidden) { throw "Google Android SDK artifact must not be packaged: $($foundForbidden.FullName)" }
$appLock = Get-Content -LiteralPath (Join-Path $root 'app.lock.json') -Raw | ConvertFrom-Json
if ($appLock.apk -ne 'payload/TangoPro.apk') { throw 'app.lock.json must point to payload/TangoPro.apk.' }
$actual = (Get-FileHash -LiteralPath (Join-Path $root $appLock.apk) -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $appLock.sha256.ToLowerInvariant()) { throw 'Payload SHA-256 does not match app.lock.json.' }
if (-not (Test-Path -LiteralPath $ApksignerPath -PathType Leaf)) { throw "External apksigner not found: $ApksignerPath" }
& $ApksignerPath verify --verbose --print-certs (Join-Path $root 'payload\TangoPro.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner rejected the bundled payload.' }
Write-Output 'Official-SDK-first-run layout and payload hash verified; no Google SDK artifact is bundled.'
