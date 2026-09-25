#!/usr/bin/env bash
# SPEC §6 / acceptance 11: the device's default zone must never leak into the widget.
# Fails if any non-test Kotlin/Java/Gradle source uses a zone-less "now" or the default zone.
set -euo pipefail
cd "$(dirname "$0")/.."

pattern='systemDefault|getDefault\(\)|LocalDateTime\.now|LocalDate\.now'

matches=$(grep -rnE "$pattern" \
    --include='*.kt' --include='*.java' --include='*.kts' \
    --exclude-dir=build --exclude-dir=test --exclude-dir=androidTest \
    . || true)

if [[ -n "$matches" ]]; then
    echo "Forbidden time-zone API found (use Instant.now() plus an explicit ZoneId):" >&2
    echo "$matches" >&2
    exit 1
fi

# SPEC R3: inexact, non-waking alarms only; no WorkManager polling; updatePeriodMillis=0.
alarms=$(grep -rnE 'AlarmManager\.RTC_WAKEUP|setExact|setAlarmClock|androidx\.work' \
    --include='*.kt' --include='*.kts' --exclude-dir=build app/src/main app/build.gradle.kts || true)
if [[ -n "$alarms" ]]; then
    echo "Forbidden scheduling API found (SPEC R3):" >&2
    echo "$alarms" >&2
    exit 1
fi
grep -q 'android:updatePeriodMillis="0"' app/src/main/res/xml/dual_clock_info.xml \
    || { echo "dual_clock_info.xml must set updatePeriodMillis=\"0\"" >&2; exit 1; }

# SPEC §6: no permissions beyond RECEIVE_BOOT_COMPLETED.
perms=$(grep -oE 'uses-permission android:name="[^"]+"' app/src/main/AndroidManifest.xml \
    | grep -v 'android.permission.RECEIVE_BOOT_COMPLETED' || true)
if [[ -n "$perms" ]]; then
    echo "Unexpected permission(s): $perms" >&2
    exit 1
fi

echo "OK: no forbidden time-zone, alarm or permission usage."
