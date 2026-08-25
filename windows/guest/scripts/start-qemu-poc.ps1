[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT,
    [string]$Qemu = 'C:\Program Files\qemu\qemu-system-x86_64.exe',
    [int]$AdbPort = 5555
)

$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path (Split-Path -Parent $PSScriptRoot) 'manifests\blissos-16.9.7-foss-generic.json' }
$guest = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($guest.status -notin 'candidate','approved') { throw 'GUEST_UNSUPPORTED: guest is not a runnable candidate.' }
if (-not (Test-Path -LiteralPath $Qemu -PathType Leaf)) { throw "QEMU_START_FAILED: QEMU not found: $Qemu" }
$root = if ($DevelopmentRoot) { [IO.Path]::GetFullPath($DevelopmentRoot) } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'windows\.local-runtime' }
$iso = Join-Path (Join-Path $root 'guest-cache') $guest.upstream.artifact
if (-not (Test-Path -LiteralPath $iso -PathType Leaf)) { throw "GUEST_DOWNLOAD_FAILED: ISO is not cached: $iso" }
if ((Get-FileHash -LiteralPath $iso -Algorithm SHA256).Hash.ToLowerInvariant() -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: ISO does not match lock.' }
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$args = @(
    '-accel', 'whpx', '-machine', 'q35', '-cpu', 'max',
    '-m', $guest.runtime.ramMb, '-smp', $guest.runtime.cpuCount,
    '-device', 'virtio-vga', '-cdrom', $iso, '-boot', 'once=d',
    '-netdev', "user,id=guestnet,restrict=on,hostfwd=tcp:127.0.0.1:$AdbPort-:5555",
    '-device', 'virtio-net-pci,netdev=guestnet',
    '-serial', "file:$(Join-Path $logs 'qemu.serial.log')",
    '-D', (Join-Path $logs 'qemu.debug.log')
)
$argumentLine = ($args | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
Write-Output 'Starting visible Bliss OS QEMU PoC. The guest has restricted user networking; only host-local ADB forwarding is configured.'
Start-Process -FilePath $Qemu -ArgumentList $argumentLine -PassThru
