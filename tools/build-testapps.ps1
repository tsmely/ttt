param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not $Jdk) { $Jdk = $env:JAVA_HOME }

$bt = Join-Path $Sdk "build-tools\35.0.0"
$platform = Join-Path $Sdk "platforms\android-35\android.jar"
$keyStore = Join-Path $root "keystore\qingping.jks"
$out = Join-Path $root "dist\test"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$apps = @(
    @{ name = "FakeAd"; dir = "fakead"; pkg = "com.qingping.fakead" },
    @{ name = "FakeBrowser"; dir = "fakebrowser"; pkg = "com.qingping.fakebrowser" }
)

foreach ($app in $apps) {
    $dir = Join-Path $root ("testapps\" + $app.dir)
    $build = Join-Path $root ("build\testapp-" + $app.dir)
    if (Test-Path $build) { Remove-Item -Recurse -Force $build }
    New-Item -ItemType Directory -Force -Path $build | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $build "classes") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $build "dex") | Out-Null

    Write-Host "==> $($app.name): link"
    & (Join-Path $bt "aapt2.exe") link `
        -o (Join-Path $build "unsigned.apk") `
        -I $platform `
        --manifest (Join-Path $dir "AndroidManifest.xml") `
        --min-sdk-version 24 --target-sdk-version 24
    if ($LASTEXITCODE -ne 0) { throw "$($app.name) link failed" }

    Write-Host "==> $($app.name): javac"
    $javaFiles = Get-ChildItem -Recurse -Filter *.java $dir | ForEach-Object { $_.FullName }
    & (Join-Path $Jdk "bin\javac.exe") -encoding UTF-8 -source 11 -target 11 -nowarn `
        -classpath $platform -d (Join-Path $build "classes") $javaFiles
    if ($LASTEXITCODE -ne 0) { throw "$($app.name) javac failed" }

    Write-Host "==> $($app.name): d8"
    $classFiles = Get-ChildItem -Recurse -Filter *.class (Join-Path $build "classes") | ForEach-Object { $_.FullName }
    & (Join-Path $bt "d8.bat") --lib $platform --min-api 24 --release `
        --output (Join-Path $build "dex") $classFiles
    if ($LASTEXITCODE -ne 0) { throw "$($app.name) d8 failed" }

    Push-Location (Join-Path $build "dex")
    try {
        & (Join-Path $Jdk "bin\jar.exe") uf (Join-Path $build "unsigned.apk") classes.dex
    } finally {
        Pop-Location
    }

    & (Join-Path $bt "zipalign.exe") -f -p 4 (Join-Path $build "unsigned.apk") (Join-Path $build "aligned.apk")
    & (Join-Path $bt "apksigner.bat") sign `
        --ks $keyStore --ks-key-alias qingping `
        --ks-pass pass:qingping2024 --key-pass pass:qingping2024 `
        --out (Join-Path $out ($app.name + ".apk")) (Join-Path $build "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "$($app.name) sign failed" }
    Write-Host "built $(Join-Path $out ($app.name + '.apk'))"
}

exit 0
