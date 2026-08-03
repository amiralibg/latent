# Build plan

One branch per phase. Do not start a phase until the previous gate has been checked by
hand on a real device. Phases 0–2 are the whole product; everything after is leverage.

---

## Phase 0 — Skeleton

- Single-activity Compose scaffold, dark UI, camera + storage permissions with rationale
- CameraX `PreviewView` inside a **locked square viewport**, masked so nothing outside
  1:1 is ever visible
- Shutter button, unprocessed JPEG capture to MediaStore
- Cold-start straight into the viewfinder — no splash, no menu

**Gate:** hold the phone and shoot twenty frames. Does the square feel right in the hand?
Is the shutter where your thumb already is? Fix ergonomics now, not in Phase 6.

---

## Phase 1 — Pipeline and provenance

- `CameraEffect` + `SurfaceProcessor` on `PREVIEW`, one hardcoded desaturation shader
- Offscreen EGL pbuffer path running the **same** `.frag` on the full-res captured frame
- Centre-crop to square, encode, write
- Dual save: B&W output + untouched colour original
- Room schema linking output ↔ source; EXIF preserved on both
- **Golden-image test** comparing the two render paths

**Gate:** screenshot the preview, open the saved file, compare side by side. Identical?
If not, stop. Every later phase compounds this error.

---

## Phase 2 — Look engine

- Full uniform chain per `CLAUDE.md`: channel mix, curve, clarity, halation, grain,
  toning, vignette
- `Recipe` model + Room persistence, duplicate/rename/reorder
- Swipeable recipe strip above the shutter, live preview on swipe
- Slider sheet for editing the active recipe, with a reset

**Gate:** rebuild all three of your reference photos as named recipes. If you can, the
app is already better than your phone's camera for your purposes and you could stop
here and shoot with it for a week before continuing. Do that — a week of real use will
reorder every remaining phase.

---

## Phase 3 — Camera controls

- Physical lens picker. CameraX's default selector only exposes front/back; enumerate
  real lenses with a custom `CameraFilter` reading
  `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` via Camera2Interop. Expect device-specific
  weirdness and budget real time here.
- Pinch zoom → `setLinearZoom`, plus tap-to-set ratio at native focal lengths
- Tap to focus, long-press to lock, decoupled AE/AF
- EV compensation dial
- **Full manual mode**: ISO (`SENSOR_SENSITIVITY`), shutter (`SENSOR_EXPOSURE_TIME`),
  manual focus distance (`CONTROL_AF_MODE_OFF` + `LENS_FOCUS_DISTANCE`, range from
  `LENS_INFO_MINIMUM_FOCUS_DISTANCE`). Auto ↔ manual toggle per-parameter, not global.
  Warn when the selected shutter speed is below the handheld threshold for the
  current focal length.
- Timer, silent shutter

---

## Phase 4 — Shooting aids

- **Focus peaking** — now essential, since manual focus is a first-class mode
- Electronic level with horizon line; the reference architecture shots live or die on this
- Live luminance histogram
- Zebra clipping warning on blown highlights
- Grids: thirds, centre cross, diagonals, square golden section
- All aids individually toggleable, state persisted

---

## Phase 5 — Library and re-grade

- Settings for save behaviour: B&W only / B&W + colour original (default) / add DNG.
  RAW needs CameraX `OUTPUT_FORMAT_RAW_JPEG` and a capable device — feature-detect and
  hide the option where unsupported.
- Contact-sheet gallery, square grid, app captures only
- **Simple re-grade**: open a shot, apply a different recipe or nudge the sliders,
  re-render from the stored original, save as a new file. No masking, no new tools.
- Export and share, optional white border for Instagram

---

## Phase 6 — Polish

- Haptics on shutter and recipe change
- Shutter animation and blackout timing
- Cold-start budget: viewfinder live in under a second
- Quick-settings tile and launcher shortcut
- Icon, store listing

---

## Deferred to v1.1

Re-crop within the original frame (still 1:1), burst mode, recipe import/export as
shareable files, intervalometer.
