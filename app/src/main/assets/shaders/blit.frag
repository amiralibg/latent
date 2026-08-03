// Plumbing, not look. Nothing here grades anything.
//
// The preview's frames arrive on a SurfaceTexture (samplerExternalOES) and the
// capture path's arrive as an uploaded bitmap (sampler2D). This pass is the only
// place that difference exists: it resolves the source into a plain square 2D
// texture, applying the crop and rotation on the way, so that latent.frag — the
// actual look — compiles byte-identically for both paths and needs no per-consumer
// preamble at all.
//
// Keep it that way. Any grading added here would exist in one form for preview and
// another for export, which is the failure mode CLAUDE.md invariant 2 is guarding.

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform LATENT_SAMPLER uTexture;

void main() {
    fragColor = vec4(texture(uTexture, vTexCoord).rgb, 1.0);
}
