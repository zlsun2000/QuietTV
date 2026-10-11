param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+$')][string]$HomeComponent,
    [string]$AdbPath='',
    [switch]$Launch
)
$ErrorActionPreference='Stop'
if($HomeComponent -match '^local\.quiettv\.launcher/'){throw 'Select the original HOME component, not QuietTV'}
if(-not $AdbPath){$AdbPath=Join-Path (Split-Path $PSScriptRoot) 'platform-tools\adb.exe';if(-not(Test-Path -LiteralPath $AdbPath)){$AdbPath=(Get-Command adb.exe -ErrorAction Stop).Source}}
# Only select an existing enabled HOME. Never enable/disable packages or reset OEM data.
$homes=(& $AdbPath -s $Serial shell cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | Out-String)
if($LASTEXITCODE -ne 0){throw 'This firmware does not support this command; use its default-app settings instead'}
$components=@($homes -split '\r?\n' | ForEach-Object {$_.Trim()} | Where-Object {$_ -match '^[A-Za-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+$'})
function Expand-Component([string]$value){$parts=$value.Split('/');if($parts.Count -ne 2){return ''};if($parts[1].StartsWith('.')){return $parts[0]+'/'+$parts[0]+$parts[1]};return $value}
if((Expand-Component $HomeComponent) -notin @($components | ForEach-Object {Expand-Component $_})){throw 'The requested component is not an enabled HOME on this device'}
$result=& $AdbPath -s $Serial shell cmd package set-home-activity --user 0 $HomeComponent
if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Success'){throw 'The original HOME could not be selected'}
$resolved=(& $AdbPath -s $Serial shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or (Expand-Component (($resolved -split '\r?\n')[-1])) -ne (Expand-Component $HomeComponent)){throw 'HOME verification failed'}
if($Launch){& $AdbPath -s $Serial shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n $HomeComponent;if($LASTEXITCODE -ne 0){throw 'HOME selected, but launch failed'}}
Write-Output 'Original HOME selected; all launcher data retained.'
