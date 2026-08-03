# CLAUDE.md — Latent (working title)

A 1:1 black-and-white camera app for Android. The app exists to make one specific
photographic style fast to shoot: square, monochrome, texture-forward, with real
tonal control. It is not a general-purpose camera.

---

## Invariants — never violate these

1. **The frame is 1:1. Always.** There is no aspect-ratio setting, no hidden toggle,
   no "advanced" escape hatch. The preview viewport is a true square. If you find
   yourself adding a ratio parameter anywhere in the UI layer, stop.
2. **Preview and capture share one shader source.** Never write a second CPU-side
   processing path "just for export". One `.frag` file, two consumers.
3. **The original is never destroyed.** Whatever the user's save settings, the
   processing chain reads from an untouched full-resolution source frame. All grading
   is re-derivable.
4. **No account, no cloud, no analytics SDK, no ads.** Local-only app.
5. **Never block the shutter.** Processing, saving and DB writes happen off the
   capture path. Shutter → next frame available must feel instant.

---

## Stack

- Kotlin, Jetpack Compose, single-activity
- **CameraX** (check for the latest stable release before pinning; 1.4+ required for
  RAW output support) with **Camera2Interop** for manual controls
- **OpenGL ES 3.0** fragment shaders for all image processing
- Room for recipes + capture pairing, DataStore for settings
- MediaStore for output, one album directory
- minSdk 26, target current. Gate manual controls behind
  `INFO_SUPPORTED_HARDWARE_LEVEL` — degrade gracefully on LIMITED devices.

---

## Capture architecture — read this before touching the pipeline

The user's save default is **processed B&W + untouched original colour**. That
constraint decides the architecture:

- Attach the `CameraEffect` to **`PREVIEW` only**.
- `ImageCapture` produces an **unprocessed** full-res frame.
- After capture, run that frame through the **same fragment shader** offscreen via an
  EGL pbuffer, then centre-crop to square and encode.

Do not attach the effect to `IMAGE_CAPTURE`. CameraX permits only one bound
`ImageCapture` use case, so an effect on that target would make the untouched original
unobtainable. The offscreen path is required regardless — build it once, properly.

Every capture writes a Room row linking the B&W output URI to its source URI. Re-grade
depends on this pairing existing from day one; retrofitting provenance later is painful.

---

## The look engine

All looks are a uniform set fed to one shader. Order of operations is fixed:

1. **Channel mix → luminance** (this is the contrast-filter emulation, and it must
   happen on linear-ish colour data *before* desaturation, or the effect is lost)
   - Neutral `(0.2126, 0.7152, 0.0722)`
   - Yellow `(0.50, 0.40, 0.10)`
   - Orange `(0.65, 0.30, 0.05)`
   - Red `(0.80, 0.15, 0.05)` — darkens sky, separates cloud
   - Green `(0.20, 0.70, 0.10)` — foliage and skin
   - Weights are user-adjustable; presets are just named triples.
2. Tone curve (lift / gamma / gain + optional spline)
3. Clarity (local contrast, unsharp mask on a blurred luminance copy)
4. Halation on highlights above threshold
5. Grain — luminance-weighted, strongest in midtones, with size and intensity
6. Toning (cool selenium ↔ neutral ↔ warm sepia)
7. Vignette

A **Recipe** is exactly this uniform set plus a name. Recipes are the primary UI
object — the user swipes between them above the shutter. Recipe name goes into EXIF.

---

## Code conventions

- Shaders live in `app/src/main/assets/shaders/` as separate `.frag` files. Never
  inline GLSL as Kotlin string literals.
- Uniform names in GLSL match the Kotlin `Recipe` field names exactly, one to one.
- Camera state lives in one `CameraController` class; Compose observes, never commands
  the hardware directly.
- No `!!`. No `GlobalScope`.

---

## Required test

From Phase 1 onward, a golden-image test renders a fixed test bitmap through both the
preview path and the offscreen capture path and asserts mean pixel difference below
threshold. It runs on every phase merge. This test is the only thing standing between
you and a WYSIWYG regression discovered three phases late.

---

## Definition of done, per phase

Compiles, runs on a physical device, the golden test passes, and the phase's stated
gate has been checked by hand — not asserted in a summary.
