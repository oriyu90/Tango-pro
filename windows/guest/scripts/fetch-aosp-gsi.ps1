[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT,
    [switch]$AcceptAndroid15GsiLicense
)

$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path (Split-Path -Parent $PSScriptRoot) 'manifests\android15-aosp-x86_64-gsi.json' }

function Get-GuestRoot {
    param([string]$Root)
    if ($Root) { return [IO.Path]::GetFullPath($Root) }
    $repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repo 'windows\.local-runtime'
}

if (-not $AcceptAndroid15GsiLicense) {
    throw 'GUEST_LICENSE_ACCEPTANCE_REQUIRED: review and accept the Android 15 GSI terms at https://developer.android.com/topic/generic-system-image/releases, then rerun with -AcceptAndroid15GsiLicense.'
}
if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) { throw "GUEST_MANIFEST_INVALID: not found: $Manifest" }

$guest = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($guest.schema -ne 1 -or $guest.type -ne 'aosp-gsi-overlay' -or $guest.status -ne 'candidate') { throw 'GUEST_MANIFEST_INVALID: unsupported schema, type, or status.' }
if ($guest.androidVersion -ne '15' -or $guest.apiLevel -ne 35 -or $guest.architecture -ne 'x86_64' -or $guest.variant -ne 'AOSP-non-GMS') { throw 'GUEST_MANIFEST_INVALID: unexpected GSI identity.' }
if ($guest.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'GUEST_MANIFEST_INVALID: SHA-256 must be pinned.' }
if (-not $guest.license.acceptanceRequired) { throw 'GUEST_MANIFEST_INVALID: missing license acceptance requirement.' }

$uri = [Uri]$guest.artifactUrl
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'dl.google.com' -or [IO.Path]::GetFileName($uri.AbsolutePath) -ne $guest.artifactName) { throw 'GUEST_MANIFEST_INVALID: artifact URL must be the pinned Android Developers download.' }

$cache = Join-Path (Get-GuestRoot -Root $DevelopmentRoot) (Join-Path 'guest-cache\aosp-gsi' $guest.buildId)
New-Item -ItemType Directory -Force -Path $cache | Out-Null
$final = Join-Path $cache $guest.artifactName
$part = "$final.part"

if (Test-Path -LiteralPath $final -PathType Leaf) {
    $actual = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -eq $guest.sha256) { Write-Output $final; exit 0 }
    throw "GUEST_HASH_MISMATCH: existing cache artifact does not match manifest: $final"
}

try {
    if (Test-Path -LiteralPath $part -PathType Leaf) {
        $partialHash = (Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($partialHash -eq $guest.sha256) {
            Move-Item -LiteralPath $part -Destination $final
            Write-Output $final
            exit 0
        }
    }
    & curl.exe -L --fail --retry 3 --retry-delay 2 --continue-at - --output $part $guest.artifactUrl
    if ($LASTEXITCODE -ne 0) { throw "GUEST_DOWNLOAD_FAILED: curl exited with $LASTEXITCODE." }
    $actual = (Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $guest.sha256) { throw 'GUEST_HASH_MISMATCH: downloaded GSI does not match the manifest.' }
    Move-Item -LiteralPath $part -Destination $final
    Write-Output $final
} catch {
    if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
    throw
}
