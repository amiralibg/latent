# Latent

<p align="center">
  <img src="assets/icon.svg" width="128" alt="Latent icon — a square dot-matrix frame with a lens at its centre">
</p>

A 1:1 black-and-white camera app for Android. One photographic style, made fast:
square frames, monochrome, texture-forward, with real tonal control.

Latent is not a general-purpose camera. There is no aspect-ratio setting, no filters
browser, no account, no cloud. The whole app is built to make one kind of picture
effortless — you pick a look, frame a square, shoot, and the picture you saw is the
picture you get.

## Why it exists

The black-and-white image has a specific look that phone cameras never deliver out of
the box: contrast filtering (yellow, orange, red filters), tonal curves, local
clarity, grain, halation, toning, vignette. Getting that look usually means shooting
raw colour and doing hours of post. Latent puts the whole chain in the viewfinder, in
real time, so the look is chosen *before* the shutter, not rescued after.

## Features

- **A true 1:1 viewfinder.** The frame is square. No ratio toggles, no escape hatch.
- **Live look engine.** Recipes — named sets of the full grade — are swiped above the
  shutter and rendered on the live preview via OpenGL ES 3.0 fragment shaders:
  - Channel-mix contrast filters (neutral / yellow / orange / red / green)
  - Tone curve (lift / gamma / gain)
  - Clarity (local contrast)
  - Halation on highlights
  - Luminance-weighted grain
  - Selenium ↔ neutral ↔ sepia toning
  - Vignette
- **What you see is what you get.** Preview and capture share one shader source; the
  saved image is rendered through the exact same pipeline you framed with, enforced by
  a golden-image test.
- **The original is never destroyed.** Every shot keeps its untouched colour source,
  paired in Room. Re-grade any capture from the original under a different recipe,
  anytime.
- **Full manual control** on capable hardware (degrades gracefully elsewhere): ISO,
  shutter speed, manual focus, white-balance presets with AWB lock, EV, physical
  lens picker, pinch zoom, tap-to-focus with long-press lock.
- **Shooting aids, preview-only** — peaking, zebra, live histogram, level, and grid
  overlays that never touch the saved file.
- **Save modes:** processed B&W only, B&W + untouched original (default), or add a DNG.
- **Contact-sheet gallery** with simple re-grade, share, and an optional white export
  border.
- **Local-only.** No account, no analytics, no ads, no network. Every frame stays on
  your device.
- Timer, silent shutter, quick-settings tile and contact-sheet launcher shortcut.

## Design invariants

These are load-bearing and not up for debate:

1. **The frame is 1:1. Always.**
2. **Preview and capture share one shader source.** One `.frag` file, two consumers —
   never a separate CPU path "just for export".
3. **The original is never destroyed.** All grading is re-derivable from an untouched
   full-resolution source frame.
4. **No account, no cloud, no analytics, no ads.**
5. **Never block the shutter.** Processing, saving and DB writes happen off the
   capture path.

See `CLAUDE.md` for the full capture architecture and look-engine specification.

## Tech stack

- Kotlin, Jetpack Compose, single activity
- CameraX 1.6 with `Camera2Interop` for manual controls, RAW (DNG) output, and
  `CameraEffect` / `SurfaceProcessor` for the preview pipeline
- OpenGL ES 3.0 fragment shaders for all image processing
- Room for recipe + capture pairing, DataStore for settings
- MediaStore output to a single album directory
- minSdk 26

## Building

Requires JDK 17 and an Android SDK with platform `android-37`.

```bash
# Debug
./gradlew :app:assembleDebug

# Release (signs with a keystore from env, or falls back to the debug key)
RELEASE_KEYSTORE=/path/to/keystore \
RELEASE_KEYSTORE_PASSWORD=<store-pass> \
RELEASE_KEY_ALIAS=<alias> \
RELEASE_KEY_PASSWORD=<key-pass> \
./gradlew :app:assembleRelease
```

The release APK lands in `app/build/outputs/apk/release/`.

> The environment-variable signing config means the same codebase builds in CI with
> keystore secrets and locally without one. Note that a debug-signed APK cannot
> update an app installed with a release key — generate a real keystore before
> shipping, and back it up: losing it means you can never update installed copies.

## Testing

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests
```

Instrumented tests run on a device/emulator and include the **golden-image test**, which
renders a fixed frame through both the preview path and the offscreen capture path and
asserts the mean pixel difference stays below threshold. It is the only thing standing
between you and a WYSIWYG regression discovered three releases late.

## Releases

Pushing a version tag builds a signed release APK and creates a GitHub Release
automatically:

```bash
git tag 0.0.1 && git push origin 0.0.1
```

Tags may be plain (`0.0.1`) or `v`-prefixed (`v0.0.1`). The workflow
(`.github/workflows/release.yml`) signs with keystore secrets set in the repo and
attaches the APK to the release. You can also run it manually from the Actions tab.

## License

No license declared yet. See the author before reuse.
