[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)]
    [string]$DevelopmentRoot,
    [Parameter(Mandatory)]
    [string]$GuestId
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($DevelopmentRoot)
$candidate = Join-Path (Join-Path $root 'guest') $GuestId
if (-not $candidate.StartsWith((Join-Path $root 'guest'), [StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing an invalid guest target.' }
if (Test-Path -LiteralPath $candidate -PathType Container -and $PSCmdlet.ShouldProcess($candidate, 'Remove extracted guest only (userdata is preserved)')) { Remove-Item -LiteralPath $candidate -Recurse -Force }
