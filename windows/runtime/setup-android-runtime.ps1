[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$InstallRoot,
    [Parameter(Mandatory)]
    [string]$UserdataRoot,
    [string]$ExistingSdkRoot,
    [Int64]$TestAvailableBytes = -1,
    [string]$TestBootstrapArchive,
    [switch]$TestSkipDialog
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$UserdataRoot = [IO.Path]::GetFullPath($UserdataRoot)
$lockPath = Join-Path $InstallRoot 'sdk-bootstrap.lock.json'
if (-not (Test-Path -LiteralPath $lockPath -PathType Leaf)) { throw "Missing SDK bootstrap lock: $lockPath" }
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
if ($lock.schema -ne 1) { throw 'Unsupported SDK bootstrap lock schema.' }
$uri = [Uri]$lock.commandLineTools.sourceUrl
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'dl.google.com') { throw 'SDK bootstrap source must be the official dl.google.com HTTPS endpoint.' }

$runtimeRoot = Join-Path $UserdataRoot 'runtime'
$statePath = Join-Path $runtimeRoot 'android-runtime-state.json'

if (($TestAvailableBytes -ge 0 -or $TestBootstrapArchive -or $TestSkipDialog) -and $env:TANGO_RUNTIME_TEST_MODE -ne '1') {
    throw 'Runtime setup test options require TANGO_RUNTIME_TEST_MODE=1.'
}

function Write-RuntimeState([string]$State, [string]$SdkRoot, [string]$Detail) {
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    $value = [ordered]@{
        schema = 1
        state = $State
        sdkRoot = $SdkRoot
        detail = $Detail
    } | ConvertTo-Json
    $part = "$statePath.part"
    [IO.File]::WriteAllText($part, $value + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $part -Destination $statePath -Force
}

function Confirm-RuntimeSetup {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'Android Runtime Setup'
    $form.StartPosition = 'CenterScreen'
    $form.ClientSize = New-Object System.Drawing.Size(610, 265)
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false

    $message = New-Object System.Windows.Forms.Label
    $message.AutoSize = $false
    $message.Location = New-Object System.Drawing.Point(18, 16)
    $message.Size = New-Object System.Drawing.Size(574, 165)
    $message.Text = "Tango Pro は Google Android SDK、Android Emulator、Android 15 API 35 default x86_64 system image を製品に同梱しません。`r`n`r`nセットアップを選ぶと、Google の公式配布元からあなたのユーザー領域に直接ダウンロードし、SDK Manager のライセンス画面を表示します。ライセンスへの同意は、必ずご本人が行ってください。`r`n`r`n既存の Android SDK を検出した場合も、その SDK には書き込みません。Tango Pro 専用 AVD だけを %LOCALAPPDATA%\TangoPro\runtime に作成します。"
    $form.Controls.Add($message)

    $terms = New-Object System.Windows.Forms.Button
    $terms.Text = '利用規約を確認'
    $terms.Location = New-Object System.Drawing.Point(18, 205)
    $terms.Size = New-Object System.Drawing.Size(145, 34)
    $terms.Add_Click({ Start-Process 'https://developer.android.com/studio' })
    $form.Controls.Add($terms)

    $setup = New-Object System.Windows.Forms.Button
    $setup.Text = 'セットアップ'
    $setup.Location = New-Object System.Drawing.Point(336, 205)
    $setup.Size = New-Object System.Drawing.Size(110, 34)
    $setup.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $form.AcceptButton = $setup
    $form.Controls.Add($setup)

    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'キャンセル'
    $cancel.Location = New-Object System.Drawing.Point(462, 205)
    $cancel.Size = New-Object System.Drawing.Size(130, 34)
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $form.CancelButton = $cancel
    $form.Controls.Add($cancel)

    return $form.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK
}

function Require-ExactAvailablePackage([string]$Listing, [string]$PackageId, [string]$Revision) {
    $quotedId = [regex]::Escape($PackageId)
    $quotedRevision = [regex]::Escape($Revision)
    if ($Listing -notmatch "(?m)^\s*$quotedId\s*\|\s*$quotedRevision\s*\|") {
        throw "RUNTIME_PACKAGE_UNAVAILABLE: $PackageId revision $Revision is not listed by the official SDK Manager."
    }
}

function Require-FreeSpace([Int64]$RequiredBytes) {
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    $available = if ($TestAvailableBytes -ge 0) {
        $TestAvailableBytes
    } else {
        (Get-Item -LiteralPath $runtimeRoot).PSDrive.Free
    }
    if ($available -lt $RequiredBytes) {
        Write-RuntimeState 'BROKEN' $null "INSUFFICIENT_DISK_SPACE: required=$RequiredBytes available=$available"
        throw 'INSUFFICIENT_DISK_SPACE'
    }
}

function Require-InstalledRevision([string]$SdkRoot, [string]$RelativePath, [string]$PackageId, [string]$Revision) {
    $properties = Join-Path $SdkRoot (Join-Path $RelativePath 'source.properties')
    if (-not (Test-Path -LiteralPath $properties -PathType Leaf)) { throw "RUNTIME_PACKAGE_UNAVAILABLE: $properties" }
    $revisionLine = Get-Content -LiteralPath $properties | Where-Object { $_ -like 'Pkg.Revision=*' } | Select-Object -First 1
    if (-not $revisionLine) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: Pkg.Revision missing at $properties" }
    $actual = $revisionLine.Substring(13)
    if ($actual -ne $Revision) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: expected $Revision, found $actual at $properties" }
    $pathLine = Get-Content -LiteralPath $properties | Where-Object { $_ -like 'Pkg.Path=*' } | Select-Object -First 1
    if ($pathLine -and $pathLine.Substring(9) -ne $PackageId) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: expected package $PackageId, found $($pathLine.Substring(9))" }
    $packageXml = Join-Path $SdkRoot (Join-Path $RelativePath 'package.xml')
    if (-not (Test-Path -LiteralPath $packageXml -PathType Leaf)) { throw "RUNTIME_PACKAGE_UNAVAILABLE: $packageXml" }
    [xml]$xml = Get-Content -LiteralPath $packageXml -Raw
    $package = $xml.SelectSingleNode('//*[local-name()="localPackage"]')
    if (-not $package -or $package.path -ne $PackageId) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml package ID does not match $PackageId" }
    $major = $package.SelectSingleNode('./*[local-name()="revision"]/*[local-name()="major"]')
    $minor = $package.SelectSingleNode('./*[local-name()="revision"]/*[local-name()="minor"]')
    $micro = $package.SelectSingleNode('./*[local-name()="revision"]/*[local-name()="micro"]')
    if (-not $major) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml revision is missing at $packageXml" }
    $xmlRevision = [string]$major.InnerText
    if ($minor) { $xmlRevision += '.' + $minor.InnerText }
    if ($micro) { $xmlRevision += '.' + $micro.InnerText }
    if ($xmlRevision -ne $Revision) { throw "RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml expected $Revision, found $xmlRevision at $packageXml" }
}

function Set-AvdValue([System.Collections.Generic.List[string]]$Lines, [string]$Key, [string]$Value) {
    $prefix = "$Key="
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i].StartsWith($prefix, [StringComparison]::Ordinal)) {
            $Lines[$i] = "$prefix$Value"
            return
        }
    }
    $Lines.Add("$prefix$Value")
}

try {
    if (-not $TestSkipDialog -and -not (Confirm-RuntimeSetup)) {
        Write-RuntimeState 'NOT_INSTALLED' $null 'The user cancelled Android Runtime Setup.'
        exit 2
    }

    $sdkRoot = $ExistingSdkRoot
    if ($sdkRoot) {
        $sdkRoot = [IO.Path]::GetFullPath($sdkRoot)
        Require-FreeSpace ([Int64]$lock.storageBudget.minimumAvdRepairFreeBytes)
        Write-RuntimeState 'CREATING_AVD' $sdkRoot 'Using an existing SDK read-only.'
    } else {
        $sdkRoot = Join-Path $runtimeRoot 'android-sdk'
        $downloadRoot = Join-Path $runtimeRoot 'downloads'
        $archive = Join-Path $downloadRoot 'commandlinetools-win.zip'
        $part = "$archive.part"
        New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
        Require-FreeSpace ([Int64]$lock.storageBudget.minimumFreshInstallFreeBytes)
        Write-RuntimeState 'DOWNLOADING_BOOTSTRAP' $sdkRoot 'Downloading the official Android Command-line Tools.'
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
        if ($TestBootstrapArchive) {
            if (-not (Test-Path -LiteralPath $TestBootstrapArchive -PathType Leaf)) { throw "Test bootstrap archive not found: $TestBootstrapArchive" }
            Copy-Item -LiteralPath $TestBootstrapArchive -Destination $part
        } else {
            Invoke-WebRequest -Uri $lock.commandLineTools.sourceUrl -OutFile $part
        }
        $actualHash = (Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $lock.commandLineTools.sha256.ToLowerInvariant()) {
            Remove-Item -LiteralPath $part -Force
            Write-RuntimeState 'BROKEN' $sdkRoot 'RUNTIME_BOOTSTRAP_HASH_MISMATCH'
            throw 'RUNTIME_BOOTSTRAP_HASH_MISMATCH'
        }
        Move-Item -LiteralPath $part -Destination $archive -Force
        $extract = Join-Path $downloadRoot 'commandlinetools-unpacked'
        if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
        Expand-Archive -LiteralPath $archive -DestinationPath $extract
        $source = Join-Path $extract 'cmdline-tools'
        $destination = Join-Path $sdkRoot 'cmdline-tools\latest'
        if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'The official Command-line Tools archive has an unexpected layout.' }
        if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Move-Item -LiteralPath $source -Destination $destination
    }

    $sdkmanager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
    $avdmanager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkmanager -PathType Leaf)) { throw "RUNTIME_PACKAGE_UNAVAILABLE: $sdkmanager" }
    if (-not $ExistingSdkRoot) {
        Write-RuntimeState 'LICENSE_REQUIRED' $sdkRoot 'Waiting for the user to accept SDK licenses in SDK Manager.'
        $licenseProcess = Start-Process -FilePath $sdkmanager -ArgumentList "--sdk_root=$sdkRoot", '--licenses' -Wait -PassThru
        if ($licenseProcess.ExitCode -ne 0) { throw 'RUNTIME_LICENSE_REQUIRED' }
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $listing = (& $sdkmanager "--sdk_root=$sdkRoot" --list 2>&1 | Out-String)
    $ErrorActionPreference = $previousErrorActionPreference
    if ($LASTEXITCODE -ne 0) { throw 'RUNTIME_PACKAGE_UNAVAILABLE: sdkmanager --list failed' }
    $packages = @($lock.packages.emulator, $lock.packages.platformTools, $lock.packages.buildTools, $lock.packages.systemImage)
    foreach ($package in $packages) { Require-ExactAvailablePackage $listing $package.id $package.revision }
    if (-not $ExistingSdkRoot) {
        Write-RuntimeState 'INSTALLING_PACKAGES' $sdkRoot 'Installing only the locked Android SDK packages.'
        & $sdkmanager "--sdk_root=$sdkRoot" --install $packages.id
        if ($LASTEXITCODE -ne 0) { throw 'RUNTIME_PACKAGE_UNAVAILABLE' }
    }

    Require-InstalledRevision $sdkRoot 'emulator' $lock.packages.emulator.id $lock.packages.emulator.revision
    Require-InstalledRevision $sdkRoot 'platform-tools' $lock.packages.platformTools.id $lock.packages.platformTools.revision
    Require-InstalledRevision $sdkRoot "build-tools\$($lock.packages.buildTools.revision)" $lock.packages.buildTools.id $lock.packages.buildTools.revision
    Require-InstalledRevision $sdkRoot 'system-images\android-35\default\x86_64' $lock.packages.systemImage.id $lock.packages.systemImage.revision

    Write-RuntimeState 'CREATING_AVD' $sdkRoot 'Creating or verifying the dedicated Tango Pro AVD.'
    $avdHome = Join-Path $runtimeRoot 'avd'
    $avdPath = Join-Path $avdHome "$($lock.avd.name).avd"
    New-Item -ItemType Directory -Force -Path $avdHome | Out-Null
    if (-not (Test-Path -LiteralPath $avdPath -PathType Container)) {
        $env:ANDROID_AVD_HOME = $avdHome
        $env:ANDROID_USER_HOME = Join-Path $runtimeRoot 'android-user'
        "no`n" | & $avdmanager create avd --name $lock.avd.name --package $lock.packages.systemImage.id --path $avdPath --device $lock.avd.deviceProfile
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the Tango Pro AVD.' }
    }
    $configPath = Join-Path $avdPath 'config.ini'
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw "Missing AVD configuration: $configPath" }
    $config = [System.Collections.Generic.List[string]]::new([string[]](Get-Content -LiteralPath $configPath))
    Set-AvdValue $config 'hw.ramSize' ([string]$lock.avd.ramMb)
    Set-AvdValue $config 'hw.cpu.ncore' ([string]$lock.avd.cpuCount)
    Set-AvdValue $config 'hw.gpu.mode' $lock.avd.gpu
    Set-AvdValue $config 'fastboot.forceColdBoot' 'yes'
    Set-AvdValue $config 'fastboot.forceFastBoot' 'no'
    Set-AvdValue $config 'PlayStore.enabled' 'false'
    [IO.File]::WriteAllLines($configPath, $config, [Text.UTF8Encoding]::new($false))

    Write-RuntimeState 'BOOTSTRAPPING_GUEST' $sdkRoot 'Android runtime is installed; Tango Pro will perform first boot verification.'
    exit 0
} catch {
    $detail = $_.Exception.Message
    if ($detail -eq 'RUNTIME_LICENSE_REQUIRED') {
        Write-RuntimeState 'LICENSE_REQUIRED' $sdkRoot $detail
    } else {
        Write-RuntimeState 'BROKEN' $sdkRoot $detail
    }
    if ($env:TANGO_RUNTIME_TEST_MODE -ne '1') {
        [System.Windows.Forms.MessageBox]::Show("Android Runtime Setup に失敗しました。`r`n$detail", 'Android Runtime Setup', 'OK', 'Error') | Out-Null
    }
    throw
}
