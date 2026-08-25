[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT,
    [int]$Retries = 3
)

$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path (Split-Path -Parent $PSScriptRoot) 'manifests\blissos-16.9.7-foss-generic.json' }

function Get-GuestRoot {
    param([string]$Root)
    if ($Root) { return [IO.Path]::GetFullPath($Root) }
    $repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
    return Join-Path $repo 'windows\.local-runtime'
}

function Get-Manifest {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "GUEST_MANIFEST_INVALID: not found: $Path" }
    $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($value.schema -ne 1 -or $value.type -ne 'prebuilt-qemu' -or $value.status -notin 'candidate','approved') { throw 'GUEST_MANIFEST_INVALID: unsupported schema, type, or status.' }
    if ($value.upstream.architecture -ne 'x86_64' -or $value.upstream.variant -ne 'FOSS' -or $value.runtime.engine -ne 'qemu') { throw 'GUEST_MANIFEST_INVALID: unexpected guest identity.' }
    if ($value.integrity.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'GUEST_MANIFEST_INVALID: SHA-256 must be pinned.' }
    if ([IO.Path]::GetFileName($value.upstream.artifact) -ne $value.upstream.artifact) { throw 'GUEST_MANIFEST_INVALID: artifact must be a file name.' }
    return $value
}

$guest = Get-Manifest -Path $Manifest
$root = Get-GuestRoot -Root $DevelopmentRoot
$cache = Join-Path $root 'guest-cache'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
$final = Join-Path $cache $guest.upstream.artifact

if (Test-Path -LiteralPath $final -PathType Leaf) {
    $actual = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -eq $guest.integrity.sha256) { Write-Output $final; exit 0 }
    throw "GUEST_HASH_MISMATCH: existing cache artifact does not match the manifest: $final"
}

$checksumPart = "$final.sha256.part"
$artifactPart = "$final.part"
try {
    & curl.exe -L --fail --silent --show-error --output $checksumPart $guest.upstream.checksumUrl
    if ($LASTEXITCODE -ne 0) { throw "GUEST_DOWNLOAD_FAILED: could not download upstream checksum (curl exit $LASTEXITCODE)." }
    $checksumLine = Get-Content -LiteralPath $checksumPart -Raw -Encoding UTF8
    $match = [regex]::Match($checksumLine, "(?im)^([a-f0-9]{64})\s+\*?$([regex]::Escape($guest.upstream.artifact))\s*$")
    if (-not $match.Success) { throw 'GUEST_DOWNLOAD_FAILED: upstream checksum file has an unexpected format.' }
    $upstreamHash = $match.Groups[1].Value.ToLowerInvariant()
    if ($upstreamHash -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: upstream checksum differs from repository lock.' }

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        if (Test-Path -LiteralPath $artifactPart) { Remove-Item -LiteralPath $artifactPart -Force }
        try {
            & curl.exe -L --fail --retry 3 --retry-delay 2 --output $artifactPart $guest.upstream.artifactUrl
            if ($LASTEXITCODE -ne 0) { throw "curl exited with $LASTEXITCODE" }
            break
        } catch {
            if ($attempt -eq $Retries) { throw }
            Start-Sleep -Seconds ($attempt * 2)
        }
    }
    $actual = (Get-FileHash -LiteralPath $artifactPart -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: downloaded ISO does not match the locked hash.' }
    Move-Item -LiteralPath $artifactPart -Destination $final
    Write-Output $final
} catch {
    if (Test-Path -LiteralPath $artifactPart) { Remove-Item -LiteralPath $artifactPart -Force }
    throw
} finally {
    if (Test-Path -LiteralPath $checksumPart) { Remove-Item -LiteralPath $checksumPart -Force }
}
