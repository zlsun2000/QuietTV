#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$Sdk,
    [Parameter(Mandatory)][string]$JavaBin,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$AppClasses,
    [Parameter(Mandatory)][string]$Keystore,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label,
    [string]$BuildTools='',
    [string]$Serial='',
    [int]$ServerPort=5041,
    [string]$RendererLabel='',
    [switch]$ConfirmDisposable,
    [switch]$BuildOnly
)
$ErrorActionPreference='Stop'
if(-not $env:QUIETTV_KEYSTORE_PASSWORD){throw 'Supply the temporary test-key password securely via QUIETTV_KEYSTORE_PASSWORD.'}
if(-not $BuildOnly -and (-not $ConfirmDisposable -or $Serial -notmatch '^emulator-[0-9]+$')){throw 'Specify an explicitly created disposable emulator, its emulator-NNNN serial and -ConfirmDisposable.'}
if(-not $BuildOnly -and -not $RendererLabel){throw 'Specify -RendererLabel (for example swiftshader) and keep it identical for both runs.'}
if($ServerPort -lt 1024 -or $ServerPort -gt 65535){throw 'Invalid isolated ADB server port.'}
if(-not $BuildTools){$BuildTools=Join-Path $Sdk 'build-tools\34.0.0'}
$androidJar=Join-Path $Sdk 'platforms\android-28\android.jar'
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
$project=Split-Path (Split-Path (Split-Path $PSScriptRoot))
$out=Join-Path $project ("emulator-local\performance-"+$Label)
if(Test-Path -LiteralPath $out){throw 'This label already has output; use a new label to preserve it.'}
foreach($required in @($Apk,$AppClasses,$Keystore,$androidJar,"$BuildTools\aapt.exe","$BuildTools\lib\d8.jar","$BuildTools\lib\apksigner.jar","$BuildTools\zipalign.exe","$JavaBin\javac.exe","$JavaBin\jar.exe","$JavaBin\java.exe")){if(-not(Test-Path -LiteralPath $required)){throw "Missing input: $required"}}
if(-not $BuildOnly -and -not(Test-Path -LiteralPath $adb)){throw 'ADB missing'}
$packageInfo=& "$BuildTools\aapt.exe" dump badging $Apk
if($LASTEXITCODE -ne 0 -or -not ($packageInfo -match "^package: name='local\.quiettv\.launcher'")){throw 'The target APK must be the QuietTV launcher.'}
New-Item -ItemType Directory -Path $out,"$out\classes","$out\dex" -Force | Out-Null
function Check([string]$step){if($LASTEXITCODE -ne 0){throw "Performance fixture build failed: $step"}}
& "$BuildTools\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -I $androidJar -F "$out\resources.apk"
Check 'resources'
& "$JavaBin\javac.exe" --release 8 -encoding UTF-8 -classpath "$AppClasses;$androidJar" -d "$out\classes" "$PSScriptRoot\PerformanceChecks.java"
Check 'Java'
& "$JavaBin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
Check 'class archive'
& "$JavaBin\java.exe" -cp "$BuildTools\lib\d8.jar" com.android.tools.r8.D8 --lib $androidJar --classpath $AppClasses --min-api 23 --output "$out\dex" "$out\classes.jar"
Check 'dex'
Copy-Item -LiteralPath "$out\resources.apk" -Destination "$out\unsigned.apk"
& "$JavaBin\jar.exe" uf "$out\unsigned.apk" -C "$out\dex" classes.dex
Check 'assembly'
& "$BuildTools\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
Check 'alignment'
& "$JavaBin\java.exe" -jar "$BuildTools\lib\apksigner.jar" sign --ks $Keystore --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$out\checks.apk" "$out\aligned.apk"
Check 'test signing'
function Certificate([string]$path){$details=& "$JavaBin\java.exe" -jar "$BuildTools\lib\apksigner.jar" verify --print-certs $path;Check 'signature verification';$line=@($details | Where-Object {$_ -match '^Signer #\d+ certificate SHA-256 digest:'});if($line.Count -ne 1){throw 'Cannot identify exactly one signing certificate'};return (($line[0] -split ': ',2)[1]).Trim()}
if((Certificate $Apk) -ne (Certificate "$out\checks.apk")){throw 'The test and target APK must use the same temporary signing certificate.'}
if($BuildOnly){Write-Output "Fixture built with matching signature: $out\checks.apk";return}
function Guest {
    $start=[Diagnostics.ProcessStartInfo]::new($adb);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    foreach($argument in (@('-P',[string]$ServerPort,'-s',$Serial)+$args)){$start.ArgumentList.Add([string]$argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{if(-not $process.Start()){throw 'Cannot start ADB'};$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync();if(-not $process.WaitForExit(240000)){$process.Kill();throw 'Emulator command timed out'};$text=$stdout.GetAwaiter().GetResult();$err=$stderr.GetAwaiter().GetResult();if($process.ExitCode -ne 0){throw "Emulator command failed: $($err.Trim())"};return $text.TrimEnd()}finally{$process.Dispose()}
}
$fingerprint=Guest shell getprop ro.build.fingerprint
$hardware=Guest shell getprop ro.hardware
$qemu=Guest shell getprop ro.kernel.qemu
if($qemu -ne '1' -or $hardware -notin @('ranchu','goldfish') -or ($fingerprint -notmatch 'generic' -and $fingerprint -notmatch '^google/sdk_google_atv_x86/emulator_x86_arm:14/')){throw 'Refusing to change data outside a recognized synthetic QEMU emulator.'}
$metadata=@("label=$Label","renderer_label=$RendererLabel","fingerprint=$fingerprint","hardware=$hardware","qemu=$qemu","sdk=$(Guest shell getprop ro.build.version.sdk)","display_size=$(Guest shell wm size)","display_density=$(Guest shell wm density)")
$gles=Guest shell dumpsys SurfaceFlinger
$metadata+=@($gles -split '\r?\n' | Where-Object {$_ -match '^\s*GLES:'})
$metadata | Set-Content -LiteralPath "$out\environment.txt" -Encoding utf8
# Version 39 cannot replace a newer release without uninstalling. The strict
# emulator guard above and explicit switch are required before any removal.
if((Guest shell pm path local.quiettv.launcher) -match '^package:'){
    if((Guest uninstall local.quiettv.launcher) -notmatch '^Success'){throw 'Could not remove the launcher from the disposable emulator'}
}
Guest install -r $Apk | Out-Null
Guest install -r "$out\checks.apk" | Out-Null
# This erases only the target launcher data on the explicitly confirmed emulator.
if((Guest shell pm clear local.quiettv.launcher) -notmatch 'Success'){throw 'Could not clear disposable test state'}
Guest shell am force-stop local.quiettv.launcher | Out-Null
$log=Guest shell am instrument -w 'local.quiettv.perfchecks/local.quiettv.launcher.PerformanceChecks'
$log | Set-Content -LiteralPath "$out\results.txt" -Encoding utf8
if($log -notmatch 'INSTRUMENTATION_RESULT: passed=synthetic performance fixture complete'){throw "Performance fixture failed; inspect $out\results.txt"}
Write-Output "Synthetic performance results: $out\results.txt"
