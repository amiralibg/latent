// The look engine. There is exactly one of these, and both render paths compile it
// from identical source with identical defines — the preview and the saved file run
// the same instructions on the same kind of texture. That is what makes the output
// WYSIWYG (CLAUDE.md invariant 2). Do not add a CPU-side path for export, and do not
// let a stage below leak into blit.frag.
//
// The order of operations is fixed and is not a matter of taste:
//   1. channel mix -> luminance   5. grain
//   2. tone curve                 6. toning
//   3. clarity                    7. vignette
//   4. halation
//
// Uniform names match the Kotlin Recipe field names exactly, one to one.

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// The square, upright frame produced by blit.frag, with a full mip chain. Stages 3
// and 4 need a blurred copy of the image; sampling a coarse level is where it comes
// from, which is why this is a plain 2D texture and not the camera's OES texture.
uniform sampler2D uTexture;

// Side of the frame in pixels. Blur radii are fractions of the frame, so the mip
// level they need depends on the resolution: without this the preview and the
// full-size render would apply visibly different amounts of clarity and halation.
uniform float frameSize;

uniform vec3 channelMix;

uniform float lift;
uniform float gamma;
uniform float gain;
uniform float contrast;

uniform float clarity;
uniform float clarityRadius;

uniform float halation;
uniform float halationThreshold;
uniform float halationRadius;

uniform float grain;
uniform float grainSize;

uniform float toning;

uniform float vignette;

const float MID_PIVOT = 0.5;

float toLinear(float c) {
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
}

float toSrgb(float c) {
    return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

// ---------------------------------------------------------------- stage 1
// The channel mix is a contrast-filter emulation, and a contrast filter acts on
// light. Run on gamma-encoded values a red filter barely darkens a sky; run on
// linear light it does what the glass does. Hence the decode here, and the re-encode
// straight afterwards so every later stage works in the display-referred space that
// lift/gamma/gain and grain are actually defined in.
float mixToLuma(vec3 encoded) {
    vec3 linear = vec3(toLinear(encoded.r), toLinear(encoded.g), toLinear(encoded.b));
    // Weights are user-adjustable, so they are not guaranteed to sum to one.
    // Normalising keeps a mix change from doubling as an exposure change.
    float total = max(channelMix.r + channelMix.g + channelMix.b, 1e-4);
    return dot(linear, channelMix / total);
}

// ---------------------------------------------------------------- stage 2
float toneCurve(float l) {
    // Slope, offset, power — the same shape as an ASC CDL, so the sliders behave
    // the way they do everywhere else.
    float v = l * gain + lift;
    v = pow(max(v, 0.0), 1.0 / max(gamma, 1e-3));

    // The optional spline, as a single symmetric S around the mid pivot. The
    // steepening falls to zero at both ends, so contrast pivots the midtones
    // without dragging black and white with it. At contrast 0 this is identity.
    float s = v - MID_PIVOT;
    v = MID_PIVOT + s * (1.0 + contrast * (1.0 - abs(s) * 2.0));

    return clamp(v, 0.0, 1.0);
}

/** Everything through stage 2, at a chosen mip level. */
float gradedLumaAt(float lod) {
    return toneCurve(toSrgb(mixToLuma(textureLod(uTexture, vTexCoord, lod).rgb)));
}

/** Mip level whose texels span `radius` of the frame. */
float lodForRadius(float radius) {
    return max(log2(max(radius * frameSize, 1.0)), 0.0);
}

// ---------------------------------------------------------------- stage 5
// Value noise rather than per-pixel hash: film grain has a size, and white noise
// resamples into mush the moment the image is scaled.
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    // Stages 1 and 2.
    float l = gradedLumaAt(0.0);

    // Stage 3 — clarity, an unsharp mask against a blurred copy of the same
    // graded luminance. Negative values soften.
    if (clarity != 0.0) {
        float local = gradedLumaAt(lodForRadius(clarityRadius));
        l = clamp(l + clarity * (l - local), 0.0, 1.0);
    }

    // Stage 4 — halation, a glow bleeding out of the highlights. Screened rather
    // than added so it lifts towards white instead of clipping through it.
    if (halation > 0.0) {
        float spread = gradedLumaAt(lodForRadius(halationRadius));
        float above = max(spread - halationThreshold, 0.0) /
            max(1.0 - halationThreshold, 1e-3);
        float glow = halation * above * above;
        l = 1.0 - (1.0 - l) * (1.0 - clamp(glow, 0.0, 1.0));
    }

    // Stage 5 — grain, weighted towards the midtones. Clear film base and blocked
    // shadows carry almost no grain; the middle of the curve carries all of it.
    if (grain != 0.0) {
        float weight = 4.0 * l * (1.0 - l);
        vec2 grainCoord = vTexCoord * (frameSize / max(grainSize, 0.5));
        l = clamp(l + grain * weight * (valueNoise(grainCoord) - 0.5), 0.0, 1.0);
    }

    // Stage 6 — toning. Cool selenium through neutral to warm sepia.
    const vec3 SELENIUM = vec3(0.88, 0.96, 1.14);
    const vec3 SEPIA = vec3(1.14, 1.00, 0.82);
    vec3 tint = mix(vec3(1.0), toning < 0.0 ? SELENIUM : SEPIA, abs(toning));
    // Weighted towards the shadows, which is where a real toner takes hold; paper
    // white stays white.
    vec3 rgb = vec3(l) * mix(tint, vec3(1.0), l * l);

    // Stage 7 — vignette.
    if (vignette != 0.0) {
        float r = length(vTexCoord - 0.5) * 1.41421356;
        rgb *= 1.0 - vignette * smoothstep(0.35, 1.0, r);
    }

    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
