[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot,
    [string]$LockPath
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$SdkRoot = [IO.Path]::GetFullPath($SdkRoot)
if (-not $LockPath) { $LockPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime\sdk-bootstrap.lock.json' }
$LockPath = [IO.Path]::GetFullPath($LockPath)
$lock = Get-Content -LiteralPath $LockPath -Raw | ConvertFrom-Json

function Assert-Package([object]$Package) {
    $relative = $Package.id.Replace(';', [IO.Path]::DirectorySeparatorChar)
    $root = Join-Path $SdkRoot $relative
    $properties = Join-Path $root 'source.properties'
    $packageXml = Join-Path $root 'package.xml'
    if (-not (Test-Path -LiteralPath $properties -PathType Leaf) -or -not (Test-Path -LiteralPath $packageXml -PathType Leaf)) {
        throw "RUNTIME_PACKAGE_UNAVAILABLE: $($Package.id)"
    }
    $sourceRevision = (Get-Content -LiteralPath $properties | Where-Object { $_ -like 'Pkg.Revision=*' } | Select-Object -First 1).Substring(13)
    [xml]$xml = Get-Content -LiteralPath $packageXml -Raw
    $node = $xml.SelectSingleNode('//*[local-name()="localPackage"]')
    $revision = $node.SelectSingleNode('./*[local-name()="revision"]')
    $parts = @($revision.SelectSingleNode('./*[local-name()="major"]').InnerText)
    foreach ($part in 'minor', 'micro') {
        $child = $revision.SelectSingleNode('./*[local-name()="' + $part + '"]')
        if ($child) { $parts += $child.InnerText }
    }
    $xmlRevision = $parts -join '.'
    if ($node.path -ne $Package.id -or $sourceRevision -ne $Package.revision -or $xmlRevision -ne $Package.revision) {
        throw "RUNTIME_PACKAGE_REVISION_MISMATCH: $($Package.id) source=$sourceRevision packageXml=$xmlRevision"
    }
    [pscustomobject]@{ Package = $Package.id; Revision = $xmlRevision; Status = 'verified' }
}

$commandLine = $lock.commandLineTools
if ($commandLine.sourceUrl -ne 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -or $commandLine.sha256 -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') {
    throw 'SDK bootstrap lock differs from the approved RC baseline; investigate before changing it.'
}

$sdkmanager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkmanager -PathType Leaf)) { throw "RUNTIME_PACKAGE_UNAVAILABLE: $sdkmanager" }
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$listing = (& $sdkmanager "--sdk_root=$SdkRoot" --list 2>&1 | Out-String)
$ErrorActionPreference = $previousErrorActionPreference
if ($LASTEXITCODE -ne 0) { throw 'RUNTIME_PACKAGE_UNAVAILABLE: sdkmanager --list failed' }
$packages = @($lock.packages.emulator, $lock.packages.platformTools, $lock.packages.buildTools, $lock.packages.systemImage)
foreach ($package in $packages) {
    $pattern = '(?m)^\s*' + [regex]::Escape($package.id) + '\s*\|\s*' + [regex]::Escape([string]$package.revision) + '\s*\|'
    if ($listing -notmatch $pattern) { throw "RUNTIME_PACKAGE_UNAVAILABLE: $($package.id) $($package.revision)" }
}

$required = [Int64]$lock.storageBudget.minimumFreshInstallFreeBytes
$free = (Get-Item -LiteralPath $SdkRoot).PSDrive.Free
[pscustomobject]@{ Bootstrap = $commandLine.version; LockStatus = 'verified'; FreeBytes = $free; RequiredFreshInstallBytes = $required } | Format-List
$packages | ForEach-Object { Assert-Package $_ } | Format-Table -AutoSize
Write-Output 'SDK lock validation passed without modifying the SDK or any AVD.'
