#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${sdk_root:?Set ANDROID_HOME to an Android SDK with Platform 28 and Build Tools}"
android_jar="${QUIETTV_ANDROID_JAR:-$sdk_root/platforms/android-28/android.jar}"
build_tools="${QUIETTV_BUILD_TOOLS:-}"
if [[ -z "$build_tools" ]]; then
  latest_tools="$(for candidate in "$sdk_root"/build-tools/*; do if [[ -f "$candidate/aapt" && "${candidate##*/}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then basename "$candidate"; fi; done | sort -t . -k 1,1n -k 2,2n -k 3,3n | tail -n 1)"
  build_tools="$sdk_root/build-tools/$latest_tools"
fi
for dependency in "$build_tools/aapt" "$build_tools/zipalign" "$build_tools/lib/d8.jar" "$build_tools/lib/apksigner.jar" "$android_jar"; do
  [[ -f "$dependency" ]] || { echo "Missing dependency: $dependency" >&2; exit 1; }
done
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null
build_dir="$project_root/build"
[[ "$build_dir" == "$project_root/build" && ! -L "$build_dir" ]] || exit 1
rm -rf -- "$build_dir"
mkdir -p "$build_dir/gen" "$build_dir/classes" "$build_dir/dex"
"$build_tools/aapt" package -f -M "$project_root/AndroidManifest.xml" -S "$project_root/res" -I "$android_jar" -J "$build_dir/gen" -F "$build_dir/resources.apk"
find "$project_root/src" "$build_dir/gen" -name '*.java' -print0 | xargs -0 javac --release 8 -encoding UTF-8 -classpath "$android_jar" -d "$build_dir/classes"
jar cf "$build_dir/classes.jar" -C "$build_dir/classes" .
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$android_jar" --min-api 23 --output "$build_dir/dex" "$build_dir/classes.jar"
cp "$build_dir/resources.apk" "$build_dir/unsigned.apk"
jar uf "$build_dir/unsigned.apk" -C "$build_dir/dex" classes.dex
"$build_tools/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"
keystore="${QUIETTV_KEYSTORE:-$project_root/local-test.jks}"
if [[ -z "${QUIETTV_KEYSTORE_PASSWORD:-}" ]]; then read -rsp 'Local signing password: ' QUIETTV_KEYSTORE_PASSWORD; echo; fi
[[ -n "$QUIETTV_KEYSTORE_PASSWORD" ]] || { echo 'Signing password required' >&2; exit 1; }
export QUIETTV_KEYSTORE_PASSWORD
if [[ ! -f "$keystore" ]]; then
  keytool -genkeypair -keystore "$keystore" -storepass:env QUIETTV_KEYSTORE_PASSWORD -keypass:env QUIETTV_KEYSTORE_PASSWORD -alias quiettv -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=QuietTV Local Build'
fi
java -jar "$build_tools/lib/apksigner.jar" sign --ks "$keystore" --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$project_root/QuietTV.apk" "$build_dir/aligned.apk"
java -jar "$build_tools/lib/apksigner.jar" verify "$project_root/QuietTV.apk"
if [[ "${QUIETTV_RUN_CHECKS:-0}" == 1 ]]; then
  mkdir -p "$build_dir/core-checks"
  javac --release 8 -encoding UTF-8 -classpath "$build_dir/classes:$android_jar" -d "$build_dir/core-checks" "$project_root/tests/CoreChecks.java"
  java -cp "$build_dir/core-checks:$build_dir/classes:$android_jar" local.quiettv.launcher.CoreChecks
fi
echo "Built: $project_root/QuietTV.apk"
