# static-check.ps1 - assertions that can be made on the APK without a device.
# These are the M0 gate checks from DEVPLAN.md. Exit code 0 = all pass.
# NOTE: keep this file ASCII-only (PowerShell 5.1 + UTF-8-no-BOM issue).
param(
    [string]$Apk = '',
    [string]$Root = '',
    # toolchain home; override if you keep the SDK somewhere else
    [string]$Tools = 'D:\android-tools'
)

$ErrorActionPreference = 'Stop'
if (-not $Root) { $Root = $PSScriptRoot }

# apksigner needs a JRE; default to the bundled one so this runs from any shell.
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $Tools 'jdk17' }

$bt  = Join-Path $Tools 'sdk\build-tools\34.0.0'
$bt2 = Join-Path $Tools 'sdk\platform-tools'

if (-not $Apk) {
    $dist = Join-Path $Root 'dist'
    $newest = Get-ChildItem -Path $dist -Filter *.apk -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $newest) { throw "no apk found in $dist; run build.ps1 first" }
    $Apk = $newest.FullName
}

Write-Host ("checking: " + $Apk)
Write-Host ("sha256  : " + (Get-FileHash $Apk -Algorithm SHA256).Hash)
Write-Host ''

$fail = 0
function Check($name, $ok, $detail) {
    if ($ok) {
        Write-Host ("  PASS  " + $name)
    } else {
        Write-Host ("  FAIL  " + $name + "   " + $detail)
        $script:fail++
    }
}

# ---- 1. permissions -------------------------------------------------------
$perm = & (Join-Path $bt 'aapt2.exe') dump permissions $Apk 2>&1 | Out-String
Check 'no INTERNET permission'     (-not ($perm -match 'android\.permission\.INTERNET'))  'apk declares INTERNET'
Check 'no ACCESS_NETWORK_STATE'    (-not ($perm -match 'ACCESS_NETWORK_STATE'))           'apk declares ACCESS_NETWORK_STATE'
foreach ($p in @('SYSTEM_ALERT_WINDOW','FOREGROUND_SERVICE','FOREGROUND_SERVICE_SPECIAL_USE',
                 'RECEIVE_BOOT_COMPLETED','POST_NOTIFICATIONS','REQUEST_IGNORE_BATTERY_OPTIMIZATIONS')) {
    Check ("declares " + $p) ($perm -match ('android\.permission\.' + $p)) 'missing'
}

# ---- 2. badging -----------------------------------------------------------
$badge = & (Join-Path $bt 'aapt2.exe') dump badging $Apk 2>&1 | Out-String
Check "package is com.scrollguard" ($badge -match "package: name='com\.scrollguard'") 'wrong package'
$wantVer = '0.0.0'
$wantCode = '0'
$gradleApp = Join-Path $Root 'app\build.gradle'
if (Test-Path $gradleApp) {
    $vm = Select-String -Path $gradleApp -Pattern 'versionName\s+"([^"]+)"' | Select-Object -First 1
    if ($vm) { $wantVer = $vm.Matches[0].Groups[1].Value }
    $cm = Select-String -Path $gradleApp -Pattern 'versionCode\s+(\d+)' | Select-Object -First 1
    if ($cm) { $wantCode = $cm.Matches[0].Groups[1].Value }
}
Check ("versionName " + $wantVer)   ($badge -match ("versionName='" + [regex]::Escape($wantVer) + "'")) 'wrong version'
Check ("versionCode " + $wantCode)  ($badge -match ("versionCode='" + [regex]::Escape($wantCode) + "'")) 'wrong versionCode'
Check "minSdk 26"                  ($badge -match "sdkVersion:'26'")                  'wrong minSdk'
Check "targetSdk 34"               ($badge -match "targetSdkVersion:'34'")            'wrong targetSdk'
Check "has launcher activity"      ($badge -match "launchable-activity: name='com\.scrollguard\.MainActivity'") 'no launcher'

# ---- 3. signature ---------------------------------------------------------
$sig = & (Join-Path $bt 'apksigner.bat') verify -v --print-certs $Apk 2>&1 | Out-String
$sigExit = $LASTEXITCODE
Check "apk signature verifies" ($sigExit -eq 0 -and $sig -match 'Verifies') ("apksigner exit " + $sigExit)
Check "signed with v2 scheme"  ($sig -match 'v2 scheme.*: true') 'v2 missing'
Check "not signed with the debug key" (-not ($sig -match 'CN=Android Debug')) 'debug keystore still in use'
Check "signed by TingYixia"    ($sig -match 'certificate DN: CN=TingYixia') 'unexpected signer'

# ---- 4. components --------------------------------------------------------
$tree = & (Join-Path $bt 'aapt2.exe') dump xmltree --file AndroidManifest.xml $Apk 2>&1 | Out-String
foreach ($c in @('MainActivity','SetupActivity','GuardService','ShieldService','ReviveJob','BootReceiver')) {
    Check ("manifest has " + $c) ($tree -match ('com\.scrollguard\.' + $c)) 'component missing'
}
Check 'no backup' ($tree -match 'allowBackup\(.*\)=false') 'allowBackup not false'
Check 'no cleartext' ($tree -match 'usesCleartextTraffic\(.*\)=false') 'cleartext allowed'
Check 'a11y service is bind-protected' ($tree -match 'BIND_ACCESSIBILITY_SERVICE') 'permission missing'

# ---- 5. accessibility config ---------------------------------------------
# release builds shorten resource paths (aapt2 optimize --shorten-resource-paths),
# so the file is no longer at res/xml/accessibility_service_config.xml. scan them.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
$xmlEntries = $zip.Entries | Where-Object { $_.FullName -match '^res/.*\.xml$' } | ForEach-Object { $_.FullName }
$zip.Dispose()

$a11y = ''
$hits = 0
foreach ($e in $xmlEntries) {
    $d = & (Join-Path $bt 'aapt2.exe') dump xmltree --file $e $Apk 2>&1 | Out-String
    if ($d -match 'accessibilityEventTypes') {
        $hits++
        $a11y = $d
    }
}
Check 'a11y config: exactly one file' ($hits -eq 1) ("found " + $hits)
Check 'a11y config: window state events' ($a11y -match 'accessibilityEventTypes') 'attr missing'
Check 'a11y config: content retrieve'    ($a11y -match 'canRetrieveWindowContent') 'attr missing'
Check 'a11y config: notificationTimeout' ($a11y -match 'notificationTimeout') 'attr missing'
Check 'a11y config: settings activity'   ($a11y -match 'settingsActivity') 'attr missing'

# ---- summary --------------------------------------------------------------
Write-Host ''
if ($fail -eq 0) {
    Write-Host 'ALL STATIC CHECKS PASSED'
    exit 0
} else {
    Write-Host ("FAILED: " + $fail + " check(s)")
    exit 1
}
