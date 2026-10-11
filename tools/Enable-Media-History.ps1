param(
    [Parameter(Mandatory=$true)][string]$Device,
    [string]$AdbPath=(Join-Path (Split-Path (Split-Path $PSScriptRoot)) 'platform-tools\adb.exe')
)
$ErrorActionPreference='Stop'
# Start the newly updated app process without showing UI before the OS binds it.
& $AdbPath -s $Device shell am broadcast -a local.quiettv.launcher.EXTRAS_STATUS -n local.quiettv.launcher/.LocalMaintenanceReceiver
if($LASTEXITCODE -ne 0){throw 'Unable to start launcher diagnostics'}
$listeners=(& $AdbPath -s $Device shell settings get secure enabled_notification_listeners).Trim()
if($listeners -match 'local\.quiettv\.launcher/'){
    & $AdbPath -s $Device shell cmd notification disallow_listener local.quiettv.launcher/.MediaHistoryService
    if($LASTEXITCODE -ne 0){throw 'Unable to refresh existing media recognition access'}
}
& $AdbPath -s $Device shell cmd notification allow_listener local.quiettv.launcher/.MediaHistoryService
if($LASTEXITCODE -ne 0){throw 'Unable to enable media recognition access'}
Write-Output 'Enabled standard media recognition. No activity or playback was started.'
