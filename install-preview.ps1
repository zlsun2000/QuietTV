param(
    [Parameter(Mandatory=$true)][string]$Device,
    [string]$AdbPath='',
    [string]$Apk=(Join-Path $PSScriptRoot 'QuietTV.apk'),
    [switch]$Launch,
    [switch]$UseAdbVerifierWorkaround
)
$ErrorActionPreference='Stop'
if(-not $AdbPath){$AdbPath=Join-Path (Split-Path $PSScriptRoot) 'platform-tools\adb.exe';if(-not(Test-Path -LiteralPath $AdbPath)){$AdbPath=(Get-Command adb.exe -ErrorAction Stop).Source}}
if(-not(Test-Path -LiteralPath $Apk)){throw 'APK missing'}
& $AdbPath -s $Device get-state
if($LASTEXITCODE -ne 0){throw 'Device is not connected or authorized'}
$old=''
if($UseAdbVerifierWorkaround){
    $old=(& $AdbPath -s $Device shell settings get global verifier_verify_adb_installs | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $old -notin @('null','0','1')){throw 'Original verification setting unavailable'}
}
try{
    if($UseAdbVerifierWorkaround){& $AdbPath -s $Device shell settings put global verifier_verify_adb_installs 0;if($LASTEXITCODE -ne 0){throw 'Cannot configure temporary verification setting'}}
    & $AdbPath -s $Device install -r $Apk
    if($LASTEXITCODE -ne 0){throw 'Install failed; do not uninstall an existing app until your data and signing key are checked'}
}finally{
    if($UseAdbVerifierWorkaround){
        if($old -eq 'null'){& $AdbPath -s $Device shell settings delete global verifier_verify_adb_installs | Out-Null}else{& $AdbPath -s $Device shell settings put global verifier_verify_adb_installs $old | Out-Null}
        $actual=(& $AdbPath -s $Device shell settings get global verifier_verify_adb_installs | Out-String).Trim()
        if($LASTEXITCODE -ne 0 -or $actual -ne $old){throw 'Verification setting restoration unconfirmed'}
    }
}
if($Launch){& $AdbPath -s $Device shell am start -n local.quiettv.launcher/.MainActivity;if($LASTEXITCODE -ne 0){throw 'Installed, but could not launch'}}
Write-Output 'Installed with application data retained. Select/check the default HOME in system settings if desired.'
