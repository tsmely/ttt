param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not $Jdk) { $Jdk = $env:JAVA_HOME }
if (-not $OutDir) { $OutDir = Join-Path $root "dist" }

$bt = Join-Path $Sdk "build-tools\35.0.0"
$platform = Join-Path $Sdk "platforms\android-35\android.jar"
$manifests = Join-Path $root "app\src\main\AndroidManifest.xml"
$res = Join-Path $root "app\src\main\res"
$src = Join-Path $root "app\src\main\java"
$keyStore = Join-Path $root "keystore\qingping.jks"
$build = Join-Path $root "build\manual"

foreach ($p in @($bt, $platform, $manifests, $res, $src)) {
    if (-not (Test-Path $p)) { throw "Missing: $p" }
}
if (-not (Test-Path (Join-Path $Jdk "bin\javac.exe"))) { throw "Bad JDK: $Jdk" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $build | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $build "gen") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $build "classes") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $build "dex") | Out-Null

Write-Host "==> aapt2 compile"
& (Join-Path $bt "aapt2.exe") compile --dir $res -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "==> aapt2 link"
& (Join-Path $bt "aapt2.exe") link `
    -o (Join-Path $build "app-unsigned.apk") `
    -I $platform `
    --manifest $manifests `
    --java (Join-Path $build "gen") `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name "1.0.0" `
    --auto-add-overlay `
    (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "==> javac"
$javaFiles = @()
$javaFiles += Get-ChildItem -Recurse -Filter *.java $src | ForEach-Object { $_.FullName }
$javaFiles += Get-ChildItem -Recurse -Filter *.java (Join-Path $build "gen") | ForEach-Object { $_.FullName }

& (Join-Path $Jdk "bin\javac.exe") `
    -encoding UTF-8 -source 11 -target 11 -nowarn `
    -classpath $platform `
    -d (Join-Path $build "classes") `
    $javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "==> d8"
$classFiles = @()
$classFiles += Get-ChildItem -Recurse -Filter *.class (Join-Path $build "classes") | ForEach-Object { $_.FullName }

& (Join-Path $bt "d8.bat") --lib $platform --min-api 24 --release `
    --output (Join-Path $build "dex") $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "==> package dex"
$apk = Join-Path $build "app-unsigned.apk"
Push-Location (Join-Path $build "dex")
try {
    & (Join-Path $Jdk "bin\jar.exe") uf $apk classes.dex
    if ($LASTEXITCODE -ne 0) { throw "jar update failed" }
} finally {
    Pop-Location
}

Write-Host "==> zipalign"
& (Join-Path $bt "zipalign.exe") -f -p 4 $apk (Join-Path $build "app-aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if (-not (Test-Path $keyStore)) {
    Write-Host "==> generate keystore"
    New-Item -ItemType Directory -Force -Path (Split-Path $keyStore) | Out-Null
    & (Join-Path $Jdk "bin\keytool.exe") -genkeypair -v `
        -keystore $keyStore -alias qingping `
        -storepass qingping2024 -keypass qingping2024 `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=QingPing, OU=Local, O=QingPing, L=Local, S=Local, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

Write-Host "==> apksigner"
$final = Join-Path $OutDir "QingPing-1.0.0.apk"
& (Join-Path $bt "apksigner.bat") sign `
    --ks $keyStore --ks-key-alias qingping `
    --ks-pass pass:qingping2024 --key-pass pass:qingping2024 `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $final (Join-Path $build "app-aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& (Join-Path $bt "apksigner.bat") verify --print-certs $final
Write-Host ""
Write-Host "APK: $final"
Get-Item $final | Select-Object FullName, Length | Format-List



exit 0
