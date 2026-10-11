param([string]$JavaBin='',[string]$AndroidJar='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot
if(-not $JavaBin){if($env:JAVA_HOME){$JavaBin=Join-Path $env:JAVA_HOME 'bin'}else{$JavaBin=Split-Path (Get-Command javac.exe -ErrorAction Stop).Source}}
if(-not $AndroidJar){$sdkRoot=$env:ANDROID_HOME;if(-not $sdkRoot){$sdkRoot=$env:ANDROID_SDK_ROOT};if($sdkRoot){$AndroidJar=Join-Path $sdkRoot 'platforms\android-28\android.jar'}else{$AndroidJar=Join-Path (Split-Path $projectRoot) 'android-tools\android-9\android.jar'}}
$testOutput=Join-Path $projectRoot 'build\core-checks'
New-Item -ItemType Directory -Path $testOutput -Force | Out-Null
$classPath=(Join-Path $projectRoot 'build\classes')+';'+$androidJar
& "$JavaBin\javac.exe" --release 8 -encoding UTF-8 -classpath $classPath -d $testOutput (Join-Path $PSScriptRoot 'CoreChecks.java')
if($LASTEXITCODE -ne 0){throw 'Core checks compilation failed'}
& "$JavaBin\java.exe" -cp ($testOutput+';'+$classPath) local.quiettv.launcher.CoreChecks
if($LASTEXITCODE -ne 0){throw 'Core checks failed'}
