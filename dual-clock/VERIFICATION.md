# Verification: SPEC §8 acceptance criteria

Status on 2026-09-25. CI results are from [run 36142041579](https://github.com/okdaithi/app-timezones/actions/runs/36142041579) on commit `b344e08` (API 34 emulator).

**Environment limits.** The authoring container could not reach `dl.google.com` or `maven.google.com`, so it had no Android SDK, no AndroidX artifacts and no KVM. As a result:
- Only `:core` was compiled and tested locally.
- `:app` targets Glance 1.1.1, Compose BOM 2024.12.01 and AGP 8.7.3. It compiles in CI (`build` job).
- The emulator criteria are automated in `scripts/emulator-checks.sh` and run by the `emulator` CI job (API 34, google_apis, x86_64). That job publishes a results table to the run summary and as the `emulator-results` artifact.

| # | Criterion | Status | How |
|---|---|---|---|
| 1 | `:core` unit tests pass | **PASS** (local + CI) | 14/14 `CallWindowTest` + 6/6 `ExplainTest` |
| 2 | Digits tick with the app process killed | MANUAL | See below. CI has no launcher to render the TextClock |
| 3 | Device zone switch leaves times and window unchanged; refresh within seconds | **PASS** (CI) | Perth, Dublin and New York device zones all showed Perth 21:40 / Galway 14:40, `band=900..1259`, refresh < 10 s |
| 4 | 2026-10-25 01:00Z: Galway 01:5x IST → 01:0x GMT, window → 16:00–21:00 Perth | **PASS** (CI) | `Galway 01:00 … Sun 25 Oct · GMT … band=960..1259`, status `Opens 16:00` |
| 5 | Status flips within ~1 min of 21:00 Perth | **PASS** (CI) | Refresh at 21:00:02 Perth: `Too late in Perth. Opens 15:00.` |
| 6 | Band keeps updating after reboot without opening the app | **PASS** (CI) | `refresh[BOOT_COMPLETED]`, one RTC alarm re-armed |
| 7 | Galway date line and "a day behind" change at 00:00 Galway | **PASS** (CI) | Refresh at 00:00:16 Galway: `Sat 26 Sep · IST`, note gone |
| 8 | Four sizes, no clipping at each bucket's minimum | MANUAL | See below |
| 9 | TalkBack reads the F9 description | MANUAL | See below. CI logs the description string |
| 10 | Settings change updates every placed widget | **PASS** (CI) | `WidgetHostTest#settingsReachEveryWidget` |
| R3 | Exactly one pending alarm, `RTC` (type 1), never `RTC_WAKEUP` | **PASS** (CI) | `dumpsys alarm`: `RTC #16: Alarm{… type 1 … com.dg.dualclock}` |
| 11 | No `systemDefault`/`getDefault()`/zone-less `now` outside tests | **PASS** (local + CI) | `scripts/check-forbidden-apis.sh`, which also checks R3 (no exact or wake-up alarms, no WorkManager, `updatePeriodMillis=0`) and the permission list |

Criterion 3 failed once before commit `b344e08`. The Etc/UTC pre-switch's broadcast backlog delayed the next refresh. The script now waits for `am wait-for-broadcast-idle` before each switch.

## How the CI job observes the widget

- **Placing the widget.** There is no launcher in CI. `WidgetHostTest#placeWidget` binds a widget to a private `AppWidgetHost` after `adb shell appwidget grantbind --package com.dg.dualclock`. The binding outlives the test process.
- **Reading its state.** Every `refreshAll` writes one log line, which the script reads with `adb logcat -s DualClock:I`:
  ```
  refresh[<reason>] widgets=N | <F9 description> | <primary date line> | <secondary date line> | band=<callWindow minutes> | next=<instant>
  ```
- **Checking the alarm.** `adb shell dumpsys alarm` must show exactly one `com.dg.dualclock` alarm, and it must not be a wake-up alarm.

## Commands

```sh
adb shell settings put global auto_time 0          # allow manual time
adb shell cmd alarm set-timezone Europe/Dublin     # older images: adb shell service call alarm 3 s16 Europe/Dublin
adb shell cmd alarm set-time $(date -u -d 2026-10-25T00:58:00Z +%s%3N)
adb logcat -s DualClock:I
adb shell dumpsys alarm | grep -A3 com.dg.dualclock
adb reboot
```

## Manual procedure (2, 8, 9)

2. Place the widget on a launcher home screen. Run `adb shell am kill com.dg.dualclock` and confirm with `adb shell pidof com.dg.dualclock` (no output). Watch both clocks across a minute boundary: both must advance.
8. Resize the widget to about 110×110, 250×60, 250×110 and 250×150 dp. Check each layout against `docs/handoff/design/preview/sizes.png`. **Risk:** at exactly 250×110 dp, the MEDIUM content (label, 34 sp clock, date line, band, status row, 12 dp vertical padding) adds up to about 110–120 dp. Check the status row for clipping first.
9. Turn on TalkBack and focus the widget. It should read, for example, "Perth 18:04, Galway 11:04. Good time to call. Until 21:00."
