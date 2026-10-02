#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.uplay.video"
ACTIVITY="$PACKAGE/.MainActivity"
DIAGNOSTICS="app/build/diagnostics"

mkdir -p "$DIAGNOSTICS"

collect_diagnostics() {
  adb logcat -d -v threadtime > "$DIAGNOSTICS/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$DIAGNOSTICS/activities.txt" 2>&1 || true
  adb shell screencap -p /sdcard/uplay-startup.png >/dev/null 2>&1 || true
  adb pull /sdcard/uplay-startup.png "$DIAGNOSTICS/startup-screen.png" >/dev/null 2>&1 || true
}
trap collect_diagnostics EXIT

test -s "$APK"
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 6

if ! adb shell pidof "$PACKAGE" > "$DIAGNOSTICS/pid.txt" 2>&1; then
  echo "UPlay process exited during startup." >&2
  exit 1
fi

adb shell dumpsys activity activities | grep -F "$PACKAGE/.MainActivity" > "$DIAGNOSTICS/resumed-activity.txt"
echo "UPlay startup smoke test passed."
