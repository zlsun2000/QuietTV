param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath = '',
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
if(-not $AdbPath){
    $AdbPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'platform-tools\adb.exe'
    if(-not(Test-Path -LiteralPath $AdbPath)){$AdbPath=(Get-Command adb.exe -ErrorAction Stop).Source}
}
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'ADB was not found.' }

# Keep all original launcher data. This restores its original enabled state (0)
# and selects the original launcher as HOME. It does not launch it unless asked.
$stateResult = & $adbPath -s $Serial shell pm default-state --user 0 com.tcl.cyberui
if ($LASTEXITCODE -ne 0) { throw "Could not restore original launcher state: $stateResult" }
$homeResult = & $adbPath -s $Serial shell cmd package set-home-activity --user 0 com.tcl.cyberui/.MainActivity
if ($LASTEXITCODE -ne 0 -or ($homeResult -join "`n") -notmatch 'Success') {
    throw "Could not set original default HOME: $homeResult"
}
$resolved = & $adbPath -s $Serial shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
if ($LASTEXITCODE -ne 0 -or ($resolved -join "`n") -notmatch 'com\.tcl\.cyberui/\.MainActivity') {
    throw "Original HOME verification failed: $resolved"
}
Write-Output 'Original launcher is enabled and selected as default HOME.'

if ($Launch) {
    & $adbPath -s $Serial shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.tcl.cyberui/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Original HOME was selected, but launch failed.' }
}
