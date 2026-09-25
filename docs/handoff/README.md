# Handoff: Perth ⇄ Galway Dual Clock (Android)

Brief for the coding agent. Read in this order.

1. **`SPEC.md`**: requirements, deviations from the design, and acceptance criteria. It is authoritative. Where the spec and the design canvas disagree, follow the spec (see §4 of the spec).
2. **`design/preview/sizes.png`** and **`build-notes.png`**: what the widget looks like, and the architecture rationale. (The previews use fallback fonts.)
3. **`design/tokens.json`**: colours, sizes and type.
4. **`reference/core/`**: the time logic, finished and tested. Copy it into a `:core` module as-is.
5. **`reference/app/`**: an Android reference sketch (Glance widget, refresh pipeline, settings, XML). It has not been compiled. Adapt its imports and signatures to the library versions you resolve.
6. **`design/canvas/`**: the raw design source (Claude Design `.dc.html`). `Main.dc.html` contains a JavaScript prototype of the same logic in its `<script>` block, which is useful for building the in-app preview. These files need the canvas runtime to render, so read them as source.

## Task

Build a Gradle Android project, `dual-clock/`, with modules `:core` (Kotlin/JVM) and `:app`, that meets every acceptance criterion in `SPEC.md` §8.

## Build plan

1. Scaffold the Gradle project with a version catalog: Kotlin 2.x, the Compose compiler plugin, AGP latest stable, minSdk 26. Package: `com.dg.dualclock`.
2. `:core`: copy `reference/core/src/**`, add the JUnit 4 dependency, and run `./gradlew :core:test`. All 14 tests must pass before you continue.
3. `:app` resources: copy `reference/app/src/main/res/**`. Download **DM Mono Regular** (SIL OFL) from Google Fonts into `res/font/dm_mono_regular.ttf`, and include `OFL.txt` in `app/src/main/assets/licenses/`. Create `layout/widget_preview.xml` (a static MEDIUM rendering for the picker) and `drawable/widget_preview` (a PNG export of it).
4. Widget: adapt `reference/app/.../widget/DualClockWidget.kt`. Keep `buildModel` as the only source of strings. Fix any Glance API drift, for example `ColorProvider` imports and `cornerRadius` availability.
5. Refresh pipeline: adapt `Refresh.kt`, and wire `onUpdate` and `onDisabled` in `DualClockReceiver` as described in its bottom comment. Merge `AndroidManifest.snippet.xml`.
6. App: a Compose `MainActivity` with the settings (SPEC A1) and the preview and explanation panel (A2, A3), styled from the app tokens.
7. CI: add a grep or lint guard for the forbidden APIs (SPEC §6 and acceptance 11), and a GitHub Actions workflow running `:core:test` and `:app:assembleDebug`.
8. Verify each item in SPEC §8 on an emulator (API 34 or later). Record the results in `VERIFICATION.md` in the repo: pass or fail for each criterion, with the adb commands used. Useful commands:
   - `adb shell cmd alarm set-timezone Europe/Dublin` (on older images, `adb shell service call alarm 3 s16 Europe/Dublin`)
   - `adb shell cmd alarm set-time <epoch_ms>` for the clock-change test (disable automatic time first: `adb shell settings put global auto_time 0`)
   - `adb shell dumpsys alarm | grep -A3 com.dg.dualclock` to confirm exactly one pending RTC alarm
   - `adb reboot` for acceptance 6

## Hard rules
- Keep all time arithmetic in `:core`. Layouts only read `WidgetModel`.
- No `ZoneId.systemDefault()` or `TimeZone.getDefault()`, and no `LocalDate(Time).now()` without a zone.
- No exact alarms, no wake-up alarms, no WorkManager periodic polling, and `updatePeriodMillis=0`.
- No network, analytics or extra permissions.
- Do not change the `CallWindowTest` expectations. If one fails, the implementation is wrong.

## Contents

```
README.md                     this brief
SPEC.md                       requirements + acceptance criteria
design/tokens.json            colours, type, sizes
design/preview/*.html|png     standalone renders of the Sizes and Build-notes boards
design/canvas/*               original Claude Design source (canvas.json + .dc.html boards)
reference/core/…              CallWindow.kt + CallWindowTest.kt (tested, 14/14 pass)
reference/app/…               Glance widget, refresh pipeline, settings, XML (sketch, not compiled)
```
