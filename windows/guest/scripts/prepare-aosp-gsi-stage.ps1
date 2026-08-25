[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ReferenceImageRoot,
    [Parameter(Mandatory)]
    [string]$GsiSystemImage,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ReferenceImageRoot -PathType Container)) { throw "GUEST_STAGE_FAILED: reference image root not found: $ReferenceImageRoot" }
if (-not (Test-Path -LiteralPath $GsiSystemImage -PathType Leaf)) { throw "GUEST_STAGE_FAILED: GSI system image not found: $GsiSystemImage" }

$root = if ($DevelopmentRoot) { [IO.Path]::GetFullPath($DevelopmentRoot) } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'windows\.local-runtime' }
$stage = Join-Path $root 'gsi-test\sdk-api35-system-overlay'
$reference = [IO.Path]::GetFullPath($ReferenceImageRoot)
if ($stage.StartsWith($reference, [StringComparison]::OrdinalIgnoreCase) -or $reference.StartsWith([IO.Path]::GetFullPath($stage), [StringComparison]::OrdinalIgnoreCase)) { throw 'GUEST_STAGE_FAILED: stage and reference image must be separate directories.' }

New-Item -ItemType Directory -Force -Path $stage | Out-Null
Get-ChildItem -LiteralPath $reference -Force | Copy-Item -Destination $stage -Recurse -Force
$target = Join-Path $stage 'system.img'
if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw 'GUEST_STAGE_FAILED: copied reference image has no system.img.' }
Copy-Item -LiteralPath $GsiSystemImage -Destination $target -Force
[ordered]@{ stage=$stage; systemSha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant(); referenceModified=$false } | ConvertTo-Json -Compress
