[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$CommandLineToolsZip,
    [Parameter(Mandatory)] [ValidatePattern('^[A-Fa-f0-9]{64}$')] [string]$ExpectedSha256,
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'TangoPro\\poc-sdk'),
    [switch]$AcceptAndroidSdkLicense,
    [switch]$IncludeApi36
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AcceptAndroidSdkLicense) {
    throw 'Review the Android SDK licence and rerun with -AcceptAndroidSdkLicense to install SDK packages.'
}

$zip = (Resolve-Path -LiteralPath $CommandLineToolsZip).Path
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant()
if ($actualSha256 -ne $ExpectedSha256.ToLowerInvariant()) {
    throw 'Command-line tools checksum does not match the value supplied from the official download page.'
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("tango-sdk-tools-" + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Force -Path $temporaryRoot, $SdkRoot | Out-Null
    Expand-Archive -LiteralPath $zip -DestinationPath $temporaryRoot -Force
    $source = Join-Path $temporaryRoot 'cmdline-tools'
    if (-not (Test-Path -LiteralPath (Join-Path $source 'bin\\sdkmanager.bat'))) {
        throw 'The ZIP does not contain the expected cmdline-tools directory.'
    }

    $toolsRoot = Join-Path $SdkRoot 'cmdline-tools\\latest'
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    Get-ChildItem -LiteralPath $source -Force | Copy-Item -Destination $toolsRoot -Recurse -Force
    $sdkManager = Join-Path $toolsRoot 'bin\\sdkmanager.bat'
    $sdkPackages = @(
        'platform-tools',
        'emulator',
        'platforms;android-35',
        'system-images;android-35;default;x86_64'
    )
    if ($IncludeApi36) {
        $sdkPackages += 'platforms;android-36', 'system-images;android-36;default;x86_64'
    }

    1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$SdkRoot" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK licence acceptance failed.' }
    & $sdkManager "--sdk_root=$SdkRoot" --install @sdkPackages
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }

    Write-Host "Android PoC SDK installed at $SdkRoot"
    Write-Host 'Set ANDROID_HOME and ANDROID_SDK_ROOT to this path for the current shell before building.'
}
finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
