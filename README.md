# Cosmo-Watch

Cosmo-Watch is a Fire TV browser. It embeds Mozilla GeckoView and bundles the official Firefox build of uBlock Origin, so filtering stays inside the extension instead of being reimplemented.

Fire OS 7 (Android 9) and Fire OS 8 (Android 11) sticks, Cube, and TVs are the target. Vega OS devices are not Android and are out of scope. This tree is for sideloading, not the Amazon Appstore.

## Pins

| Piece | Value |
|---|---|
| GeckoView | `org.mozilla.geckoview:geckoview:157.0.20260924084938` |
| GeckoView minSdk | 26 (from the AAR manifest) |
| App minSdk / targetSdk | 28 / 36 |
| compileSdk | 37.1, which is the AAR `minCompileSdk` |
| uBlock Origin | 1.75.0 Firefox MV2, id `uBlock0@raymondhill.net` |
| XPI | `uBlock0_1.75.0.firefox.signed.xpi` |
| SHA-256 | `5b74415860456370644bd80f16125e865b0e6c356bb5dfcfb84069967eaa5287` |

The hash, download URL, and version are in `third_party/ublock-origin.pin`. The XPI is unpacked at `app/src/main/assets/extensions/ublock/` with `manifest.json` at the root of that folder. `manifest_version` is 2. This is uBlock Origin, not uBlock Origin Lite.

GeckoView 157's `geckoview` artifact is one AAR with `arm64-v8a`, `armeabi-v7a`, and `x86_64`. ABI splits emit one APK per ABI. Fire TV needs the 32-bit or 64-bit ARM APK. The `x86_64` APK is for the Android emulator.

## Build

Install Android SDK platform 37.1 and build-tools 36 (AGP 9.4.1's default). JDK 17 or newer. GeckoView 157 pulls `kotlin-stdlib` 2.4.20, so the root build file pins the Kotlin Gradle plugin to 2.4.20. AGP's built-in Kotlin otherwise stays on 2.2.10 and cannot read that metadata. Then:

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug APKs land in `app/build/outputs/apk/debug/`. Sideload the ABI that matches the device:

```bash
adb install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -n watch.cosmo/.MainActivity
```

Many Fire TV sticks still run a 32-bit userland. If the arm64 APK will not install, use `armeabi-v7a`.

Debug builds turn on `about:config`, remote debugging, and Gecko console output so desktop Firefox `about:debugging` can attach over adb. Release builds leave those off. The extension is not updated from the network; a newer uBO ships with the next app build.

## Remote

The D-pad moves a cursor drawn over the page. Holding a direction speeds it up. At the edge of the screen the page scrolls through `PanZoomController.scrollBy`. Select clicks by sending a touch down/up to `GeckoView`. A long press on Select, or the Menu key, opens the control menu. Search on the remote opens the address bar. Back closes the address bar, then the menu, then fullscreen video, then navigates back, then leaves the app.

While a Gecko media session is active, the remote’s play, pause, play/pause, stop, rewind, fast-forward, next, and previous keys call GeckoView `MediaSession` (`play`, `pause`, `stop`, `seekTo` / `seekForward` / `seekBackward`, `nextTrack`, `previousTrack`). Those commands are also published on an Android `MediaSession` so Fire TV can deliver them as transport controls. In fullscreen, Left and Right seek 10 seconds, Up and Down change the media volume, and Select toggles play. A long press on Select still opens the menu.

The address bar takes a URL or a search. The default search template is DuckDuckGo (`https://duckduckgo.com/?q=%s`), stored in `SharedPreferences` and editable from the menu. The home page starts at `https://cinejoy.pk/watch/tv/220542/1/1` and can be replaced with "Set this page as home". Desktop mode is the default, with a menu toggle for the mobile user agent and viewport.

## uBlock Origin

On startup the app calls `ensureBuiltIn("resource://android/assets/extensions/ublock/", "uBlock0@raymondhill.net")` and waits for that `GeckoResult` before the first `loadUri`. If install fails, a warning stays on screen and browsing continues.

`ensureBuiltIn` does not reinstall when the installed version already matches the bundle. If the versions differ, the app calls `installBuiltIn` so the same extension id is updated in the Gecko profile. That is the path expected to keep uBO storage. If the version still does not match, the fallback uninstalls and installs again, and that fallback can drop list toggles, custom filters, and other extension settings. Neither path has been confirmed on a Fire TV in this repo.

The dashboard menu item loads `dashboard.html` from `extension.metaData.baseUrl`. The browser-action popup (`popup-fenix.html`) is not wired up. `WebExtension.ActionDelegate` is optional and unused. `setAllowedInPrivateBrowsing(WebExtension, boolean)` exists on GeckoView 157 and is unused because there is no private mode.

uBO ships its own default lists and updates those lists on its own schedule. The app does not seed extra lists and does not replace filter matching.

"Clear site data" uses `StorageController.ClearFlags.SITE_DATA` (cookies, DOM storage, cache, permissions). It does not uninstall the extension. Do not wipe the Gecko profile on an app upgrade; extension storage lives there.

## Known limitations

These were not run on a Fire OS device or emulator in this workspace, so they stay open:

- Whether uBO HTML filtering (`webRequest.filterResponseData`, including `##^script:has-text(...)`) works under GeckoView on Android. Network filtering, cosmetic filtering, and scriptlets are part of this unmodified extension; HTML filtering depends on the engine.
- Whether the uBO dashboard is comfortable with only the virtual cursor.
- Whether `installBuiltIn` keeps uBO settings across a bundled version bump. The uninstall fallback can wipe them.
- Whether Widevine playback works in GeckoView on Fire OS.
- Cold start, long-session memory, and cursor smoothness on a stick. `Application.onTrimMemory` is logged as `CosmoWatch`.

Gecko's own tracking protection is left at its default so it does not fight uBO. The extension process is also left at the GeckoView default.

## Licenses

GeckoView is MPL 2.0. uBlock Origin is GPLv3, and its license file is inside the bundled extension. Shipping that extension inside the APK means a public release of Cosmo-Watch should be treated as GPL-compatible and should ship corresponding source. Filter lists have their own licenses (EasyList is dual GPL/CC BY-SA). Get advice before a public release. Amazon Appstore rules for ad-blocking browsers are a separate review; sideloading for personal use is the path this project is set up for.
