package com.latent.camera.gl

import android.graphics.Bitmap
import android.graphics.Color

/**
 * The fixed frame the golden test renders through both paths.
 *
 * Deliberately asymmetric on both axes and in both diagonals. A mean-difference
 * check over a symmetric pattern would pass while one path rendered the frame
 * flipped or rotated, and a flip between preview and capture is exactly the class
 * of bug this test exists to catch.
 */
internal object TestFrame {

    const val SIZE = 512

    private val PRIMARIES = intArrayOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
    )

    fun create(size: Int = SIZE): Bitmap {
        val pixels = IntArray(size * size)
        val bandTop = size * 3 / 8
        val bandBottom = size * 5 / 8
        val corner = size / 8

        for (y in 0 until size) {
            for (x in 0 until size) {
                val colour = when {
                    // Orientation markers: white top-left, black bottom-right.
                    x < corner && y < corner -> Color.WHITE
                    x >= size - corner && y >= size - corner -> Color.BLACK

                    // Saturated primaries, so a wrong channel mix cannot average out.
                    y in bandTop until bandBottom ->
                        PRIMARIES[(x * PRIMARIES.size / size).coerceIn(0, PRIMARIES.lastIndex)]

                    // Smooth elsewhere. Hard edges are kept to the markers and the
                    // primaries band: the two paths reach the sampler by different
                    // routes, and half-texel differences only show up on hard edges.
                    else -> {
                        val r = x * 255 / (size - 1)
                        val g = y * 255 / (size - 1)
                        val b = (x + 2 * y) * 255 / (3 * (size - 1))
                        Color.rgb(r, g, b)
                    }
                }
                pixels[y * size + x] = colour
            }
        }

        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

/** Mean absolute per-channel difference, in 0-255 levels. */
internal fun meanChannelDifference(a: Bitmap, b: Bitmap): Double {
    require(a.width == b.width && a.height == b.height) {
        "Size mismatch: ${a.width}x${a.height} vs ${b.width}x${b.height}"
    }
    val width = a.width
    val height = a.height
    val rowA = IntArray(width)
    val rowB = IntArray(width)
    var total = 0L

    for (y in 0 until height) {
        a.getPixels(rowA, 0, width, 0, y, width, 1)
        b.getPixels(rowB, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            val pa = rowA[x]
            val pb = rowB[x]
            total += Math.abs(Color.red(pa) - Color.red(pb)).toLong()
            total += Math.abs(Color.green(pa) - Color.green(pb)).toLong()
            total += Math.abs(Color.blue(pa) - Color.blue(pb)).toLong()
        }
    }
    return total.toDouble() / (width.toLong() * height * 3)
}
