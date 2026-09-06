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

// Preview-only load control, not part of the look. 1.0 is the full three-octave
// field the file is rendered with; 0.0 is the central octave alone, engaged by the
// preview governor under sustained thermal pressure. Same source, same uniforms on
// both paths — the capture path always renders 1.0, so the golden test compares
// full against full and this can never be a quiet WYSIWYG break.
uniform float grainDetail;

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
// Film grain is particles, not noise.
//
// That distinction is the whole stage. Smooth interpolated noise produces gradients
// — soft blotches that the eye files under dirty sensor or bad compression. A film
// emulsion produces discrete silver crystals that clumped as they formed: hard little
// edges at one scale, clouds of them at a coarser one, and clear gelatin in between.
// Each piece below is one of those properties, and dropping any of them takes the
// stage back to looking digital.

// Integer-style hash rather than the usual fract(sin(dot(p, k)) * big). The sine
// version loses precision once its argument gets large, and the argument here is a
// cell index that runs into the hundreds across the frame — the cost is faint
// diagonal structure, which the eye reads as a pattern laid over the picture rather
// than as grain in it.
float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
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

// Each octave is turned against the last. Value noise lives on an axis-aligned
// lattice and stacking octaves on the same lattice keeps every row and column lined
// up, which is exactly the regularity that gives noise away. Roughly 37 degrees, so
// no octave shares an axis with any other.
const mat2 GRAIN_TURN = mat2(0.8018, -0.5976, 0.5976, 0.8018);

// Three scales, because a grain is not the only thing you can see in a grainy frame.
// The middle octave is the grain itself, at the size the recipe asked for. The coarse
// one is the clumping — patches where the emulsion happened to be denser — and it is
// the single thing that most separates film from sensor noise. The fine one is the
// crystal edge, which keeps the texture from turning soft when the same recipe is
// rendered at export size.
float grainField(vec2 p) {
    float mid = valueNoise(p);
    if (grainDetail >= 0.5) {
        float n = valueNoise(GRAIN_TURN * p * 0.47 + 11.3) * 0.28;
        n += mid * 0.50;
        n += valueNoise(GRAIN_TURN * p * 2.13 + 5.7) * 0.22;
        return n;
    }
    // The reduced field: the central octave on its own. One octave has ~1.6x the
    // spread of the three-octave stack, so the deviation is scaled back by the
    // inverse to keep `grain` meaning the same depth of modulation — engaging this
    // reads as slightly simpler grain, not as the grain vanishing or doubling.
    return 0.5 + (mid - 0.5) * 0.61;
}

// How hard the particles are. Higher is more bimodal — more grain, less haze.
const float GRAIN_HARDNESS = 2.3;

// Hardening moves energy out of the middle of the distribution and into the edges,
// which raises the spread of the field by about half again. Scaling that back means
// `grain` still means the depth of modulation it meant before — a recipe someone
// saved last week grades to the same weight of grain today, in a different shape.
const float GRAIN_NORMALISE = 0.69;

// The curve that turns the field above from cloud into particles.
//
// Stacking octaves pulls the distribution towards its middle (three averaged randoms
// are far more Gaussian than one), and the middle is precisely the soft grey mush
// that does not look like film. Steepening the centre pushes those values apart
// again, so most of the frame settles at clear or exposed and the transition between
// them collapses into an edge. Without this the stage is a blur; with it, it is grain.
float harden(float n) {
    n = clamp(n, 0.0, 1.0);
    return n < 0.5
        ? 0.5 * pow(2.0 * n, GRAIN_HARDNESS)
        : 1.0 - 0.5 * pow(2.0 - 2.0 * n, GRAIN_HARDNESS);
}

// Grain is a property of the picture, not of the pixel grid it happens to be rendered
// on. `grainSize` is therefore read as pixels *at this reference frame size* and
// converted to a cell count across the frame, so the preview at ~1080 and a 3000px
// export lay down the same number of grains and the viewfinder is telling the truth
// about the file. Tying it to the real pixel count instead — which is what a naive
// `frameSize / grainSize` does — makes grain that is plainly visible in the export
// invisible in the preview, and vice versa.
const float GRAIN_REFERENCE = 1024.0;

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

    // Stage 5 — grain, loudest through the midtones.
    //
    // Loudest, but never nothing. A weight that falls to zero at both ends leaves
    // clean white skies and glassy blacks sitting in the middle of a textured frame,
    // and that combination is the tell of an effect applied to a digital picture:
    // stock that recorded anything recorded some texture with it. The floor is what
    // carries the grain up into the highlights, where a projected print shows it.
    if (grain > 0.0) {
        float weight = mix(0.3, 1.0, 4.0 * l * (1.0 - l));
        float cells = GRAIN_REFERENCE / max(grainSize, 0.5);
        float particle = (harden(grainField(vTexCoord * cells)) - 0.5) * GRAIN_NORMALISE;
        l = clamp(l + grain * weight * particle, 0.0, 1.0);
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
