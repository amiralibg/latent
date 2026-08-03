// Preview-only overlays. Never compiled into the capture path.
//
// Samples the already-graded frame and draws focus peaking and/or zebra stripes.
// The look itself lives in latent.frag and is untouched here — that is what keeps
// saved B&W free of shooting aids (CLAUDE.md invariant 2).

precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 texelSize;
uniform float peaking;
uniform float peakingThreshold;
uniform float zebra;
uniform float zebraThreshold;
uniform float zebraPhase;

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    if (peaking > 0.5) {
        // Sobel on luminance — edges light up when glass is in focus.
        float tl = dot(texture(uTexture, vTexCoord + vec2(-texelSize.x,  texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float  t = dot(texture(uTexture, vTexCoord + vec2( 0.0,          texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float tr = dot(texture(uTexture, vTexCoord + vec2( texelSize.x,  texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float  l = dot(texture(uTexture, vTexCoord + vec2(-texelSize.x,  0.0)).rgb,         vec3(0.2126, 0.7152, 0.0722));
        float  r = dot(texture(uTexture, vTexCoord + vec2( texelSize.x,  0.0)).rgb,         vec3(0.2126, 0.7152, 0.0722));
        float bl = dot(texture(uTexture, vTexCoord + vec2(-texelSize.x, -texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float  b = dot(texture(uTexture, vTexCoord + vec2( 0.0,         -texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float br = dot(texture(uTexture, vTexCoord + vec2( texelSize.x, -texelSize.y)).rgb, vec3(0.2126, 0.7152, 0.0722));

        float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
        float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
        float edge = sqrt(gx * gx + gy * gy);

        if (edge > peakingThreshold) {
            // Red peaking on a B&W frame — traditional, readable, never saved.
            color = mix(color, vec3(1.0, 0.15, 0.12), 0.85);
        }
    }

    if (zebra > 0.5 && luma >= zebraThreshold) {
        float stripe = step(0.5, fract((vTexCoord.x + vTexCoord.y) * 36.0 + zebraPhase));
        color = mix(color, vec3(1.0), stripe * 0.65);
    }

    fragColor = vec4(color, 1.0);
}
