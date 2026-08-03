// Full-screen quad. The only interesting part is texTransform: the preview path
// feeds it the SurfaceTexture matrix (already folded together with CameraX's crop
// and rotation), the capture path feeds it a centre-crop-to-square matrix. Neither
// path moves geometry, so the two share this vertex stage unchanged.
//
// No #version here — ShaderSource prepends it, because the fragment stage needs a
// per-consumer preamble and the two files have to stay symmetrical.

in vec4 aPosition;
in vec4 aTexCoord;

uniform mat4 texTransform;

out vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = (texTransform * aTexCoord).xy;
}
