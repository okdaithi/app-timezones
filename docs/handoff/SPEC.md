# Perth ⇄ Galway Dual Clock: Product & Technical Spec

Status: ready for implementation · Source design: Claude Design canvas "Perth–Galway Dual Clock" (files in `design/canvas/`)

## 1. Purpose

An Android home-screen widget for someone who lives between Perth, Western Australia and Galway, Ireland. It shows both local times and whether now is a reasonable time to call, meaning both cities are inside calling hours. The app also serves as a teaching reference for Jetpack Glance and correct time-zone handling. Keep the code clean and commented at the points listed in §7.

## 2. Fixed facts (do not re-derive)

| Item | Value |
|---|---|
| Perth zone | `Australia/Perth`. UTC+08:00 all year, no DST, abbreviation AWST |
| Galway zone | `Europe/Dublin`. IST (UTC+01:00) from the last Sunday of March to the last Sunday of October; otherwise GMT (UTC+00:00). Changes at 01:00 UTC |
| Gap | Galway is 7 h behind Perth during IST and 8 h behind during GMT |
| Default calling hours | 08:00 (inclusive) to 21:00 (exclusive), local, in **both** cities |
| Resulting window, IST | 15:00–21:00 Perth = 08:00–14:00 Galway |
| Resulting window, GMT | 16:00–21:00 Perth = 08:00–13:00 Galway |
| Next clock changes | 2026-10-25T01:00Z (to GMT); 2027-03-28T01:00Z (to IST) |

## 3. Functional requirements

### 3.1 Widget
- **F1** Show the current time of both cities as HH:mm, always 24-hour, whatever the device's 12/24 h setting.
- **F2** The minute digits must tick without app code running. Use `TextClock` (inside `AndroidRemoteViews`) with `android:timeZone` set to the city's IANA id.
- **F3** Order: primary city first (left, or top on SMALL). Primary is a setting: Perth (default) or Galway.
- **F4** Each city has a coloured dot, an uppercase label and, on MEDIUM sizes, a date line: `Fri 25 Sep · IST`. When the secondary city's date differs from the primary's, append ` · a day behind` or ` · a day ahead`. On SMALL, append the weekday to the label instead: `GALWAY · FRI`.
- **F5** Band (MEDIUM sizes): a 24 h track along the **primary** city's clock, a green segment for the call window, and a white marker at now. MEDIUM_TALL adds tick rows every 3 h: primary hours above in the primary colour, secondary hours below in the secondary colour.
- **F6** Status:
  - Open → `Good time to call` / `Until 21:00` (time on the primary clock) / short `Call now`
  - Closed → `Too early in Galway` or `Too late in Galway` (secondary city is checked first, then primary) / `Opens 15:00` / short `Opens 15:00`
  - No overlap (settings too narrow) → `No shared hours` / `Adjust calling hours` / short `No overlap`
- **F7** Responsive sizes: see §5.
- **F8** Tapping anywhere opens the app.
- **F9** Accessibility: the widget root carries a content description such as `Perth 18:04, Galway 11:04. Good time to call. Until 21:00.`

### 3.2 App (single activity, Jetpack Compose)
- **A1** Settings: open hour (0–23), close hour (1–24, must be greater than the open hour), and primary city. Persist with DataStore Preferences. Changes apply to all placed widgets immediately by calling `refreshAll`.
- **A2** Live preview of the MEDIUM widget, plus the explanation panel from `design/canvas/Main.dc.html`: offsets, gap, window in both clocks, and the next clock change with the resulting new window.
- **A3** Optional but recommended, since this is a teaching app: a debug-only "simulate time" control that offsets `now` in the in-app preview. It must never affect the real widget.
- **A4** A light theme using the app tokens in `design/tokens.json`. The widget is always dark.

### 3.3 Refresh behaviour
- **R1** One entry point, `refreshAll(context)`. It writes settings and a tick into each widget's Glance state, calls `update`, then schedules the next refresh. If no widgets are placed, it cancels the alarm.
- **R2** The next refresh is the earliest of: the next window edge, the next clock change in either zone, the next midnight in Perth, the next midnight in Galway, and now + 15 min (marker cadence). This is `core.nextRefresh`.
- **R3** Use an `AlarmManager.setWindow(RTC, t, 60 s)` alarm: inexact and non-waking. Do **not** use exact alarms, `RTC_WAKEUP`, WorkManager periodic work, or `updatePeriodMillis` (set it to 0).
- **R4** Also refresh on `TIMEZONE_CHANGED`, `TIME_SET`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, widget `onUpdate`, and settings changes. Cancel on `onDisabled`.

## 4. Deliberate deviations from the canvas (implement these, not the canvas)
1. **Countdowns are replaced by clock times on the widget.** The canvas shows "Closes in 2h 56m" and "Opens in 8h 20m". A countdown goes stale every minute, and a Glance widget cannot refresh that often. The widget shows `Until 21:00` and `Opens 15:00` instead. Countdowns are fine inside the app.
2. **MEDIUM (250×110 dp) drops the tick rows.** They do not fit at 110 dp height. MEDIUM_TALL (≥150 dp) shows them.
3. **The "now" marker moves in 15-minute steps**, not continuously. On a roughly 280 dp track, 15 min is about 3 dp.
4. **Widget labels use the system sans-serif.** Glance `Text` cannot load bundled fonts. Only the `TextClock` digits use DM Mono, through the XML layout.
5. **Dates use fixed English abbreviations** (`Fri 25 Sep`), not locale formatting. Locale-based `MMM` produces `Sept` on newer CLDR data (verified), which breaks the layout.
6. **Abbreviations are hard-mapped** (AWST, IST or GMT, else `UTC±hh:mm`). Never use the `zzz` pattern; its output varies by device.

## 5. Visual spec

Tokens: `design/tokens.json`. Visual reference: `design/preview/sizes.png` and `design/preview/sizes.html`. The previews render with fallback fonts; the real fonts are DM Mono, Instrument Sans and Instrument Serif.

| Size key | Min dp | Contents | Clock sp |
|---|---|---|---|
| SMALL | 110×110 | label + clock × 2 stacked, status dot + short status | 28 |
| WIDE | 250×60 | two label + clock blocks in a row; right: dot + short status, plus "Until HH:mm" when open | 26 |
| MEDIUM | 250×110 | two columns: label, clock, date line; band; status row (title left, detail right) | 34 |
| MEDIUM_TALL | 250×150 | MEDIUM plus tick rows above and below the band | 34 |

Other values: padding 16 dp (SMALL 14), city label 11 sp medium in the muted colour, 8 dp dots, band track 8 dp, marker 2×12 dp, status title 13 sp medium, detail 11 sp muted. Corner radius: the system widget radius on API 31+ (`appWidgetBackground()` + `cornerRadius`).

## 6. Technical constraints
- Kotlin 2.x with the Compose compiler plugin; `minSdk 26` (native `java.time`, font resources in RemoteViews); `targetSdk` and `compileSdk` = latest stable.
- Dependencies: `androidx.glance:glance-appwidget` (latest stable 1.1.x or newer), `androidx.datastore:datastore-preferences`, Compose BOM, `kotlinx-coroutines-android`, JUnit 4 for `:core` tests.
- Modules: `:core` is a pure Kotlin/JVM library with no Android dependency and holds all time logic. `:app` holds the widget, receivers and settings UI. Every string and fraction on the widget comes from `core.buildModel`; layouts contain no time arithmetic.
- **Never call `ZoneId.systemDefault()`, `TimeZone.getDefault()`, `LocalDateTime.now()` or `LocalDate.now()`** anywhere. Always use `Instant.now()` plus an explicit zone. Enforce this with a lint check or a grep test in CI.
- Never persist a UTC offset. Compute it from `ZoneRules` on every refresh, since tzdata updates with the OS.
- No network, no analytics, no permissions beyond `RECEIVE_BOOT_COMPLETED`.

## 7. Comments required in code (teaching value)
- At `ZoneClock`: why TextClock and not recomposition.
- At `windowOn`: why the offset is evaluated at the window's own time (clock-change day).
- At `scheduleNext`: why RTC and inexact, and why no exact-alarm permission is needed.
- At the manifest receiver: why `exported=true` is safe for protected system broadcasts.

## 8. Acceptance criteria
1. `:core` unit tests pass: the supplied `CallWindowTest` (14 tests, verified on JVM 21 / Kotlin 2.1.21), plus any you add.
2. On an emulator with the widget placed, the digits tick each minute for both cities with the app process killed.
3. With the device zone switched between Australia/Perth, Europe/Dublin and America/New_York, the widget shows the same Perth and Galway times and the same window. The zone change triggers a refresh within a few seconds.
4. With the device clock set to 2026-10-25 00:50 UTC and 20 minutes allowed to pass, Galway digits jump 01:5x IST → 01:0x GMT, and the band and status move to the 16:00–21:00 Perth window.
5. At a window edge (e.g. 21:00 Perth), the status flips within about 1 minute of the edge while the screen is on.
6. After a reboot, the band keeps updating without the app being opened.
7. At 00:00 Galway, the Galway date line and the "a day behind" note change within about 1 minute (screen on).
8. Resizing through all four sizes shows the correct layout and nothing clips at the minimum size of each bucket.
9. TalkBack reads the content description from F9.
10. Changing settings in the app updates every placed widget immediately.
11. Grepping the repo for `systemDefault|getDefault()|LocalDateTime.now|LocalDate.now` finds no matches outside tests.
