# build.ps1 - build the release APK and drop a versioned copy into dist\
# NOTE: keep this file ASCII-only. PowerShell 5.1 misreads UTF-8-without-BOM
#       files, so any non-ASCII literal here would corrupt paths at runtime.
param(
    [string]$Version = '1.0.0',
    [string]$Root = ''
)

$ErrorActionPreference = 'Stop'

if (-not $Root) { $Root = $PSScriptRoot }
$proj = 'D:\scrollguard'
$sdk  = 'D:\android-tools\sdk'

$env:JAVA_HOME        = 'D:\android-tools\jdk17'
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME     = $sdk
$gradle = 'D:\android-tools\gradle\gradle-8.7\bin\gradle.bat'

Write-Host "== gradle assembleRelease =="
& $gradle -p $proj assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle failed with exit code $LASTEXITCODE" }

$src = Join-Path $proj 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $src)) { throw "apk not found: $src" }

$dist = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$name  = 'tingyixia-v' + $Version + '-' + $stamp + '.apk'
$dst   = Join-Path $dist $name
Copy-Item $src $dst -Force

$hash = (Get-FileHash $dst -Algorithm SHA256).Hash
Add-Content -Path (Join-Path $dist 'SHA256.txt') -Value ($hash + '  ' + $name) -Encoding Ascii

Write-Host "== ok =="
Write-Host ("apk  : " + $dst)
Write-Host ("size : " + (Get-Item $dst).Length + " bytes")
Write-Host ("sha  : " + $hash)
