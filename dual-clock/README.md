# Perth ⇄ Galway dual clock

An Android home-screen widget (Jetpack Glance) showing Perth and Galway time and when a call suits both cities. The spec and design handoff are in [`../docs/handoff/`](../docs/handoff/README.md). `SPEC.md` there is authoritative.

| Module | Contents |
|---|---|
| `:core` | Pure Kotlin/JVM. All time logic: call window, refresh schedule, widget model, in-app explanation. Unit-tested. |
| `:app` | Glance widget, refresh pipeline (`widget/Refresh.kt`), DataStore settings, Compose settings and preview screen. |

```sh
./gradlew :core:test                 # time logic
./gradlew :app:assembleDebug         # needs the Android SDK
scripts/check-forbidden-apis.sh      # SPEC §6 guard
scripts/emulator-checks.sh           # SPEC §8 on a booted emulator; see VERIFICATION.md
```

Fonts: DM Mono, Instrument Sans and Instrument Serif are not bundled yet. Search for `TODO(font)`.
