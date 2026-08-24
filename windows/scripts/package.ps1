[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RuntimeBundle,
    [Parameter(Mandatory)]
    [string]$IsccPath
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$stage = Join-Path $repo 'dist-windows\stage'
if (Test-Path -LiteralPath $stage) { throw "Refusing to overwrite existing staging directory: $stage" }
New-Item -ItemType Directory -Path $stage | Out-Null

try {
    Expand-Archive -LiteralPath $RuntimeBundle -DestinationPath $stage
    & powershell -ExecutionPolicy Bypass -File (Join-Path $repo 'windows\scripts\sync-apk.ps1') -StageRoot $stage
    & powershell -ExecutionPolicy Bypass -File (Join-Path $repo 'windows\scripts\verify-runtime.ps1') -RuntimeRoot $stage
    & $IsccPath "/DSourceRoot=$stage" (Join-Path $repo 'windows\installer\TangoPro.iss')
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed with exit code $LASTEXITCODE." }
} catch {
    throw
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
}
