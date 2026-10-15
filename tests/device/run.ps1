#requires -Version 7.0
param(
    [string]$Sdk=$env:ANDROID_HOME,
    [string]$JavaBin='',
    [string]$BuildTools='',
    [string]$Keystore='',
    [string]$Apk='',
    [ValidateSet('display','rules','fresh','upgrade')][string]$Phase='display',
    [string]$Serial='emulator-5570',
    [int]$ServerPort=5041,
    [switch]$ReuseChecks
)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-[0-9]+$'){throw 'Only an explicitly created disposable emulator may run these tests'}
$project=Split-Path (Split-Path $PSScriptRoot)
if(-not $Sdk){$Sdk=$env:ANDROID_SDK_ROOT};if(-not $Sdk){throw 'Supply -Sdk or ANDROID_HOME'}
if(-not $JavaBin){if($env:JAVA_HOME){$JavaBin=Join-Path $env:JAVA_HOME 'bin'}else{$JavaBin=Split-Path (Get-Command javac.exe -ErrorAction Stop).Source}}
if(-not $Keystore){$Keystore=Join-Path $project 'local-test.jks'}
if(-not $Apk){$Apk=Join-Path $project 'QuietTV.apk'}
if(-not $BuildTools){$BuildTools=Join-Path $Sdk 'build-tools\34.0.0'}
$androidJar=Join-Path $Sdk 'platforms\android-28\android.jar'
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
function Guest {
    $command=@('-P',[string]$ServerPort,'-s',$Serial)+$args
    $start=[Diagnostics.ProcessStartInfo]::new($adb);$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    foreach($argument in $command){$start.ArgumentList.Add([string]$argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{
        if(-not $process.Start()){throw 'Cannot start emulator command'}
        $outputTask=$process.StandardOutput.ReadToEndAsync();$errorTask=$process.StandardError.ReadToEndAsync()
        $timeout=if($args.Count -ge 3 -and $args[0] -eq 'shell' -and $args[2] -eq 'instrument'){240000}else{60000}
        if(-not $process.WaitForExit($timeout)){$process.Kill();throw 'Emulator command timed out'}
        $output=$outputTask.GetAwaiter().GetResult();$errorText=$errorTask.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw "Emulator command failed: $($errorText.Trim())"}
        if($output){$output.TrimEnd() -split '\r?\n'}
    }finally{$process.Dispose()}
}
$model=Guest shell getprop ro.product.model
$fingerprint=Guest shell getprop ro.build.fingerprint
if(($model -notmatch 'sdk|Emulator') -and ($fingerprint -notmatch 'generic')){throw 'Refusing tests outside a synthetic emulator'}
$out=Join-Path $project 'build\device-checks'
New-Item -ItemType Directory -Path (Join-Path $project 'emulator-local') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $project "emulator-local\$Serial") -Force | Out-Null
foreach($directory in @($out,"$out\gen","$out\classes","$out\dex")){New-Item -ItemType Directory -Path $directory -Force | Out-Null}
function Check([string]$step){if($LASTEXITCODE -ne 0){throw "Device test build failed: $step"}}
if(-not $ReuseChecks){
& "$BuildTools\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -A "$project\docs\playback-rules" -I $androidJar -J "$out\gen" -F "$out\resources.apk"
Check 'resources'
$sources=@(Get-ChildItem "$PSScriptRoot\src" -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
& "$JavaBin\javac.exe" --release 8 -encoding UTF-8 -classpath "$project\build\classes;$androidJar" -d "$out\classes" @sources
Check 'Java'
& "$JavaBin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
Check 'class archive'
& "$JavaBin\java.exe" -cp "$BuildTools\lib\d8.jar" com.android.tools.r8.D8 --lib $androidJar --classpath "$project\build\classes.jar" --min-api 23 --output "$out\dex" "$out\classes.jar"
Check 'dex'
Copy-Item -LiteralPath "$out\resources.apk" -Destination "$out\unsigned.apk" -Force
& "$JavaBin\jar.exe" uf "$out\unsigned.apk" -C "$out\dex" classes.dex
Check 'assembly'
& "$BuildTools\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
Check 'alignment'
& "$JavaBin\java.exe" -jar "$BuildTools\lib\apksigner.jar" sign --ks $Keystore --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$out\checks.apk" "$out\aligned.apk"
Check 'signing'
}elseif(-not(Test-Path -LiteralPath "$out\checks.apk")){throw 'Build the check APK before using -ReuseChecks'}
Guest install -r $Apk
Guest install -r "$out\checks.apk"
if($Phase -eq 'fresh'){Guest shell pm clear local.quiettv.launcher}
Guest shell am force-stop local.quiettv.launcher
$class=switch($Phase){'display'{'DisplayChecks'} 'rules'{'RuleChecks'} default{'DeviceChecks'}}
$log=Guest shell am instrument -w -e phase $Phase "local.quiettv.checks/local.quiettv.launcher.$class"
$log | Tee-Object -FilePath (Join-Path $project "emulator-local\$Serial\$Phase.txt")
if(-not ($log -match 'INSTRUMENTATION_RESULT: passed=')){throw 'Emulator checks failed; inspect instrumentation output'}
