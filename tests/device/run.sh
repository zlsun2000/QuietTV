#!/usr/bin/env bash
set -euo pipefail
[[ "${QUIETTV_EMULATOR_TESTS:-0}" == 1 ]] || { echo 'Only run on an explicitly authorized, disposable CI emulator' >&2; exit 1; }
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
sdk_root="${ANDROID_HOME:?}"
build_tools="${QUIETTV_BUILD_TOOLS:?}"
image="${QUIETTV_EMULATOR_IMAGE:?}"
case "$image" in 'system-images;android-28;android-tv;x86'|'system-images;android-30;android-tv;x86') ;; *) echo 'Unsupported test image' >&2; exit 1 ;; esac
adb_bin="$sdk_root/platform-tools/adb"
serial='emulator-5556'
avd_name="quiettv-ci-$(echo "$image" | tr -cd '0-9')"
out_dir="$project_root/build/device-checks"
mkdir -p "$out_dir/gen" "$out_dir/classes" "$out_dir/dex" "$project_root/build/ci-artifacts"
echo no | "$sdk_root/cmdline-tools/latest/bin/avdmanager" create avd --force --name "$avd_name" --package "$image"
"$sdk_root/emulator/emulator" -avd "$avd_name" -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -wipe-data > "$project_root/build/ci-artifacts/emulator.log" 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT
"$adb_bin" -s "$serial" wait-for-device
booted=0
for _ in $(seq 1 120); do
  if [[ "$("$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then booted=1; break; fi
  sleep 2
done
[[ "$booted" == 1 ]] || { echo 'Emulator failed to boot' >&2; exit 1; }
"$adb_bin" -s "$serial" shell input keyevent 82
"$adb_bin" -s "$serial" install -r "$project_root/QuietTV.apk"
"$adb_bin" -s "$serial" shell cmd package set-home-activity --user 0 local.quiettv.launcher/.MainActivity
android_jar="$sdk_root/platforms/android-28/android.jar"
"$build_tools/aapt" package -f -M "$project_root/tests/device/AndroidManifest.xml" -I "$android_jar" -J "$out_dir/gen" -F "$out_dir/resources.apk"
find "$project_root/tests/device/src" -name '*.java' -print0 | xargs -0 javac --release 8 -encoding UTF-8 -classpath "$project_root/build/classes:$android_jar" -d "$out_dir/classes"
jar cf "$out_dir/classes.jar" -C "$out_dir/classes" .
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$android_jar" --min-api 23 --output "$out_dir/dex" "$out_dir/classes.jar"
cp "$out_dir/resources.apk" "$out_dir/unsigned.apk"
jar uf "$out_dir/unsigned.apk" -C "$out_dir/dex" classes.dex
"$build_tools/zipalign" -f 4 "$out_dir/unsigned.apk" "$out_dir/aligned.apk"
java -jar "$build_tools/lib/apksigner.jar" sign --ks "$project_root/local-test.jks" --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$out_dir/checks.apk" "$out_dir/aligned.apk"
"$adb_bin" -s "$serial" install -r "$out_dir/checks.apk"
for phase in fresh upgrade; do
  if [[ "$phase" == upgrade ]]; then
    "$adb_bin" -s "$serial" shell am force-stop local.quiettv.launcher
    "$adb_bin" -s "$serial" install -r "$project_root/QuietTV.apk"
  fi
  "$adb_bin" -s "$serial" shell am instrument -w -e phase "$phase" local.quiettv.checks/local.quiettv.launcher.DeviceChecks | tee "$project_root/build/ci-artifacts/$phase.txt"
  grep -q "INSTRUMENTATION_RESULT: passed=$phase:" "$project_root/build/ci-artifacts/$phase.txt"
done
"$adb_bin" -s "$serial" pull /sdcard/Android/data/local.quiettv.launcher/files/ci-fresh.png "$project_root/build/ci-artifacts/ci-fresh.png"
"$adb_bin" -s "$serial" logcat -d -s AndroidRuntime:E > "$project_root/build/ci-artifacts/crashes.txt"
[[ ! -s "$project_root/build/ci-artifacts/crashes.txt" || -z "$(grep 'FATAL EXCEPTION' "$project_root/build/ci-artifacts/crashes.txt" || true)" ]]
