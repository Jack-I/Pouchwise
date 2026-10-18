# Builds the APK from PowerShell without Android Studio.
# Usage:  .\build.ps1            -> debug APK
#         .\build.ps1 release    -> release APK (signed with keystore.properties if present, otherwise the debug key)
#         .\build.ps1 test       -> JVM unit tests
#         .\build.ps1 install    -> build debug APK and install it on a USB-connected phone (adb)
#
# JAVA_HOME and ANDROID_HOME are respected when set. Otherwise the script looks for a JDK under
# %LOCALAPPDATA%\Android\jdk\<jdk-dir> and the SDK under %LOCALAPPDATA%\Android\Sdk.
param([string]$Task = "debug")

$ErrorActionPreference = "Stop"
$androidRoot = Join-Path $env:LOCALAPPDATA "Android"

if (-not $env:JAVA_HOME) {
    $jdkDir = Join-Path $androidRoot "jdk"
    $jdk = if (Test-Path $jdkDir) { Get-ChildItem $jdkDir -Directory | Select-Object -First 1 } else { $null }
    if (-not $jdk) { throw "JDK 17 not found: set JAVA_HOME or unpack a JDK under $jdkDir (see README, Build)." }
    $env:JAVA_HOME = $jdk.FullName
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $androidRoot "Sdk"
}
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# Some Windows setups cannot connect over AF_UNIX sockets, which JDK 17 uses for its internal pipes;
# Gradle then fails with "Unable to establish loopback connection". Pointing the JDK at a missing
# socket directory makes it fall back to TCP loopback. JAVA_TOOL_OPTIONS reaches every child JVM.
if (-not $env:JAVA_TOOL_OPTIONS) {
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\__no_unix_sockets__"
}

Set-Location $PSScriptRoot
switch ($Task) {
    "debug" {
        .\gradlew.bat --console=plain assembleDebug
        if ($LASTEXITCODE -eq 0) { Get-Item app\build\outputs\apk\debug\app-debug.apk | Select-Object FullName, Length }
    }
    "release" {
        .\gradlew.bat --console=plain assembleRelease
        if ($LASTEXITCODE -eq 0) { Get-Item app\build\outputs\apk\release\app-release.apk | Select-Object FullName, Length }
    }
    "test"    { .\gradlew.bat --console=plain testDebugUnitTest }
    "install" { .\gradlew.bat --console=plain installDebug }
    default   { .\gradlew.bat --console=plain $Task }
}
exit $LASTEXITCODE
