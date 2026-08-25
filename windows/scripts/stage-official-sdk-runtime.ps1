[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$LauncherPath,
    [Parameter(Mandatory)]
    [string]$ViewerRoot,
    [string]$StageRoot = (Join-Path $env:LOCALAPPDATA 'TangoPro\official-sdk-stage')
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$stage = [IO.Path]::GetFullPath($StageRoot)
$launcher = [IO.Path]::GetFullPath($LauncherPath)
$viewer = [IO.Path]::GetFullPath($ViewerRoot)
if (Test-Path -LiteralPath $stage) { throw "Refusing to overwrite existing staging directory: $stage" }
foreach ($required in $launcher, (Join-Path $viewer 'TangoView.exe'), (Join-Path $repo 'windows\runtime\sdk-bootstrap.lock.json'), (Join-Path $repo 'windows\runtime\setup-android-runtime.ps1')) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required staging input not found: $required" }
}

New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item -LiteralPath $launcher -Destination (Join-Path $stage 'TangoPro.exe')
foreach ($lock in 'runtime.lock.json', 'components.lock.json', 'sdk-bootstrap.lock.json') {
    Copy-Item -LiteralPath (Join-Path $repo "windows\runtime\$lock") -Destination (Join-Path $stage $lock)
}
New-Item -ItemType Directory -Path (Join-Path $stage 'runtime') | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'windows\runtime\setup-android-runtime.ps1') -Destination (Join-Path $stage 'runtime\setup-android-runtime.ps1')
Copy-Item -LiteralPath $viewer -Destination (Join-Path $stage 'runtime\viewer') -Recurse
& powershell -ExecutionPolicy Bypass -File (Join-Path $repo 'windows\scripts\sync-apk.ps1') -StageRoot $stage
if ($LASTEXITCODE -ne 0) { throw 'APK staging failed.' }
& powershell -ExecutionPolicy Bypass -File (Join-Path $repo 'windows\scripts\verify-runtime.ps1') -RuntimeRoot $stage -ApksignerPath $env:TANGO_APKSIGNER
if ($LASTEXITCODE -ne 0) { throw 'Runtime staging verification failed.' }
Write-Output "Official-SDK-first-run runtime staged at $stage"
