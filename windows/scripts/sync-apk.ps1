[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$StageRoot,
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ApkPath = if ($ApkPath) { $ApkPath } else { Join-Path $repo 'dist-android\Tango-pro-2.1.0-android-debug.apk' }
$stage = [IO.Path]::GetFullPath($StageRoot)
$apk = [IO.Path]::GetFullPath($ApkPath)
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK not found: $apk" }

$payload = Join-Path $stage 'payload\TangoPro.apk'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $payload) | Out-Null
Copy-Item -LiteralPath $apk -Destination $payload -Force

$sourceLock = Get-Content -LiteralPath (Join-Path $repo 'windows\runtime\app.lock.json') -Raw | ConvertFrom-Json
$sourceLock.apk = 'payload/TangoPro.apk'
$sourceLock.sha256 = (Get-FileHash -LiteralPath $payload -Algorithm SHA256).Hash.ToLowerInvariant()
$json = $sourceLock | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $stage 'app.lock.json'), $json, (New-Object Text.UTF8Encoding($false)))

Write-Output "Staged payload: $payload"
