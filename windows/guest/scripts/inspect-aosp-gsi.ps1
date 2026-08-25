[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Archive,
    [Parameter(Mandatory)]
    [string]$OutputJson,
    [string]$ExtractRoot
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) { throw "GUEST_INSPECTION_FAILED: archive not found: $Archive" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($Archive))
try {
    $entries = @($zip.Entries | Where-Object { -not $_.FullName.EndsWith('/') } | Sort-Object FullName | ForEach-Object {
        [ordered]@{ name=$_.FullName; size=$_.Length; compressedSize=$_.CompressedLength }
    })
    $system = @($entries | Where-Object { $_.name -match '(^|/)system\.img$' })
    if ($system.Count -ne 1) { throw 'GUEST_INSPECTION_FAILED: archive must contain exactly one system.img.' }
    $systemImage = [ordered]@{ name=$system[0].name; size=$system[0].size; format='unknown-not-extracted'; sha256=$null }
    if ($ExtractRoot) {
        $extract = [IO.Path]::GetFullPath($ExtractRoot)
        New-Item -ItemType Directory -Force -Path $extract | Out-Null
        $destination = Join-Path $extract 'system.img'
        if (Test-Path -LiteralPath $destination) { throw "GUEST_INSPECTION_FAILED: refusing to overwrite extracted system image: $destination" }
        $entry = $zip.Entries | Where-Object { $_.FullName -eq $system[0].name } | Select-Object -First 1
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $false)
        $magic = [IO.File]::ReadAllBytes($destination)[0..3]
        $systemImage.format = if (($magic -join ',') -eq '58,255,38,237') { 'android-sparse' } else { 'raw-or-other' }
        $systemImage.sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [ordered]@{
        schema = 1
        archive = [IO.Path]::GetFileName($Archive)
        archiveSha256 = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash.ToLowerInvariant()
        entries = $entries
        systemImage = $systemImage
        note = 'This inspection does not mount, modify, or repack the GSI. It extracts only system.img into a development-only stage when -ExtractRoot is supplied.'
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputJson -Encoding UTF8
} finally {
    $zip.Dispose()
}
