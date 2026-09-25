#!/usr/bin/env bash
# Automates the SPEC §8 acceptance criteria that can be observed from adb on an emulator.
# Expects a booted emulator with the debug APK and the androidTest APK installed.
# Observations come from the one-line "DualClock" log that refreshAll writes, plus dumpsys alarm.
# Criteria that need eyes on a launcher (2, 8, 9) are reported as MANUAL; see VERIFICATION.md.
#
# Usage: scripts/emulator-checks.sh [results.md]
set -uo pipefail

PKG=com.dg.dualclock
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
OUT="${1:-emulator-results.md}"
FAILED=0

echo "| # | Criterion | Result | Evidence |" > "$OUT"
echo "|---|---|---|---|" >> "$OUT"

record() { # num, name, PASS|FAIL|MANUAL, evidence
    local ev="${4//|/\\|}"
    echo "| $1 | $2 | $3 | \`${ev:0:300}\` |" >> "$OUT"
    echo "[$3] $1 $2 :: $4"
    [[ "$3" == FAIL ]] && FAILED=1
    return 0
}

adbsh() { adb shell "$@" | tr -d '\r'; }
log() { adb logcat -d -s DualClock:I | tr -d '\r' | grep 'refresh\['; }

# wait_log <regex> <timeout_s>: prints the first matching refresh line.
wait_log() {
    local deadline=$((SECONDS + $2)) line
    while (( SECONDS < deadline )); do
        line=$(log | grep -E "$1" | head -1)
        [[ -n "$line" ]] && { echo "$line"; return 0; }
        sleep 2
    done
    return 1
}

epoch_ms() { date -u -d "$1" +%s%3N; }

set_time() { # ISO-8601 UTC
    adb logcat -c
    adbsh cmd alarm set-time "$(epoch_ms "$1")" >/dev/null
}

alarm_lines() { adbsh dumpsys alarm | grep -E '(RTC|ELAPSED)[_A-Z]* #[0-9]+: Alarm\{.*com\.dg\.dualclock'; }

check_one_rtc_alarm() {
    local lines n
    lines=$(alarm_lines)
    n=$(grep -c . <<<"$lines")
    if [[ "$n" == 1 ]] && ! grep -q 'WAKEUP' <<<"$lines"; then echo "ok: $lines"; return 0; fi
    echo "expected exactly one non-wakeup RTC alarm, found $n: $lines"
    return 1
}

field() { sed -nE "s/.*$1 ([0-9]{2}:[0-9]{2}).*/\1/p" <<<"$2"; }
minutes() { echo $((10#${1%%:*} * 60 + 10#${1##*:})); }
band() { sed -nE 's/.*band=([^ ]+).*/\1/p' <<<"$1"; }

# ---- setup ---------------------------------------------------------------------------------
adbsh settings put global auto_time 0
adbsh settings put global auto_time_zone 0
adbsh svc power stayon true
adbsh input keyevent KEYCODE_WAKEUP
adbsh appwidget grantbind --package "$PKG" --user 0

adb logcat -c
place=$(adb shell am instrument -w -e class "$PKG.WidgetHostTest#placeWidget" "$RUNNER" | tr -d '\r')
if ! grep -q 'OK (1 test)' <<<"$place"; then
    record 0 "Place widget via AppWidgetHost" FAIL "$place"
    cat "$OUT"; exit 1
fi
record 0 "Place widget via AppWidgetHost" PASS "$(log | tail -1)"

# ---- 1 & 11 run in the build job -----------------------------------------------------------
record 1 ":core unit tests" "n/a here" "build job: ./gradlew :core:test"
record 11 "Forbidden-API grep" "n/a here" "build job: scripts/check-forbidden-apis.sh"

# ---- 2: digits tick with the process dead ---------------------------------------------------
adbsh am kill "$PKG"
record 2 "Digits tick with app process killed" MANUAL "needs a launcher; TextClock renders in the host process"

# ---- 3: device zone does not change the widget ----------------------------------------------
expected_gap=$(python3 -c "
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
n = datetime.now(timezone.utc)
d = n.astimezone(ZoneInfo('Australia/Perth')).utcoffset() - n.astimezone(ZoneInfo('Europe/Dublin')).utcoffset()
print(int(d.total_seconds() // 60))")
adbsh cmd alarm set-timezone Etc/UTC >/dev/null # start elsewhere so the first switch really changes the zone
gaps=() bands=() evidence="" ok=1
for tz in Australia/Perth Europe/Dublin America/New_York; do
    # Each switch sends TIMEZONE_CHANGED to every app that listens for it, cold-starting them
    # one by one. Drain the previous switch's backlog first, so the 10 s bound times our
    # refresh and not other apps' receivers.
    timeout 120 adb shell am wait-for-broadcast-idle >/dev/null 2>&1 || sleep 15
    adb logcat -c
    adbsh cmd alarm set-timezone "$tz" >/dev/null
    if line=$(wait_log 'refresh\[(TIMEZONE_CHANGED|TIME_SET)\]' 10); then
        p=$(field Perth "$line"); g=$(field Galway "$line")
        gaps+=($(( ( $(minutes "$p") - $(minutes "$g") + 1440 ) % 1440 )))
        bands+=("$(band "$line")")
        evidence+="$tz: Perth $p Galway $g band=$(band "$line"); "
    else
        ok=0; evidence+="$tz: no refresh within 10 s; "
    fi
done
if (( ok )) && [[ "${gaps[0]}" == "$expected_gap" && "${gaps[1]}" == "$expected_gap" && "${gaps[2]}" == "$expected_gap" \
      && "${bands[0]}" == "${bands[1]}" && "${bands[1]}" == "${bands[2]}" ]]; then
    record 3 "Device zone switch: same times, same window, refresh < 10 s" PASS "$evidence"
else
    record 3 "Device zone switch: same times, same window, refresh < 10 s" FAIL "expected gap $expected_gap min; $evidence"
fi
alarm=$(check_one_rtc_alarm) && record "R3" "Exactly one pending RTC (non-wakeup) alarm" PASS "$alarm" \
    || record "R3" "Exactly one pending RTC (non-wakeup) alarm" FAIL "$alarm"

# ---- 5: window edge at 21:00 Perth ----------------------------------------------------------
set_time 2026-09-25T12:58:00Z
before=$(wait_log 'refresh\[TIME_SET\].*Until 21:00' 15)
after=$(wait_log 'refresh\[REFRESH\].*Too late in Perth\. Opens 15:00' 200)
if [[ -n "$before" && -n "$after" ]]; then
    record 5 "Status flips at 21:00 Perth within ~1 min" PASS "$after"
else
    record 5 "Status flips at 21:00 Perth within ~1 min" FAIL "before=$before after=$after"
fi

# ---- 7: midnight in Galway ------------------------------------------------------------------
set_time 2026-09-25T22:58:00Z
before=$(wait_log 'refresh\[TIME_SET\].*Fri 25 Sep · IST · a day behind' 15)
after=$(wait_log 'refresh\[REFRESH\].*\| Sat 26 Sep · IST \|' 200)
if [[ -n "$before" && -n "$after" ]]; then
    record 7 "Galway date line changes at 00:00 Galway" PASS "$after"
else
    record 7 "Galway date line changes at 00:00 Galway" FAIL "before=$before after=$after"
fi

# ---- 4: Irish clock change, 2026-10-25 01:00Z ------------------------------------------------
set_time 2026-10-25T00:58:00Z
before=$(wait_log 'refresh\[TIME_SET\].*Galway 01:5[0-9].*IST.*band=900\.\.1259' 15)
after=$(wait_log 'refresh\[REFRESH\].*Galway 01:0[0-9].*GMT.*band=960\.\.1259' 200)
if [[ -n "$before" && -n "$after" ]]; then
    record 4 "IST→GMT: Galway 01:5x→01:0x, window 16:00–21:00 Perth" PASS "$after"
else
    record 4 "IST→GMT: Galway 01:5x→01:0x, window 16:00–21:00 Perth" FAIL "before=$before after=$after"
fi

# ---- 10: settings reach every widget --------------------------------------------------------
settings=$(adb shell am instrument -w -e class "$PKG.WidgetHostTest#settingsReachEveryWidget" "$RUNNER" | tr -d '\r')
if grep -q 'OK (1 test)' <<<"$settings"; then
    record 10 "Settings change updates every placed widget" PASS "WidgetHostTest#settingsReachEveryWidget"
else
    record 10 "Settings change updates every placed widget" FAIL "$settings"
fi

# ---- 8, 9: need a launcher / TalkBack --------------------------------------------------------
record 8 "Resize through four sizes without clipping" MANUAL "needs a launcher"
record 9 "TalkBack reads the F9 description" MANUAL "description logged: $(log | tail -1 | cut -d'|' -f2)"

# ---- 6: reboot (last: the emulator restarts) -------------------------------------------------
adb reboot
adb wait-for-device
for _ in $(seq 1 90); do [[ "$(adbsh getprop sys.boot_completed)" == 1 ]] && break; sleep 2; done
adbsh input keyevent KEYCODE_WAKEUP
if line=$(wait_log 'refresh\[BOOT_COMPLETED\]' 120) && alarm=$(check_one_rtc_alarm); then
    record 6 "After reboot the band keeps updating (alarm re-armed)" PASS "$line"
else
    record 6 "After reboot the band keeps updating (alarm re-armed)" FAIL "line=${line:-none} alarm=${alarm:-none}"
fi

adbsh settings put global auto_time 1
adbsh settings put global auto_time_zone 1

cat "$OUT"
exit $FAILED
