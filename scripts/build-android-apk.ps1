$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root 'frontend'
$android = Join-Path $root 'android'

Write-Host 'Building the React checkout...'
npm --prefix $frontend run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

Write-Host 'Building the Android APK...'
& (Join-Path $android 'gradlew.bat') -p $android :app:assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }

$apk = Join-Path $android 'app\build\outputs\apk\debug\app-debug.apk'
$installable = Join-Path $root 'GuardPay-debug.apk'
Copy-Item -LiteralPath $apk -Destination $installable -Force
Write-Host "APK ready: $installable"
Write-Host "Gradle output: $apk"
