# Mixxx Mobile (Android MVP)

Clean-room Android homage to the core DJ workflow of
[mixxx-desktop](../mixxx-desktop/) (GPLv2): 2 decks, mixer with
equal-power crossfader, tempo/sync, loops + hotcues, waveform, one
filter/echo slot per deck, and a Room-backed track library.

This is **not** a port of the Mixxx C++/Qt engine — it re-implements the
user-visible behavior described in the
[Mixxx 2.5 interface manual](https://manual.mixxx.org/2.5/en/chapters/user_interface)
in Kotlin.

## Stack

- Kotlin + Jetpack Compose (Material3, dark DJ theme)
- Media3/ExoPlayer for MVP decode + tempo (`Media3PlaybackEngine`)
- Room for tracks/cues; `PlaybackEngine` interface keeps a seam for a
  future Oboe/AAudio native mixer (see the
  [low-latency checklist](https://developer.android.com/games/sdk/oboe/low-latency-audio))

## Build

Prerequisites: JDK 17, Android SDK with `platforms;android-34` and
`build-tools;35.0.0`, `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) set.

```sh
cd mixxx-android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew` bootstraps Gradle 8.7 on first run (no wrapper jar checked in).

## What works in MVP

Play/cue/pause per deck, seek, tempo slider (0.92–1.08x), sync-to-other-deck,
loop in/out/clear, 3 hotcue set/trigger slots per deck, per-channel gain +
3-band EQ params, equal-power crossfader, master volume, per-deck FX wet knob
(DSP deferred), demo library with 2 tracks.

## Out of scope (Phase 2+)

4 decks, samplers, mic/aux, vinyl DVS, MIDI/HID controllers, live
broadcasting, full Mixxx effect racks, real peak analysis/BPM detect,
MediaStore import UI. See `DjViewModel` + `PlaybackEngine` seams.

## License

GPL-2.0-only, matching Mixxx. Mixxx itself remains in `../mixxx-desktop/`
under its own GPLv2 license.
