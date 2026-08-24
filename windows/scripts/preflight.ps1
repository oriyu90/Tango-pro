[CmdletBinding()]
param(
    [string]$RuntimeRoot
)

$ErrorActionPreference = 'Stop'
if (-not [Environment]::Is64BitOperatingSystem -or [Environment]::Is64BitProcess -eq $false) { throw 'Tango Pro requires 64-bit Windows and a 64-bit process.' }
$computer = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$systemDrive = Get-PSDrive -Name $env:SystemDrive.TrimEnd(':')
$whpx = & dism.exe /Online /Get-FeatureInfo /FeatureName:HypervisorPlatform 2>$null
$whpxEnabled = $whpx -match 'State\s*:\s*Enabled'

[pscustomobject]@{
    OsCaption = $os.Caption
    OsVersion = $os.Version
    Architecture = $env:PROCESSOR_ARCHITECTURE
    MemoryGiB = [math]::Round($computer.TotalPhysicalMemory / 1GB, 1)
    FreeDiskGiB = [math]::Round($systemDrive.Free / 1GB, 1)
    WhpxEnabled = $whpxEnabled
    RuntimeRoot = $RuntimeRoot
}

if (-not $whpxEnabled) { throw 'Windows Hypervisor Platform is not enabled. Enable it and restart before starting Tango Pro.' }
if ($RuntimeRoot -and -not (Test-Path -LiteralPath $RuntimeRoot -PathType Container)) { throw "Runtime root not found: $RuntimeRoot" }
