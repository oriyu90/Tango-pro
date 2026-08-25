[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$ScrcpyRoot = (Join-Path $env:LOCALAPPDATA 'TangoPro\poc-scrcpy\scrcpy-win64-v4.1'),
    [string]$StageRoot = (Join-Path $env:LOCALAPPDATA 'TangoPro\launcher-stage')
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$stage = [IO.Path]::GetFullPath($StageRoot)
$launcher = Join-Path $repo 'windows\launcher\target\release\TangoPro.exe'
if (-not $SdkRoot) { throw 'Set ANDROID_HOME or pass -SdkRoot.' }
if (Test-Path -LiteralPath $stage) { throw "Refusing to overwrite existing PoC stage: $stage" }
foreach ($required in $launcher, (Join-Path $SdkRoot 'platform-tools\adb.exe'), (Join-Path $SdkRoot 'emulator\emulator.exe'), (Join-Path $SdkRoot 'build-tools\36.0.0\apksigner.bat'), (Join-Path $ScrcpyRoot 'scrcpy.exe')) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required PoC component not found: $required" }
}

New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item -LiteralPath $launcher -Destination (Join-Path $stage 'TangoPro.exe')
Copy-Item -LiteralPath (Join-Path $repo 'windows\runtime\runtime.lock.json') -Destination (Join-Path $stage 'runtime.lock.json')
Copy-Item -LiteralPath (Join-Path $repo 'windows\runtime\components.lock.json') -Destination (Join-Path $stage 'components.lock.json')
Copy-Item -LiteralPath (Join-Path $repo 'windows\runtime\sdk-bootstrap.lock.json') -Destination (Join-Path $stage 'sdk-bootstrap.lock.json')
New-Item -ItemType Directory -Path (Join-Path $stage 'runtime') | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'windows\runtime\setup-android-runtime.ps1') -Destination (Join-Path $stage 'runtime\setup-android-runtime.ps1')
Copy-Item -LiteralPath (Join-Path $SdkRoot 'platform-tools') -Destination (Join-Path $stage 'runtime\adb') -Recurse
Copy-Item -LiteralPath (Join-Path $SdkRoot 'emulator') -Destination (Join-Path $stage 'runtime\emulator') -Recurse
Copy-Item -LiteralPath (Join-Path $SdkRoot 'build-tools\36.0.0') -Destination (Join-Path $stage 'runtime\android') -Recurse
Copy-Item -LiteralPath (Join-Path $SdkRoot 'platform-tools') -Destination (Join-Path $stage 'runtime\android\platform-tools') -Recurse
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'runtime\android\system-images\android-35\default') | Out-Null
Copy-Item -LiteralPath (Join-Path $SdkRoot 'system-images\android-35\default\x86_64') -Destination (Join-Path $stage 'runtime\android\system-images\android-35\default\x86_64') -Recurse
Copy-Item -LiteralPath $ScrcpyRoot -Destination (Join-Path $stage 'runtime\viewer') -Recurse
Move-Item -LiteralPath (Join-Path $stage 'runtime\viewer\scrcpy.exe') -Destination (Join-Path $stage 'runtime\viewer\TangoView.exe')
& powershell -ExecutionPolicy Bypass -File (Join-Path $repo 'windows\scripts\sync-apk.ps1') -StageRoot $stage
if ($LASTEXITCODE -ne 0) { throw 'APK staging failed.' }
Write-Output "PoC-only runtime staged at $stage"
