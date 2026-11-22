#Requires -Version 7
# Boots an Android emulator for testing, or reuses it if it's already running.
#
#   -Create   Creates the AVD first if it doesn't exist: installs the system image if
#             missing, creates a Pixel 6 AVD with avdmanager and applies $ConfigOverrides.
#   -Install  Builds the debug APK, installs it on that emulator only (never on a connected
#             phone) and launches the app.
#   -ColdBoot Boots without loading the quick-boot snapshot.
param(
    [string]$Avd = 'gelli_test_api35',
    [switch]$Create,
    [switch]$Install,
    [switch]$ColdBoot,
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot

# The AVD that -Create builds. The default image is AOSP with test keys and no Play Store, so
# `adb root` works on it. ARM hosts such as Apple Silicon can't run x86_64 images.
$ApiLevel = 35
$Abi = if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64') { 'arm64-v8a' } else { 'x86_64' }
$SystemImage = "system-images;android-$ApiLevel;default;$Abi"
$DeviceProfile = 'pixel_6'

# Settings that differ from, or are pinned against changes to, avdmanager's defaults for the
# device profile.
$ConfigOverrides = [ordered]@{
    # avdmanager writes "no", which makes the emulator ignore the host keyboard.
    'hw.keyboard'             = 'yes'
    'hw.ramSize'              = '2G'
    'disk.dataPartition.size' = '10G'
}

$exe = if ($IsWindows) { '.exe' } else { '' }
$bat = if ($IsWindows) { '.bat' } else { '' }

# A shell started before the SDK variables were set won't have them, so fall back to the
# user-level values (Windows only) and then to local.properties.
function Get-EnvSetting([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $value -and $IsWindows) { $value = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $value
}

# The build and the SDK command-line tools need a JDK that Gradle supports, and JDK 27 isn't
# one. Prefer a JDK 21 that Gradle provisioned, then fall back to JAVA_HOME.
function Find-Jdk {
    $jdk = Get-ChildItem (Join-Path $HOME '.gradle/jdks') -Directory -Filter '*-21-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { return $jdk.FullName }
    if ($env:JAVA_HOME) { return $env:JAVA_HOME }
    throw 'No JDK found. Install JDK 21 or set JAVA_HOME.'
}

function Find-SdkTool([string]$Sdk, [string]$Name) {
    $tool = Get-ChildItem (Join-Path $Sdk 'cmdline-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { $_.Name -ne 'latest' } |
        ForEach-Object { Join-Path $_.FullName "bin/$Name$bat" } |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $tool) {
        throw "$Name not found. Install 'Android SDK Command-line Tools' from the SDK Manager."
    }
    return $tool
}

function Get-EmulatorSerial([string]$Name) {
    $serials = & $adb devices | Select-String '^(emulator-\d+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value }
    foreach ($serial in $serials) {
        $avdName = (& $adb -s $serial emu avd name 2>$null | Select-Object -First 1)
        if ($avdName -and $avdName.Trim() -eq $Name) { return $serial }
    }
    return $null
}

function Set-AvdConfig([string]$Path, [System.Collections.IDictionary]$Settings) {
    $lines = [Collections.Generic.List[string]]([IO.File]::ReadAllLines($Path))
    foreach ($key in $Settings.Keys) {
        $line = "$key=$($Settings[$key])"
        $index = $lines.FindIndex({ param($l) $l -match "^$([regex]::Escape($key))\s*=" })
        if ($index -ge 0) { $lines[$index] = $line } else { $lines.Add($line) }
    }
    [IO.File]::WriteAllText($Path, ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
}

function New-TestAvd {
    $javaHome = Find-Jdk
    $sdkmanager = Find-SdkTool $sdk 'sdkmanager'
    $avdmanager = Find-SdkTool $sdk 'avdmanager'
    $env:JAVA_HOME = $javaHome

    $imageDir = Join-Path $sdk ($SystemImage -replace ';', '/')
    if (-not (Test-Path $imageDir)) {
        Write-Host "Installing $SystemImage..."
        # Left interactive so you can read and accept the SDK licenses yourself.
        & $sdkmanager --install $SystemImage
        if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }
    }

    Write-Host "Creating $Avd from $SystemImage ($DeviceProfile)..."
    # avdmanager asks whether to create a custom hardware profile; answer no.
    'no' | & $avdmanager create avd -n $Avd -k $SystemImage -d $DeviceProfile 2>&1 |
        Where-Object { $_ -notmatch 'Loading local repository|inconsistent location|Skipping duplicate' }
    if ($LASTEXITCODE -ne 0) { throw "avdmanager failed with exit code $LASTEXITCODE" }

    $avdDir = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $HOME '.android/avd' }
    Set-AvdConfig (Join-Path $avdDir "$Avd.avd/config.ini") $ConfigOverrides
    Write-Host "Created $Avd" -ForegroundColor Green
}

$sdk = Get-EnvSetting 'ANDROID_HOME'
if (-not $sdk -or -not (Test-Path "$sdk/emulator/emulator$exe")) {
    $line = Get-Content "$root/local.properties" -ErrorAction SilentlyContinue | Where-Object { $_ -match '^sdk\.dir=' }
    if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\(.)', '$1' }
}
if (-not $sdk -or -not (Test-Path "$sdk/emulator/emulator$exe")) {
    throw 'Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties.'
}

$emulator = "$sdk/emulator/emulator$exe"
$adb = "$sdk/platform-tools/adb$exe"

# Restore the caller's environment afterwards, since running a script with `.\` shares it.
$savedEnv = @{}
$savedEnv['ANDROID_AVD_HOME'] = $env:ANDROID_AVD_HOME
$savedEnv['JAVA_HOME'] = $env:JAVA_HOME

try {
    $avdHome = Get-EnvSetting 'ANDROID_AVD_HOME'
    if ($avdHome) { $env:ANDROID_AVD_HOME = $avdHome }

    & $adb start-server | Out-Null
    $serial = Get-EmulatorSerial $Avd

    if ($serial) {
        Write-Host "$Avd is already running as $serial"
    } else {
        $available = @(& $emulator -list-avds 2>$null)
        if ($available -notcontains $Avd) {
            if (-not $Create) {
                throw "AVD '$Avd' not found. Run with -Create to create it. Available: $($available -join ', ')"
            }
            New-TestAvd
        } elseif ($Create) {
            Write-Host "$Avd already exists"
        }

        $arguments = @('-avd', $Avd, '-netdelay', 'none', '-netspeed', 'full')
        if ($ColdBoot) { $arguments += '-no-snapshot-load' }

        # Start-Process detaches the emulator so it keeps running after this script exits.
        $process = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru

        Write-Host "Booting $Avd..."
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        while (-not $serial) {
            if ($process.HasExited) { throw "Emulator exited with code $($process.ExitCode)" }
            if ((Get-Date) -gt $deadline) { throw "Timed out waiting for $Avd to connect to adb" }
            Start-Sleep -Seconds 2
            $serial = Get-EmulatorSerial $Avd
        }

        while ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -ne '1') {
            if ($process.HasExited) { throw "Emulator exited with code $($process.ExitCode)" }
            if ((Get-Date) -gt $deadline) { throw "Timed out waiting for $Avd to finish booting" }
            Start-Sleep -Seconds 2
        }
        Write-Host "$Avd booted as $serial" -ForegroundColor Green
    }

    if ($Install) {
        $env:JAVA_HOME = Find-Jdk
        Push-Location $root
        try {
            # assembleDebug rather than installDebug: installDebug installs on every connected
            # device, including a phone plugged in over USB.
            & "./gradlew$bat" assembleDebug
            if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        } finally {
            Pop-Location
        }

        $apk = "$root/app/build/outputs/apk/debug/app-debug.apk"
        $package = (Get-Content "$root/app/build/outputs/apk/debug/output-metadata.json" | ConvertFrom-Json).applicationId

        & $adb -s $serial install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "adb install failed with exit code $LASTEXITCODE" }

        & $adb -s $serial shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $package | Out-Null
        Write-Host "Installed and launched $package on $serial" -ForegroundColor Green
    }
} finally {
    foreach ($name in $savedEnv.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process')
    }
}
