package com.latent.camera.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30

/** Thrown for any GL or EGL failure. Nothing here is recoverable in place. */
class GlException(message: String) : RuntimeException(message)

internal object Gl {

    /** `GL_TEXTURE_EXTERNAL_OES` — the target a SurfaceTexture is bound to. */
    const val TEXTURE_EXTERNAL_OES = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    fun checkError(op: String) {
        var error = GLES30.glGetError()
        if (error == GLES30.GL_NO_ERROR) return
        val all = StringBuilder()
        while (error != GLES30.GL_NO_ERROR) {
            all.append(" 0x").append(Integer.toHexString(error))
            error = GLES30.glGetError()
        }
        throw GlException("$op failed:$all")
    }

    fun createTexture(target: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        checkError("glGenTextures")
        if (ids[0] == 0) throw GlException("glGenTextures returned 0")

        GLES30.glBindTexture(target, ids[0])
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // Clamping matters on the capture path: the centre-crop matrix can land a
        // sample fractionally outside [0,1] at the edge, and wrapping there would
        // fold the opposite side of the frame into the border pixels.
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        checkError("glTexParameteri")
        GLES30.glBindTexture(target, 0)
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        if (id == 0) return
        GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) throw GlException("glCreateShader returned 0")
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw GlException("Shader compile failed: $log\n${source.numbered()}")
        }
        return shader
    }

    /** Compile and link, leaking neither shader if the other one fails. */
    fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = try {
            compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        } catch (e: GlException) {
            GLES30.glDeleteShader(vertexShader)
            throw e
        }
        return try {
            linkProgram(vertexShader, fragmentShader)
        } finally {
            // Attached shaders survive until the program itself is deleted.
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
        }
    }

    fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        if (program == 0) throw GlException("glCreateProgram returned 0")
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw GlException("Program link failed: $log")
        }
        return program
    }

    fun attribLocation(program: Int, name: String): Int {
        val location = GLES30.glGetAttribLocation(program, name)
        if (location < 0) throw GlException("Attribute '$name' not found")
        return location
    }

    fun uniformLocation(program: Int, name: String): Int {
        val location = GLES30.glGetUniformLocation(program, name)
        if (location < 0) throw GlException("Uniform '$name' not found or optimised out")
        return location
    }

    /** Largest texture the driver will accept, cached per context by the caller. */
    fun maxTextureSize(): Int {
        val value = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, value, 0)
        return if (value[0] > 0) value[0] else 2048
    }

    /** Shader logs report line numbers; without these the preamble offset misleads. */
    private fun String.numbered(): String =
        lineSequence().mapIndexed { i, line -> "${i + 1}: $line" }.joinToString("\n")
}
