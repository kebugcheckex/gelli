# Boots an Android emulator for testing, or reuses it if it's already running.
# With -Install, also builds the debug APK, installs it on that emulator only (never on a
# connected phone) and launches the app.
param(
    [string]$Avd = 'gelli_test_api35',
    [switch]$Install,
    [switch]$ColdBoot,
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot

# A shell started before the SDK variables were set won't have them, so fall back to the
# user-level values and then to local.properties.
function Get-SdkSetting([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $value
}

$sdk = Get-SdkSetting 'ANDROID_HOME'
if (-not $sdk -or -not (Test-Path "$sdk\emulator\emulator.exe")) {
    $line = Get-Content "$root\local.properties" -ErrorAction SilentlyContinue | Where-Object { $_ -match '^sdk\.dir=' }
    if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\(.)', '$1' }
}
if (-not $sdk -or -not (Test-Path "$sdk\emulator\emulator.exe")) {
    throw 'Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties.'
}

$emulator = "$sdk\emulator\emulator.exe"
$adb = "$sdk\platform-tools\adb.exe"
$avdHome = Get-SdkSetting 'ANDROID_AVD_HOME'

function Get-EmulatorSerial([string]$Name) {
    $serials = & $adb devices | Select-String '^(emulator-\d+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value }
    foreach ($serial in $serials) {
        $avdName = (& $adb -s $serial emu avd name 2>$null | Select-Object -First 1)
        if ($avdName -and $avdName.Trim() -eq $Name) { return $serial }
    }
    return $null
}

& $adb start-server | Out-Null
$serial = Get-EmulatorSerial $Avd

if ($serial) {
    Write-Host "$Avd is already running as $serial"
} else {
    $savedAvdHome = $env:ANDROID_AVD_HOME
    try {
        if ($avdHome) { $env:ANDROID_AVD_HOME = $avdHome }

        $available = & $emulator -list-avds 2>$null
        if ($available -notcontains $Avd) {
            throw "AVD '$Avd' not found. Available: $($available -join ', ')"
        }

        $arguments = @('-avd', $Avd, '-netdelay', 'none', '-netspeed', 'full')
        if ($ColdBoot) { $arguments += '-no-snapshot-load' }

        # Start-Process detaches the emulator so it keeps running after this script exits.
        $process = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru
    } finally {
        $env:ANDROID_AVD_HOME = $savedAvdHome
    }

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
    # The default JDK 27 breaks the build, so use the JDK 21 that Gradle provisioned.
    $jdk = Join-Path $HOME '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
    if (-not (Test-Path $jdk)) {
        throw "JDK 21 not found at $jdk"
    }

    $savedJavaHome = $env:JAVA_HOME
    Push-Location $root
    try {
        $env:JAVA_HOME = $jdk
        # assembleDebug rather than installDebug: installDebug installs on every connected
        # device, including a phone plugged in over USB.
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $savedJavaHome
    }

    $apk = "$root\app\build\outputs\apk\debug\app-debug.apk"
    $package = (Get-Content "$root\app\build\outputs\apk\debug\output-metadata.json" | ConvertFrom-Json).applicationId

    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) {
        throw "adb install failed with exit code $LASTEXITCODE"
    }

    & $adb -s $serial shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $package | Out-Null
    Write-Host "Installed and launched $package on $serial" -ForegroundColor Green
}
