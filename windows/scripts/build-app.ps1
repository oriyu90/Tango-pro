[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $SdkRoot) { throw 'Set ANDROID_HOME or pass -SdkRoot.' }
$env:ANDROID_HOME = [IO.Path]::GetFullPath($SdkRoot)
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Push-Location $repo
try {
    & .\gradlew.bat test lint stageDebugApk --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
    & powershell -ExecutionPolicy Bypass -File .\windows\scripts\inspect-app.ps1 -ApkPath .\dist-android\Tango-pro-2.1.0-android-debug.apk
    if ($LASTEXITCODE -ne 0) { throw "APK inspection failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
