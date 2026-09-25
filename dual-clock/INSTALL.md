# Installing Perth ⇄ Galway

This covers sideloading the Android app and adding its home-screen widget. The app is not on Google Play.

**Requirements:** Android 8.0 (API 26) or later. The app needs no accounts or network access. Its only permission lets it restart its refresh timer after a reboot.

## 1. Get the APK

Pick one of these.

### A. Download from GitHub Actions (no tools needed)

1. Sign in to GitHub. Artifacts are only visible to signed-in users with access to the repository.
2. Open **https://github.com/okdaithi/app-timezones/actions/workflows/dual-clock.yml**.
3. Filter by branch `main` and open the newest run with a green check.
4. Under **Artifacts**, download **apks**.
5. Unzip it. You need `debug/app-debug.apk`. Ignore the `androidTest` APK: it contains the CI tests.

Artifacts expire after 90 days. If none are left, re-run the workflow from the same page with **Run workflow**.

### B. Build it yourself

Needs JDK 17 and the Android SDK (Android Studio installs both).

```sh
git clone https://github.com/okdaithi/app-timezones
cd app-timezones/dual-clock
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## 2. Install it on the phone

### From the phone itself

1. Copy `app-debug.apk` to the phone, for example with Google Drive, email or a USB cable.
2. Open it in the Files app.
3. Android asks to allow installs from that source. Tap **Settings**, turn on **Allow from this source**, then go back.
4. Tap **Install**.
5. Google Play Protect may warn about an unknown app. Tap **More details → Install anyway**. The warning appears because the APK is a debug build that isn't on Google Play.

### From a computer with adb

1. On the phone, open **Settings → About phone** and tap **Build number** seven times to unlock developer options.
2. Open **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone, accept the prompt that appears on it, then run:

```sh
adb install -r app-debug.apk
```

## 3. Open the app once

Launch **Perth ⇄ Galway** from the app drawer. Android keeps a newly installed app inactive until it is opened, so skipping this step can stop the widget from refreshing after a reboot.

The app shows:
- a live preview of the widget
- the current UTC offsets, the time gap and today's call window
- the next clock change

Set your preferences here:
- **Calling hours:** the open and close hour. The defaults are 08:00–21:00 local, in both cities.
- **Primary city:** the city shown on the left, whose clock the band follows. The default is Perth.

Changes apply to every placed widget immediately.

## 4. Add the widget

1. Long-press an empty area of the home screen and tap **Widgets**.
2. Find **Perth ⇄ Galway**, then drag it onto the home screen.
3. Long-press the widget and drag its handles to resize it. The layout changes with the size:

| Size | Shows |
|---|---|
| Small (about 2×2 cells) | Both clocks stacked, plus a short status |
| Wide (4×1) | Both clocks in a row, the status, and "Until HH:mm" while calling is open |
| Medium (4×2) | Clocks, date lines, the 24 h band with its green call window, and the status line |
| Tall (4×3) | Medium plus hour ticks above and below the band |

Tap the widget to open the app.

## Reading the widget

- **Green band:** the hours when both cities are inside calling hours, on the primary city's clock.
- **White line:** now. It moves in 15-minute steps. The clocks tick every minute.
- **Status:**
  - `Good time to call · Until 21:00`: calling is open until that time.
  - `Too late in Galway · Opens 15:00` or `Too early …`: the named city is outside calling hours. The time says when the window opens next.
  - `No shared hours · Adjust calling hours`: with your settings, the two cities' hours never overlap. Widen them in the app.
- **"a day behind" / "a day ahead":** the other city is on a different date.

Ireland's clock changes (last Sunday of March and of October) are handled automatically. The window moves from 15:00–21:00 Perth in Irish summer to 16:00–21:00 Perth in Irish winter.

## Updating

Install a newer APK over the old one, the same way as above.

If Android reports **"App not installed"** or a package conflict, the two APKs were signed with different debug keys: each CI run, and each computer that builds the app, uses its own. Uninstall the old app first, then install the new one. Your calling-hour settings reset to the defaults, and you need to add the widget again.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Widget doesn't appear in the widget list | Open the app once, then check the list again. Some launchers only refresh the list after a restart. |
| Status or band doesn't update after a reboot | Open the app once (step 3). Also check that battery settings don't restrict the app. |
| Some battery savers still delay updates | Set **Settings → Apps → Perth ⇄ Galway → Battery** to **Unrestricted**. The clocks always stay correct. Only the status and band can lag. |
| Clocks show the wrong time | The clocks use the phone's time. Check **Settings → System → Date & time**. Your own time zone setting doesn't matter. |

## Uninstalling

Long-press the app icon and tap **Uninstall**, or run `adb uninstall com.dg.dualclock`.
