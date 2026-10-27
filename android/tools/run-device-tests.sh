#!/usr/bin/env bash
# Run from android/. Keep the actual Gradle status and collect before emulator shutdown.
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest
test_status=$?
mkdir -p app/build/reports/appearance-screenshots
if ! adb pull /sdcard/Android/data/br.com.pevicasi.nfefacil/files/appearance-evidence/. app/build/reports/appearance-screenshots/; then
  echo '::warning::Native appearance screenshots could not be collected'
fi
exit "$test_status"
