package uk.co.snakebot.autoplayer.core

import kotlin.math.max

/**
 * Detects the bright lime snake head directly.
 *
 * The supplied Galaxy screenshot shows a very stable signature:
 * - head green is substantially brighter than body/grid/border
 * - green is the dominant RGB channel
 * - yellow food fails the green-dominance test
 *
 * We sample the board on a coarse lattice for speed, then return the centroid
 * of all head-colour samples mapped back to a logical board cell.
 */
class HeadDetector {
    fun detect(source: PixelSource, bounds: PixelRect, spec: BoardSpec): Cell? {
        if (bounds.width <= 0 || bounds.height <= 0) return null
        if (bounds.left < 0 || bounds.top < 0 ||
            bounds.right > source.width || bounds.bottom > source.height
        ) return null

        val step = max(3, minOf(bounds.width, bounds.height) / 180)
        var sumX = 0L
        var sumY = 0L
        var count = 0

        var y = bounds.top + step / 2
        while (y < bounds.bottom) {
            var x = bounds.left + step / 2
            while (x < bounds.right) {
                val c = source.argb(x, y)
                if (isHeadColour(c)) {
                    sumX += x
                    sumY += y
                    count++
                }
                x += step
            }
            y += step
        }

        // At native Galaxy resolution the head produces dozens of samples.
        // Keep the floor low enough to survive scaling/compression.
        if (count < 6) return null

        val cx = sumX.toDouble() / count
        val cy = sumY.toDouble() / count

        val cellW = bounds.width.toDouble() / spec.columns
        val cellH = bounds.height.toDouble() / spec.rows
        val col = ((cx - bounds.left) / cellW).toInt().coerceIn(0, spec.columns - 1)
        val row = ((cy - bounds.top) / cellH).toInt().coerceIn(0, spec.rows - 1)
        return Cell(col, row)
    }

    private fun isHeadColour(argb: Int): Boolean {
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff

        // Works whether the capture path preserves R/B order or swaps them:
        // green must beat both other channels.
        return g >= 170 &&
            g - r >= 14 &&
            g - b >= 14
    }
}
