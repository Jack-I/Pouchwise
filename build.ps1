# Builds the APK without Android Studio.
# Usage:  .\build.ps1            -> debug APK
#         .\build.ps1 release    -> release APK (signed with keystore.properties if present, otherwise the debug key)
#         .\build.ps1 test       -> JVM unit tests
#         .\build.ps1 install    -> build debug APK and install it on a USB-connected phone (adb)
param([string]$Task = "debug")

$ErrorActionPreference = "Stop"
$androidRoot = Join-Path $env:LOCALAPPDATA "Android"
$jdk = Get-ChildItem (Join-Path $androidRoot "jdk") -Directory | Select-Object -First 1
if (-not $jdk) { throw "JDK not found under $androidRoot\jdk - see README (Toolchain)." }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $androidRoot "Sdk"
$env:Path = "$($jdk.FullName)\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# On this machine (Windows on ARM) Java's AF_UNIX connect fails with "Invalid argument", which surfaces in
# Gradle as "Unable to establish loopback connection". Pointing the JDK at a non-existent socket directory
# makes it fall back to TCP loopback. JAVA_TOOL_OPTIONS reaches every child JVM (daemon, Kotlin, workers).
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\__no_unix_sockets__"

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
