#!/usr/bin/env bash
# Run from android/. Keep the actual Gradle status and collect before emulator shutdown.
set -uo pipefail
./gradlew --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedDebugAndroidTest
test_status=$?
mkdir -p app/build/reports/appearance-screenshots
if ! adb pull /sdcard/Android/data/br.com.pevicasi.nfefacil/files/appearance-evidence/. app/build/reports/appearance-screenshots/; then
  echo '::warning::Native appearance screenshots could not be collected'
fi
# Explicitly filter only non-personal timing messages authored by this app.
adb logcat -d -s NfeScanTiming:D '*:S' > app/build/reports/appearance-screenshots/scanner-timing.log
exit "$test_status"
