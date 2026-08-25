[CmdletBinding()]
param(
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT
)

$ErrorActionPreference = 'Stop'
$root = if ($DevelopmentRoot) { [IO.Path]::GetFullPath($DevelopmentRoot) } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'windows\.local-runtime' }
Get-ChildItem -LiteralPath $root -Force -ErrorAction SilentlyContinue | Select-Object Name, FullName, @{ Name = 'SizeBytes'; Expression = { if ($_.PSIsContainer) { $null } else { $_.Length } } }
