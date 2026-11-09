# Builds a signed release APK. Prompts for the keystore password so it never lands in
# shell history or persistent environment variables.
param(
    [string]$KeyAlias = 'gelli'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path 'store.jks')) {
    throw "Keystore not found at $PSScriptRoot\store.jks"
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
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
    $password = $null
}

$apk = Get-ChildItem 'app\build\outputs\apk\release\*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "`nRelease APK: $($apk.FullName)" -ForegroundColor Green
