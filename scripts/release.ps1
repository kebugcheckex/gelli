# Builds a signed release APK. Prompts for the keystore password so it never lands in
# shell history or persistent environment variables.
param(
    [string]$KeyAlias = 'gelli'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot

if (-not (Test-Path "$root\store.jks")) {
    throw "Keystore not found at $root\store.jks"
}

# The default JDK 27 breaks the build, so use the JDK 21 that Gradle provisioned.
$jdk = Join-Path $HOME '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
if (-not (Test-Path $jdk)) {
    throw "JDK 21 not found at $jdk"
}

$secure = Read-Host -Prompt 'Keystore password' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $secure).Password
if (-not $password) {
    throw 'Password is empty'
}

$saved = @{}
foreach ($name in 'JAVA_HOME', 'STORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD') {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

Push-Location $root
try {
    $env:JAVA_HOME = $jdk
    $env:STORE_PASSWORD = $password
    $env:KEY_ALIAS = $KeyAlias
    # PKCS12 keystores use the store password for the key as well.
    $env:KEY_PASSWORD = $password

    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
    $password = $null
}

$versionName = Select-String -Path "$root\app\build.gradle" -Pattern "versionName\s*=\s*'([^']+)'" |
    Select-Object -First 1 | ForEach-Object { $_.Matches[0].Groups[1].Value }
if (-not $versionName) {
    throw 'versionName not found in app/build.gradle'
}

$commit = git -C $root rev-parse --short HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'Could not read the commit hash'
}
# Flag builds with uncommitted changes, since the hash alone would not describe them.
if (git -C $root status --porcelain --untracked-files=no) {
    $commit += '-dirty'
}

$apk = "$root\app\build\outputs\apk\release\app-release.apk"
$target = "$root\app\build\outputs\apk\release\app-release-$versionName-$commit.apk"
Move-Item -Path $apk -Destination $target -Force
Write-Host "`nRelease APK: $target" -ForegroundColor Green
