[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT,
    [string]$FactoryDataImage
)

$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path (Split-Path -Parent $PSScriptRoot) 'manifests\blissos-16.9.7-foss-generic.json' }
$guest = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($guest.type -ne 'prebuilt-qemu' -or $guest.integrity.sha256 -notmatch '^[a-f0-9]{64}$' -or $guest.runtime.systemArtifact -notin 'system.sfs','system.efs') { throw 'GUEST_MANIFEST_INVALID: prebuilt QEMU guest with locked hash and known system artifact is required.' }
$root = if ($DevelopmentRoot) { [IO.Path]::GetFullPath($DevelopmentRoot) } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'windows\.local-runtime' }
$iso = Join-Path (Join-Path $root 'guest-cache') $guest.upstream.artifact
if (-not (Test-Path -LiteralPath $iso -PathType Leaf)) { throw "GUEST_DOWNLOAD_FAILED: ISO is not cached: $iso" }
if ((Get-FileHash -LiteralPath $iso -Algorithm SHA256).Hash.ToLowerInvariant() -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: refusing to extract an unverified ISO.' }

$target = Join-Path (Join-Path $root 'guest') $guest.id
$androidOs = Join-Path $target 'AndroidOS'
New-Item -ItemType Directory -Force -Path $androidOs | Out-Null
$mounted = $false
try {
    Mount-DiskImage -ImagePath $iso -StorageType ISO -Access ReadOnly | Out-Null
    $mounted = $true
    $volume = Get-DiskImage -ImagePath $iso | Get-Volume
    $source = $volume.DriveLetter + ':'
    foreach ($asset in 'kernel','initrd.img',$guest.runtime.systemArtifact) {
        $from = Join-Path $source $asset
        $to = Join-Path $androidOs $asset
        if (-not (Test-Path -LiteralPath $from -PathType Leaf)) { throw "GUEST_BOOT_FAILED: required boot asset is absent: $asset" }
        if (Test-Path -LiteralPath $to -PathType Leaf) {
            if ((Get-FileHash -LiteralPath $from -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $to -Algorithm SHA256).Hash) { throw "GUEST_BOOT_FAILED: existing extracted asset differs: $to" }
        } else {
            Copy-Item -LiteralPath $from -Destination $to
        }
    }
} finally {
    if ($mounted) { Dismount-DiskImage -ImagePath $iso }
}

if ($FactoryDataImage) {
    if (-not (Test-Path -LiteralPath $FactoryDataImage -PathType Leaf)) { throw "Factory data image not found: $FactoryDataImage" }
    $dataDir = Join-Path $root 'userdata'
    $data = Join-Path $dataDir 'data.img'
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    if (-not (Test-Path -LiteralPath $data)) { Copy-Item -LiteralPath $FactoryDataImage -Destination $data }
}

[pscustomobject]@{ GuestRoot = $target; Kernel = Join-Path $androidOs 'kernel'; Initrd = Join-Path $androidOs 'initrd.img'; System = Join-Path $androidOs $guest.runtime.systemArtifact; DataImage = Join-Path $root 'userdata\data.img' }
