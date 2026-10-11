param(
    [string]$BuildTools = '',
    [string]$AndroidJar = '',
    [string]$JavaBin = '',
    [string]$Keystore = (Join-Path $PSScriptRoot 'local-test.jks'),
    [string]$KeystorePassword = $env:QUIETTV_KEYSTORE_PASSWORD
)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$tools=$BuildTools
$sdkRoot=$env:ANDROID_HOME
if(-not $sdkRoot){$sdkRoot=$env:ANDROID_SDK_ROOT}
if(-not $tools -and $sdkRoot){$candidates=@(Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory -ErrorAction SilentlyContinue | Where-Object {$_.Name -match '^\d+\.\d+\.\d+$'} | Sort-Object {[version]$_.Name} -Descending);if($candidates.Count){$tools=$candidates[0].FullName}}
if(-not $tools){$tools=Join-Path (Split-Path $root) 'android-tools\android-11'}
if(-not $AndroidJar -and $sdkRoot){$AndroidJar=Join-Path $sdkRoot 'platforms\android-28\android.jar'}
if(-not $AndroidJar){$AndroidJar=Join-Path (Split-Path $root) 'android-tools\android-9\android.jar'}
if(-not $JavaBin){
    if($env:JAVA_HOME){$JavaBin=Join-Path $env:JAVA_HOME 'bin'}
    else {$JavaBin=Split-Path (Get-Command javac.exe -ErrorAction Stop).Source}
}
$javaBin=$JavaBin
foreach($file in @("$tools\aapt.exe","$tools\lib\d8.jar","$tools\lib\apksigner.jar","$tools\zipalign.exe",$AndroidJar,"$javaBin\javac.exe")){
    if(-not(Test-Path -LiteralPath $file)){throw "Build dependency missing: $file. Supply -BuildTools, -AndroidJar and -JavaBin."}
}
if(-not $KeystorePassword){
    $secure=Read-Host 'Password for the local signing keystore' -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try{$KeystorePassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secure.Dispose()}
}
if(-not $KeystorePassword){throw 'A signing password is required.'}
$build=Join-Path $root 'build'
# A clean output prevents deleted sources/classes from leaking into a later APK.
# Validate the exact target before any recursive removal.
$expectedBuild=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))
if([IO.Path]::GetFullPath($build) -ne $expectedBuild){throw 'Unexpected build directory'}
if(Test-Path -LiteralPath $build){$item=Get-Item -LiteralPath $build;if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Build directory cannot be a link'};Remove-Item -LiteralPath $build -Recurse -Force}
foreach($dir in @($build,"$build\gen","$build\classes","$build\dex")){New-Item -ItemType Directory -Path $dir -Force | Out-Null}
function Check([string]$step){if($LASTEXITCODE -ne 0){throw "Build failed: $step"}}
& "$tools\aapt.exe" package -f -M "$root\AndroidManifest.xml" -S "$root\res" -I $androidJar -J "$build\gen" -F "$build\resources.apk"
Check 'resources'
$sources=@(Get-ChildItem "$root\src" -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})+@(Get-ChildItem "$build\gen" -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
& "$javaBin\javac.exe" --release 8 -encoding UTF-8 -classpath $androidJar -d "$build\classes" @sources
Check 'java'
& "$javaBin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
Check 'class archive'
& "$javaBin\java.exe" -cp "$tools\lib\d8.jar" com.android.tools.r8.D8 --lib $androidJar --min-api 23 --output "$build\dex" "$build\classes.jar"
Check 'dex'
Copy-Item "$build\resources.apk" "$build\unsigned.apk" -Force
& "$javaBin\jar.exe" uf "$build\unsigned.apk" -C "$build\dex" classes.dex
Check 'apk assembly'
& "$tools\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
Check 'alignment'
if(-not(Test-Path -LiteralPath $Keystore)){
    $oldKeyPassword=$env:QUIETTV_KEYSTORE_PASSWORD
    try{
        $env:QUIETTV_KEYSTORE_PASSWORD=$KeystorePassword
        & "$javaBin\keytool.exe" -genkeypair -keystore $Keystore -storepass:env QUIETTV_KEYSTORE_PASSWORD -keypass:env QUIETTV_KEYSTORE_PASSWORD -alias quiettv -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=QuietTV Local Build'
        Check 'local signing key'
    } finally {$env:QUIETTV_KEYSTORE_PASSWORD=$oldKeyPassword}
}
$oldKeyPassword=$env:QUIETTV_KEYSTORE_PASSWORD
try{
    $env:QUIETTV_KEYSTORE_PASSWORD=$KeystorePassword
    & "$javaBin\java.exe" -jar "$tools\lib\apksigner.jar" sign --ks $Keystore --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$root\QuietTV.apk" "$build\aligned.apk"
    Check 'signing'
} finally {$env:QUIETTV_KEYSTORE_PASSWORD=$oldKeyPassword}
& "$javaBin\java.exe" -jar "$tools\lib\apksigner.jar" verify "$root\QuietTV.apk"
Check 'signature verification'
Write-Output "Built: $root\QuietTV.apk"
