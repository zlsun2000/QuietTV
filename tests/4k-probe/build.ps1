param(
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$JavaBin,
    [string]$BuildToolsVersion = '34.0.0'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidJar = Join-Path $Sdk 'platforms\android-28\android.jar'
$buildTools = Join-Path $Sdk (Join-Path 'build-tools' $BuildToolsVersion)
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8 = Join-Path $buildTools 'lib\d8.jar'
$signer = Join-Path $buildTools 'lib\apksigner.jar'
$java = Join-Path $JavaBin 'java.exe'
$javac = Join-Path $JavaBin 'javac.exe'
$jar = Join-Path $JavaBin 'jar.exe'
$keytool = Join-Path $JavaBin 'keytool.exe'
foreach ($tool in @($androidJar, $aapt, $zipalign, $d8, $signer, $java, $javac, $jar, $keytool)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing tool: $tool" }
}

# New, ignored output directory per build: no project data or existing files are removed.
$out = Join-Path $root ('emulator-local\4k-probe-build-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
New-Item -ItemType Directory -Path $classes, $dex -Force | Out-Null
$classesJar = Join-Path $out 'classes.jar'
$resources = Join-Path $out 'resources.apk'
$unsigned = Join-Path $out 'unsigned.apk'
$aligned = Join-Path $out 'aligned.apk'
$apk = Join-Path $out 'probe.apk'
$keystore = Join-Path $out 'temporary.jks'
$source = Join-Path $PSScriptRoot 'src\local\quiettv\renderprobe\ProbeActivity.java'
$manifest = Join-Path $PSScriptRoot 'AndroidManifest.xml'

function Invoke-ProbeTool([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Tool failed ($LASTEXITCODE): $program" }
}

$passwordName = 'QUIETTV_4K_PROBE_TEMP_PASSWORD'
$previousPassword = [Environment]::GetEnvironmentVariable($passwordName, 'Process')
[Environment]::SetEnvironmentVariable($passwordName, [guid]::NewGuid().ToString('N'), 'Process')
try {
    Invoke-ProbeTool $javac @('--release', '8', '-classpath', $androidJar, '-d', $classes, $source)
    Invoke-ProbeTool $jar @('cf', $classesJar, '-C', $classes, '.')
    Invoke-ProbeTool $java @('-cp', $d8, 'com.android.tools.r8.D8', '--lib', $androidJar, '--min-api', '23', '--output', $dex, $classesJar)
    Invoke-ProbeTool $aapt @('package', '-f', '-M', $manifest, '-I', $androidJar, '-F', $resources)
    Copy-Item -LiteralPath $resources -Destination $unsigned
    Invoke-ProbeTool $jar @('uf', $unsigned, '-C', $dex, 'classes.dex')
    Invoke-ProbeTool $zipalign @('-f', '4', $unsigned, $aligned)
    Invoke-ProbeTool $keytool @('-genkeypair', '-keystore', $keystore, '-storetype', 'JKS', '-alias', 'probe', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '1', '-dname', 'CN=QuietTV 4K Probe', '-storepass:env', $passwordName, '-keypass:env', $passwordName)
    Invoke-ProbeTool $java @('-jar', $signer, 'sign', '--ks', $keystore, '--ks-key-alias', 'probe', '--ks-pass', "env:$passwordName", '--key-pass', "env:$passwordName", '--out', $apk, $aligned)
    Invoke-ProbeTool $java @('-jar', $signer, 'verify', '--verbose', $apk)
    Write-Output "Probe APK: $apk"
} finally {
    [Environment]::SetEnvironmentVariable($passwordName, $previousPassword, 'Process')
}
