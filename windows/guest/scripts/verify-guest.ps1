[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$DevelopmentRoot = $env:TANGO_DEV_ROOT,
    [switch]$SkipUpstreamChecksum
)

$ErrorActionPreference = 'Stop'
if (-not $Manifest) { $Manifest = Join-Path (Split-Path -Parent $PSScriptRoot) 'manifests\blissos-16.9.7-foss-generic.json' }
$guest = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($guest.schema -ne 1 -or $guest.type -ne 'prebuilt-qemu' -or $guest.status -notin 'candidate','approved') { throw 'GUEST_MANIFEST_INVALID: unsupported schema, type, or status.' }
if ($guest.upstream.architecture -ne 'x86_64' -or $guest.upstream.variant -ne 'FOSS' -or $guest.integrity.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'GUEST_MANIFEST_INVALID: expected pinned FOSS x86_64 guest.' }
$root = if ($DevelopmentRoot) { [IO.Path]::GetFullPath($DevelopmentRoot) } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'windows\.local-runtime' }
$artifact = Join-Path (Join-Path $root 'guest-cache') $guest.upstream.artifact
if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "GUEST_DOWNLOAD_FAILED: artifact is not cached: $artifact" }
$size = (Get-Item -LiteralPath $artifact).Length
if ($size -lt 1GB) { throw "GUEST_MANIFEST_INVALID: cached ISO is implausibly small ($size bytes)." }
$actual = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: cached ISO does not match repository lock.' }
if (-not $SkipUpstreamChecksum) {
    $checksum = Join-Path ([IO.Path]::GetTempPath()) ("tango-guest-" + [guid]::NewGuid().ToString('N') + '.sha256')
    try {
        & curl.exe -L --fail --silent --show-error --output $checksum $guest.upstream.checksumUrl
        if ($LASTEXITCODE -ne 0) { throw "GUEST_DOWNLOAD_FAILED: could not download upstream checksum (curl exit $LASTEXITCODE)." }
        $upstream = Get-Content -LiteralPath $checksum -Raw -Encoding UTF8
    } finally {
        if (Test-Path -LiteralPath $checksum) { Remove-Item -LiteralPath $checksum -Force }
    }
    $match = [regex]::Match($upstream, "(?im)^([a-f0-9]{64})\s+\*?$([regex]::Escape($guest.upstream.artifact))\s*$")
    if (-not $match.Success -or $match.Groups[1].Value.ToLowerInvariant() -ne $guest.integrity.sha256) { throw 'GUEST_HASH_MISMATCH: upstream checksum does not match repository lock.' }
}
[pscustomobject]@{ Id = $guest.id; Artifact = $artifact; SizeBytes = $size; Sha256 = $actual; Status = $guest.status; UpstreamChecksumChecked = -not $SkipUpstreamChecksum }
