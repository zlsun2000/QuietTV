#!/usr/bin/env bash
set -euo pipefail
[[ "${QUIETTV_EMULATOR_TESTS:-0}" == 1 ]] || { echo 'Only run on an explicitly authorized, disposable CI emulator' >&2; exit 1; }
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
sdk_root="${ANDROID_HOME:?}"
build_tools="${QUIETTV_BUILD_TOOLS:?}"
image="${QUIETTV_EMULATOR_IMAGE:?}"
case "$image" in 'system-images;android-23;android-tv;x86'|'system-images;android-28;android-tv;x86'|'system-images;android-30;android-tv;x86') ;; *) echo 'Unsupported test image' >&2; exit 1 ;; esac
adb_bin="$sdk_root/platform-tools/adb"
emulator_bin="${QUIETTV_EMULATOR_BIN:-$sdk_root/emulator/emulator}"
serial='emulator-5556'
# Bound every ADB call; a disconnected guest must not stall the whole job.
adb_device() { timeout 60s "$adb_bin" -s "$serial" "$@"; }
avd_name="quiettv-ci-$(echo "$image" | tr -cd '0-9')"
out_dir="$project_root/build/device-checks"
mkdir -p "$out_dir/gen" "$out_dir/classes" "$out_dir/dex" "$project_root/build/ci-artifacts"
export ANDROID_AVD_HOME="$out_dir/avd"
export ANDROID_USER_HOME="$out_dir/android-user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
mkdir -p "$ANDROID_USER_HOME"
# The guest's injected key and the host's authentication key must agree.
export ADB_VENDOR_KEYS="$ANDROID_USER_HOME/adbkey"
"$adb_bin" keygen "$ADB_VENDOR_KEYS"
"$adb_bin" start-server
avd_path="$ANDROID_AVD_HOME/$avd_name.avd"
mkdir -p "$ANDROID_AVD_HOME"
profile=tv_1080p
# The API 23 image's old DMA driver panics allocating a 1080p RGBA buffer
# (8294400 bytes). Use the official smaller TV profile for this runtime.
if [[ "$image" == 'system-images;android-23;android-tv;x86' ]]; then profile=tv_720p; fi
echo no | "$sdk_root/cmdline-tools/latest/bin/avdmanager" create avd --force --name "$avd_name" --package "$image" --path "$avd_path" --device "$profile"
# SDK/runner versions can disagree on the default AVD index directory.
[[ -f "$avd_path/config.ini" ]] || { echo 'AVD creation failed' >&2; exit 1; }
printf 'avd.ini.encoding=UTF-8\npath=%s\n' "$avd_path" > "$ANDROID_AVD_HOME/$avd_name.ini"
graphics=(-gpu swiftshader_indirect)
if [[ "$image" != 'system-images;android-28;android-tv;x86' ]]; then graphics+=(-feature -Vulkan); fi
if [[ "$image" == 'system-images;android-23;android-tv;x86' ]]; then
  # This kernel's goldfish DMA allocator also panics at 720p. Use the
  # emulator's non-DMA color-buffer transport without changing the guest OS.
  graphics+=(-feature -GLDMA)
fi
"$emulator_bin" -avd "$avd_name" -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot "${graphics[@]}" -no-metrics -show-kernel -wipe-data > "$project_root/build/ci-artifacts/emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
  result=$?
  if [[ "$result" != 0 ]]; then
    timeout 10s "$adb_bin" -s "$serial" logcat -d > "$project_root/build/ci-artifacts/failure-logcat.log" 2>&1 || true
  fi
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
booted=0
boot_deadline=$((SECONDS+240))
while (( SECONDS < boot_deadline )); do
  kill -0 "$emulator_pid" 2>/dev/null || { tail -n 30 "$project_root/build/ci-artifacts/emulator.log"; exit 1; }
  if [[ "$(timeout 5s "$adb_bin" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then booted=1; break; fi
  sleep 2
done
[[ "$booted" == 1 ]] || { echo 'Emulator failed to boot' >&2; exit 1; }
# Provision this guest in one connection before its first-boot USB/ADB reset.
timeout 20s "$adb_bin" -s "$serial" shell 'settings put global adb_enabled 1 && settings put global device_provisioned 1 && settings put secure user_setup_complete 1 && settings put secure tv_user_setup_complete 1' > "$project_root/build/ci-artifacts/provision.log" 2>&1 || true
# boot_completed may precede the first stable settings/package service connection.
ready=0
readiness_log="$project_root/build/ci-artifacts/boot-readiness.log"
attempt=0
offline_attempts=0
for _ in $(seq 1 30); do
  attempt=$((attempt+1))
  state="$(timeout 5s "$adb_bin" -s "$serial" get-state 2>&1 || true)"
  printf 'attempt=%s state=%s\n' "$attempt" "$state" >> "$readiness_log"
  if [[ "$state" != device ]]; then offline_attempts=$((offline_attempts+1)); else offline_attempts=0; fi
  if [[ "$offline_attempts" == 8 && "${GITHUB_ACTIONS:-false}" == true ]]; then
    # A first-boot adbd restart can leave the emulator transport undiscovered.
    # Reset only the disposable GitHub runner's server, never a local PC server.
    timeout 5s "$adb_bin" kill-server >> "$readiness_log" 2>&1 || true
    timeout 5s "$adb_bin" start-server >> "$readiness_log" 2>&1 || true
  fi
  if timeout 10s "$adb_bin" -s "$serial" shell pm path android >> "$readiness_log" 2>&1 && timeout 10s "$adb_bin" -s "$serial" shell settings get global device_provisioned >> "$readiness_log" 2>&1; then
    ready=$((ready+1)); [[ "$ready" == 3 ]] && break
  else ready=0; fi
  sleep 2
done
[[ "$ready" == 3 ]] || { cat "$readiness_log"; timeout 10s "$adb_bin" -s "$serial" logcat -d > "$project_root/build/ci-artifacts/boot-logcat.log" 2>&1 || true; echo 'Emulator services failed to settle' >&2; exit 1; }
# Provision only the disposable emulator so its first-use wizard does not own HOME.
adb_device shell settings put global device_provisioned 1
adb_device shell settings put secure user_setup_complete 1
adb_device shell settings put secure tv_user_setup_complete 1
adb_device shell input keyevent 82
adb_device install -r "$project_root/QuietTV.apk"
android_jar="$sdk_root/platforms/android-28/android.jar"
"$build_tools/aapt" package -f -M "$project_root/tests/device/AndroidManifest.xml" -A "$project_root/docs/playback-rules" -I "$android_jar" -J "$out_dir/gen" -F "$out_dir/resources.apk"
find "$project_root/tests/device/src" -name '*.java' -print0 | xargs -0 javac --release 8 -encoding UTF-8 -classpath "$project_root/build/classes:$android_jar" -d "$out_dir/classes"
jar cf "$out_dir/classes.jar" -C "$out_dir/classes" .
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$android_jar" --classpath "$project_root/build/classes.jar" --min-api 23 --output "$out_dir/dex" "$out_dir/classes.jar"
cp "$out_dir/resources.apk" "$out_dir/unsigned.apk"
jar uf "$out_dir/unsigned.apk" -C "$out_dir/dex" classes.dex
"$build_tools/zipalign" -f 4 "$out_dir/unsigned.apk" "$out_dir/aligned.apk"
java -jar "$build_tools/lib/apksigner.jar" sign --ks "$project_root/local-test.jks" --ks-key-alias quiettv --ks-pass env:QUIETTV_KEYSTORE_PASSWORD --key-pass env:QUIETTV_KEYSTORE_PASSWORD --out "$out_dir/checks.apk" "$out_dir/aligned.apk"
adb_device install -r "$out_dir/checks.apk"
# Set HOME after both installs; stock TV policies may still override the preference.
if [[ "$image" != 'system-images;android-23;android-tv;x86' ]]; then
  adb_device shell cmd package set-home-activity --user 0 local.quiettv.launcher/.MainActivity
  adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
fi
for phase in fresh upgrade; do
  # startActivitySync must create an Activity, not reuse a previously opened
  # singleTask HOME instance created while the default HOME was being selected.
  adb_device shell am force-stop local.quiettv.launcher
  if [[ "$phase" == upgrade ]]; then
    adb_device install -r "$project_root/QuietTV.apk"
  fi
  timeout 120s "$adb_bin" -s "$serial" shell am instrument -w -e phase "$phase" local.quiettv.checks/local.quiettv.launcher.DeviceChecks | tee "$project_root/build/ci-artifacts/$phase.txt"
  grep -q "INSTRUMENTATION_RESULT: passed=$phase:" "$project_root/build/ci-artifacts/$phase.txt"
done
# Feature regressions use only synthetic apps, IDs, metadata and a locally encoded silent video.
for scenario in rules display settings; do
  if [[ "$scenario" == rules ]]; then adb_device shell pm revoke local.quiettv.launcher android.permission.READ_TV_LISTINGS; fi
  adb_device shell am force-stop local.quiettv.launcher
  runner=RuleChecks
  if [[ "$scenario" == display ]]; then runner=DisplayChecks; fi
  if [[ "$scenario" == settings ]]; then runner=SettingsChecks; fi
  timeout 240s "$adb_bin" -s "$serial" shell am instrument -w "local.quiettv.checks/local.quiettv.launcher.$runner" | tee "$project_root/build/ci-artifacts/$scenario.txt"
  grep -q 'INSTRUMENTATION_RESULT: passed=' "$project_root/build/ci-artifacts/$scenario.txt"
  if [[ "$scenario" == rules ]] && [[ "$(adb_device shell getprop ro.build.version.sdk | tr -d '\r')" -ge 26 ]]; then
    (
      trap 'adb_device shell pm revoke local.quiettv.launcher android.permission.READ_TV_LISTINGS; adb_device shell am broadcast -a local.quiettv.checks.CLEAR_TV_PROGRAMS -n local.quiettv.checks/.TvProgramFixture >/dev/null' EXIT
      adb_device shell am broadcast -a local.quiettv.checks.SEED_TV_PROGRAMS -n local.quiettv.checks/.TvProgramFixture | tee "$project_root/build/ci-artifacts/tv-provider-seed.txt"
      grep -q synthetic_programs_seeded "$project_root/build/ci-artifacts/tv-provider-seed.txt"
      adb_device shell pm grant local.quiettv.launcher android.permission.READ_TV_LISTINGS
      adb_device shell am force-stop local.quiettv.launcher
      timeout 120s "$adb_bin" -s "$serial" shell am instrument -w local.quiettv.checks/local.quiettv.launcher.TvHistoryChecks | tee "$project_root/build/ci-artifacts/tv-provider.txt"
      grep -q 'INSTRUMENTATION_RESULT: passed=' "$project_root/build/ci-artifacts/tv-provider.txt"
      adb_device shell am force-stop local.quiettv.launcher
      timeout 120s "$adb_bin" -s "$serial" shell am instrument -w local.quiettv.checks/local.quiettv.launcher.WatchNextChecks | tee "$project_root/build/ci-artifacts/watch-next.txt"
      grep -q 'INSTRUMENTATION_RESULT: passed=' "$project_root/build/ci-artifacts/watch-next.txt"
    )
  fi
done
if [[ "${QUIETTV_DOCUMENTATION_CAPTURE:-0}" == 1 ]]; then
  [[ "$image" == 'system-images;android-28;android-tv;x86' ]] || { echo 'Documentation capture requires the isolated API 28 image' >&2; exit 1; }
  # Only the disposable guest receives this synthetic, offline desktop setup.
  # Keep the released UI and builtin background, with no accounts or history.
  adb_device shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER > "$out_dir/documentation-tv-apps.txt"
  adb_device shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > "$out_dir/documentation-apps.txt"
  python3 - "$out_dir/documentation-tv-apps.txt" "$out_dir/documentation-apps.txt" "$out_dir/documentation-state.json" <<'PY'
import json,pathlib,re,sys
components='\n'.join(pathlib.Path(path).read_text() for path in sys.argv[1:3])
packages=sorted(set(re.findall(r'^\s*([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+\s*$',components,re.M)) - {'local.quiettv.launcher'})
favorites=packages[:5]
if not favorites:raise SystemExit('No documentation applications available')
print('Documentation setup uses %d installed, launchable applications' % len(packages))
def value(kind,data):return {'type':kind,'value':data}
state={'home':{'config_schema':value('int',1),'setup_seen':value('boolean',True),
    'wallpaper_kind':value('string','builtin'),'favorites_initialized':value('boolean',True),
    'favorites':value('set',favorites),'hidden':value('set',[]),
    'groups':value('string',json.dumps([{'id':'favorites','title':'常用应用','row':True},{'id':'all','title':'全部应用','row':False}],ensure_ascii=False))},
    'playback_history':{'config_schema':value('int',1),'enabled':value('boolean',False),
        'generic_enabled':value('boolean',False),'music_enabled':value('boolean',False),'records':value('string','[]')},'weather':{}}
pathlib.Path(sys.argv[3]).write_text(json.dumps(state,ensure_ascii=False),encoding='utf-8')
PY
  adb_device shell am broadcast -a local.quiettv.launcher.EXPORT_STATE -n local.quiettv.launcher/.LocalMaintenanceReceiver
  adb_device push "$out_dir/documentation-state.json" /sdcard/Android/data/local.quiettv.launcher/files/quiettv-state-backup.json
  adb_device shell am broadcast -a local.quiettv.launcher.IMPORT_STATE -n local.quiettv.launcher/.LocalMaintenanceReceiver
fi
# Instrumentation finishes its Activity. Reopen it explicitly for a private
# display capture; do not connect UiAutomation's unnecessary accessibility
# service or read app-private external storage through the API 30 shell.
adb_device shell am start -W -n local.quiettv.launcher/.MainActivity
foreground=0
for _ in $(seq 1 20); do
  # API 30 moves current focus into the display section, which the "windows"
  # subcommand omits. The full dump includes actual window focus on all APIs.
  adb_device shell dumpsys window > "$project_root/build/ci-artifacts/capture-window.txt"
  if grep -Eq 'mCurrentFocus=.*local\.quiettv\.launcher/' "$project_root/build/ci-artifacts/capture-window.txt"; then foreground=1; break; fi
  sleep 1
done
[[ "$foreground" == 1 ]] || { echo 'Launcher did not own the capture window' >&2; exit 1; }
if [[ "${QUIETTV_DOCUMENTATION_CAPTURE:-0}" == 1 ]]; then sleep 2; fi
adb_device exec-out screencap -p > "$project_root/build/ci-artifacts/ci-fresh.png"
python3 - "$project_root/build/ci-artifacts/ci-fresh.png" <<'PY'
import pathlib,sys
capture=pathlib.Path(sys.argv[1]).read_bytes()
if not capture.startswith(b'\x89PNG\r\n\x1a\n') or len(capture)<100:
    raise SystemExit('Emulator capture export is not a valid PNG')
print('Private emulator PNG verified; public CI artifacts contain logs only.')
PY
adb_device logcat -d -s AndroidRuntime:E > "$project_root/build/ci-artifacts/crashes.txt"
# Some stock images crash their own Live Channels app before our install.
# Preserve those logs, but fail for a launcher or test-provider process crash.
if grep -Eq 'Process: local\.quiettv\.(launcher|checks)(:|,)' "$project_root/build/ci-artifacts/crashes.txt"; then
  echo 'QuietTV process crashed' >&2
  exit 1
fi
