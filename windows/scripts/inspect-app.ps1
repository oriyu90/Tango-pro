[CmdletBinding()]
param(
    [string]$ProjectRoot,
    [string]$ApkPath,
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ProjectRoot) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
}

function Get-RequiredMatch {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [string]$Pattern,
        [Parameter(Mandatory)] [string]$Name
    )

    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $match.Success) {
        throw "Could not read $Name from app/build.gradle.kts."
    }
    return $match.Groups[1].Value
}

function Find-Aapt {
    param([Parameter(Mandatory)] [string]$Root)

    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) | Where-Object { $_ }
    foreach ($sdkRoot in $sdkCandidates) {
        $buildTools = Join-Path $sdkRoot 'build-tools'
        if (Test-Path -LiteralPath $buildTools) {
            $aapt = Get-ChildItem -LiteralPath $buildTools -Directory |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
                Where-Object { Test-Path -LiteralPath $_ } |
                Select-Object -First 1
            if ($aapt) { return $aapt }
        }
    }
    return $null
}

function Find-ApkSigner {
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) | Where-Object { $_ }
    foreach ($sdkRoot in $sdkCandidates) {
        $buildTools = Join-Path $sdkRoot 'build-tools'
        if (Test-Path -LiteralPath $buildTools) {
            $apkSigner = Get-ChildItem -LiteralPath $buildTools -Directory |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
                Where-Object { Test-Path -LiteralPath $_ } |
                Select-Object -First 1
            if ($apkSigner) { return $apkSigner }
        }
    }
    return $null
}

$buildFile = Join-Path $ProjectRoot 'app\\build.gradle.kts'
if (-not (Test-Path -LiteralPath $buildFile)) {
    throw "Android build file not found: $buildFile"
}

$buildText = Get-Content -Raw -LiteralPath $buildFile
$versionVariable = Get-RequiredMatch $buildText 'val\s+appVersionName\s*=\s*"([^"]+)"' 'appVersionName'
$compileSdkMatch = [regex]::Match(
    $buildText,
    'compileSdk\s*\{\s*version\s*=\s*release\((\d+)\)\s*\{\s*minorApiLevel\s*=\s*(\d+)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)
if (-not $compileSdkMatch.Success) {
    throw 'Could not read compileSdk from app/build.gradle.kts.'
}

$metadata = [ordered]@{
    schema = 1
    packageName = Get-RequiredMatch $buildText 'applicationId\s*=\s*"([^"]+)"' 'applicationId'
    namespace = Get-RequiredMatch $buildText 'namespace\s*=\s*"([^"]+)"' 'namespace'
    versionName = $versionVariable
    versionCode = [int](Get-RequiredMatch $buildText 'versionCode\s*=\s*(\d+)' 'versionCode')
    minSdk = [int](Get-RequiredMatch $buildText 'minSdk\s*=\s*(\d+)' 'minSdk')
    targetSdk = [int](Get-RequiredMatch $buildText 'targetSdk\s*=\s*(\d+)' 'targetSdk')
    compileSdk = "$($compileSdkMatch.Groups[1].Value).$($compileSdkMatch.Groups[2].Value)"
}

if ($ApkPath) {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    $aapt = Find-Aapt $ProjectRoot
    if (-not $aapt) {
        throw 'APK inspection requires aapt.exe from an Android SDK. Set ANDROID_HOME or ANDROID_SDK_ROOT.'
    }

    $badging = & $aapt dump badging $resolvedApk
    if ($LASTEXITCODE -ne 0) { throw "aapt could not inspect $resolvedApk" }
    $packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
    $apkPackage = Get-RequiredMatch $packageLine "name='([^']+)'" 'APK package name'
    $apkVersionCode = [int](Get-RequiredMatch $packageLine "versionCode='(\d+)'" 'APK versionCode')
    $apkVersionName = Get-RequiredMatch $packageLine "versionName='([^']+)'" 'APK versionName'
    if ($apkPackage -ne $metadata.packageName -or $apkVersionCode -ne $metadata.versionCode -or $apkVersionName -ne $metadata.versionName) {
        throw 'APK identity does not match app/build.gradle.kts; app.lock.json was not updated.'
    }
    $projectUri = [Uri]((Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd('\\') + '\\')
    $apkUri = [Uri]$resolvedApk
    $metadata.apk = [Uri]::UnescapeDataString($projectUri.MakeRelativeUri($apkUri).ToString()).Replace('\\', '/')
    $metadata.sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToLowerInvariant()
    $apkSigner = Find-ApkSigner
    if (-not $apkSigner) {
        throw 'APK inspection requires apksigner.bat from an Android SDK. Set ANDROID_HOME or ANDROID_SDK_ROOT.'
    }
    $certificateOutput = & $apkSigner verify --verbose --print-certs $resolvedApk
    if ($LASTEXITCODE -ne 0) { throw "apksigner could not verify $resolvedApk" }
    $certificateLine = $certificateOutput | Where-Object { $_ -match 'certificate SHA-256 digest:' } | Select-Object -First 1
    $metadata.certificateSha256 = (Get-RequiredMatch $certificateLine 'digest:\s*([A-Fa-f0-9]{64})' 'APK certificate SHA-256').ToLowerInvariant()
}

if (-not $OutputPath) {
    $OutputPath = Join-Path $ProjectRoot 'windows\\runtime\\app.lock.json'
}
$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$json = $metadata | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($OutputPath, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $(Resolve-Path -LiteralPath $OutputPath)"
