param(
    [Parameter(Mandatory=$true)][string]$Device,
    [string]$AdbPath = ''
)
$ErrorActionPreference='Stop'
if(-not $AdbPath){
    $AdbPath=Join-Path (Split-Path $PSScriptRoot) 'platform-tools\adb.exe'
    if(-not(Test-Path -LiteralPath $AdbPath)){$AdbPath=(Get-Command adb.exe -ErrorAction Stop).Source}
}
$apk=Join-Path $PSScriptRoot 'QuietTV.apk'
& $adbPath -s $Device get-state
if($LASTEXITCODE -ne 0){throw 'TV is not connected or authorized'}
$homeBefore=(& $adbPath -s $Device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Out-String).Trim()
if($LASTEXITCODE -ne 0){throw 'Original HOME preference unavailable'}
$quietWasDefault=$homeBefore -match 'local\.quiettv\.launcher/\.MainActivity'
$old=(& $adbPath -s $Device shell settings get global verifier_verify_adb_installs | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $old -notin @('null','0','1')){throw 'Original verification setting unavailable'}
Set-Content (Join-Path $PSScriptRoot 'adb-verifier-original.txt') $old
try {
    & $adbPath -s $Device shell settings put global verifier_verify_adb_installs 0
    if($LASTEXITCODE -ne 0){throw 'Cannot configure temporary ADB verification setting'}
    & $adbPath -s $Device install -r $apk
    if($LASTEXITCODE -ne 0){throw 'Install failed'}
} finally {
    if($old -eq 'null'){& $adbPath -s $Device shell settings delete global verifier_verify_adb_installs}
    else {& $adbPath -s $Device shell settings put global verifier_verify_adb_installs $old}
    $actual=(& $adbPath -s $Device shell settings get global verifier_verify_adb_installs | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $actual -ne $old){throw 'Installation verification setting restoration unconfirmed'}
}
if($quietWasDefault){
    & $adbPath -s $Device shell cmd package set-home-activity --user 0 local.quiettv.launcher/.MainActivity
    if($LASTEXITCODE -ne 0){throw 'Default HOME restoration failed'}
    $homeAfter=(& $adbPath -s $Device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $homeAfter -notmatch 'local\.quiettv\.launcher/\.MainActivity'){throw 'Default HOME restoration unconfirmed'}
}
& $adbPath -s $Device shell am start -n local.quiettv.launcher/.MainActivity
if($LASTEXITCODE -ne 0){throw 'Launcher did not open'}
Write-Output 'Preview installed. Original launcher remains enabled; existing QuietTV HOME preference preserved.'
