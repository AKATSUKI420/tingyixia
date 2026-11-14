# run-mumu.ps1 - one command: make sure MuMu is up, install the newest apk, start it.
# NOTE: keep this file ASCII-only. PowerShell 5.1 misreads UTF-8-without-BOM
#       files, so any non-ASCII literal here would corrupt paths at runtime.
param(
    [string]$Apk   = '',
    [switch]$Shot,
    [string]$ShotPath = '',
    [switch]$NoStart,
    [int]$BootTimeoutSec = 180,
    # toolchain home; override if JDK/SDK live somewhere else
    [string]$Tools = 'D:\android-tools',
    # MuMu install (mumu-cli.exe)
    [string]$MuMuCli = 'D:\Program Files\Netease\MuMu\nx_main\mumu-cli.exe',
    [string]$Serial = '127.0.0.1:16384'
)

$ErrorActionPreference = 'Stop'

$root   = $PSScriptRoot
$sdk    = Join-Path $Tools 'sdk'
$adb    = Join-Path $sdk 'platform-tools\adb.exe'
$mumu   = $MuMuCli
$serial = $Serial
$pkg    = 'com.scrollguard'
$act    = 'com.scrollguard/.MainActivity'

foreach ($p in @($adb, $mumu)) {
    if (-not (Test-Path $p)) { throw "not found: $p" }
}

# NOTE: do not name a parameter $Args here - it collides with the automatic
#       variable and @Args would splat the function's own arguments instead.
function Invoke-Adb {
    param([string[]]$AdbArgs)
    return (& $adb -s $serial @AdbArgs 2>&1) -join "`n"
}

function Test-Booted {
    try {
        $v = (& $adb -s $serial shell getprop sys.boot_completed 2>&1) -join ''
        return ($v.Trim() -eq '1')
    } catch {
        return $false
    }
}

# ---- 1. make sure the player is running and adb can see it ----
if (-not (Test-Booted)) {
    Write-Host '== start MuMu player 0 =='
    & $mumu control -v 0 launch 2>&1 | Out-Null
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 4
        & $adb connect $serial 2>&1 | Out-Null
        if (Test-Booted) { break }
    }
    if (-not (Test-Booted)) { throw "MuMu did not finish booting in $BootTimeoutSec s" }
} else {
    & $adb connect $serial 2>&1 | Out-Null
}
Write-Host ("== device ok: " + $serial + " ==")

# ---- 2. pick the apk: explicit, else newest in dist\, else newest in root ----
if (-not $Apk) {
    $cand = @()
    $dist = Join-Path $root 'dist'
    if (Test-Path $dist) { $cand += Get-ChildItem $dist -Filter '*.apk' -File }
    $cand += Get-ChildItem $root -Filter '*.apk' -File
    if ($cand.Count -eq 0) { throw 'no apk found: pass -Apk <path>' }
    $Apk = ($cand | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not (Test-Path $Apk)) { throw "apk not found: $Apk" }

# ---- 3. install ----
Write-Host ('== install ' + (Split-Path $Apk -Leaf) + ' ==')
$installOut = Invoke-Adb @('install', '-r', $Apk)
if ($installOut -notmatch 'Success') {
    throw ("install failed: " + $installOut.Trim())
}
$pkgDump = Invoke-Adb @('shell', 'dumpsys', 'package', $pkg)
$verLines = ($pkgDump -split "`n") | Select-String 'versionName|versionCode' | Select-Object -First 2
Write-Host (($verLines | ForEach-Object { $_.ToString().Trim() }) -join "`n")

# ---- 4. start ----
if (-not $NoStart) {
    Write-Host '== start app =='
    & $adb -s $serial shell am start -n $act 2>&1 | Select-Object -First 2
    Start-Sleep -Seconds 3
    $winDump = Invoke-Adb @('shell', 'dumpsys', 'window')
    $focus = ($winDump -split "`n") | Select-String 'mCurrentFocus' | Select-Object -First 1
    if ($focus) { Write-Host ('focus: ' + $focus.ToString().Trim()) }
}

# ---- 5. optional screenshot (cmd redirect keeps the png bytes intact) ----
if ($Shot) {
    if (-not $ShotPath) {
        $ShotPath = Join-Path $root ('shot-' + (Get-Date -Format 'HHmmss') + '.png')
    }
    $redirect = '"' + $adb + '" -s ' + $serial + ' exec-out screencap -p > "' + $ShotPath + '"'
    cmd /c $redirect
    Write-Host ('shot : ' + $ShotPath + '  (' + (Get-Item $ShotPath).Length + ' bytes)')
}

Write-Host '== ok =='
