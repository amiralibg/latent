package com.latent.camera.gl

import android.content.res.AssetManager
import android.opengl.GLES30

private const val VERSION = "#version 300 es\n"

/**
 * Which kind of texture the *blit* pass is sampling — the camera's SurfaceTexture on
 * the preview path, an uploaded bitmap on the capture path.
 *
 * This distinction exists in `blit.frag` and nowhere else. `latent.frag`, which is
 * the look, always samples a plain 2D texture and so compiles identically for both
 * paths. Resist any change that would push a sampler variant into the look shader.
 */
enum class TextureSource(
    val glTarget: Int,
    private val samplerType: String,
    private val extension: String?,
) {
    ExternalOes(
        glTarget = Gl.TEXTURE_EXTERNAL_OES,
        samplerType = "samplerExternalOES",
        extension = "GL_OES_EGL_image_external_essl3",
    ),
    Texture2d(
        glTarget = GLES30.GL_TEXTURE_2D,
        samplerType = "sampler2D",
        extension = null,
    );

    internal fun preamble(): String = buildString {
        append(VERSION)
        if (extension != null) append("#extension $extension : require\n")
        append("#define LATENT_SAMPLER $samplerType\n")
    }
}

/**
 * Loads the shader assets. Shaders are files, never Kotlin string literals — the
 * version directive and the blit pass's sampler define are the only exceptions, and
 * they exist so the look itself can stay one file shared by both render paths.
 */
internal object ShaderSource {

    private const val VERTEX_ASSET = "shaders/latent.vert"
    private const val BLIT_ASSET = "shaders/blit.frag"
    private const val LOOK_ASSET = "shaders/latent.frag"
    private const val AIDS_ASSET = "shaders/aids.frag"

    fun vertex(assets: AssetManager): String = VERSION + read(assets, VERTEX_ASSET)

    fun blit(assets: AssetManager, source: TextureSource): String =
        source.preamble() + read(assets, BLIT_ASSET)

    /** No per-consumer variation. Both paths compile this exact string. */
    fun look(assets: AssetManager): String = VERSION + read(assets, LOOK_ASSET)

    /** Preview-only. Capture must never load this. */
    fun aids(assets: AssetManager): String = VERSION + read(assets, AIDS_ASSET)

    private fun read(assets: AssetManager, path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }
}
