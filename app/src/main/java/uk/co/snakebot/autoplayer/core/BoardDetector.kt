package uk.co.snakebot.autoplayer.core

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Snake Classic board detector tuned from the supplied Samsung screenshot.
 *
 * Occupancy alone is not enough because the score display, power-up glow and
 * the helper overlay can all occupy grid-looking areas. Snake cells in this
 * game are consistently lime/green, so snake candidates must be both solid
 * and green. Food/power-ups remain colour-independent targets.
 */
class BoardDetector {
    fun detect(source: PixelSource, bounds: PixelRect, spec: BoardSpec): BoardSnapshot? {
        if (bounds.width <= 0 || bounds.height <= 0) return null
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > source.width || bounds.bottom > source.height) return null

        val scores = HashMap<Cell, Double>(spec.cellCount)
        for (y in 0 until spec.rows) {
            for (x in 0 until spec.columns) {
                scores[Cell(x, y)] = occupancyScore(source, bounds, spec, x, y)
            }
        }

        val snakeCandidates = scores.filter { (cell, score) ->
            score >= 0.42 && isSnakeGreen(source, bounds, spec, cell.x, cell.y)
        }.keys

        val components = connectedComponents(snakeCandidates, spec)
        val snake = components.maxByOrNull { it.size } ?: emptySet()
        if (snake.size < 3) return null

        val targets = scores
            .filter { (cell, score) -> cell !in snake && score >= 0.20 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { cell -> neighbors(cell, spec).none { it in snake } }
            .take(8)

        val snakeScore = snake.mapNotNull { scores[it] }.average()
        val confidence = (snakeScore * min(1.0, snake.size / 3.0)).coerceIn(0.0, 1.0)
        return BoardSnapshot(spec, snake, targets, confidence)
    }

    private fun isSnakeGreen(
        source: PixelSource,
        bounds: PixelRect,
        spec: BoardSpec,
        cellX: Int,
        cellY: Int
    ): Boolean {
        val x0 = bounds.left + bounds.width * cellX.toDouble() / spec.columns
        val x1 = bounds.left + bounds.width * (cellX + 1).toDouble() / spec.columns
        val y0 = bounds.top + bounds.height * cellY.toDouble() / spec.rows
        val y1 = bounds.top + bounds.height * (cellY + 1).toDouble() / spec.rows
        val cw = x1 - x0
        val ch = y1 - y0

        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var count = 0
        val steps = 5

        for (iy in 0 until steps) {
            for (ix in 0 until steps) {
                val fx = .25 + .50 * ix / (steps - 1)
                val fy = .25 + .50 * iy / (steps - 1)
                val c = sample(source, x0 + cw * fx, y0 + ch * fy)
                red += ((c shr 16) and 0xff)
                green += ((c shr 8) and 0xff)
                blue += (c and 0xff)
                count++
            }
        }

        val r = red / count
        val g = green / count
        val b = blue / count

        // Measured against the supplied game screenshot. This includes both
        // the darker body cells and the bright head while excluding the dark
        // score digits, red fruit and blue/purple star.
        return g >= 55.0 && g - r >= 12.0 && g - b >= 30.0
    }

    private fun occupancyScore(
        source: PixelSource,
        bounds: PixelRect,
        spec: BoardSpec,
        cellX: Int,
        cellY: Int
    ): Double {
        val x0 = bounds.left + bounds.width * cellX.toDouble() / spec.columns
        val x1 = bounds.left + bounds.width * (cellX + 1).toDouble() / spec.columns
        val y0 = bounds.top + bounds.height * cellY.toDouble() / spec.rows
        val y1 = bounds.top + bounds.height * (cellY + 1).toDouble() / spec.rows
        val cw = x1 - x0
        val ch = y1 - y0

        val corners = listOf(
            sample(source, x0 + cw * .13, y0 + ch * .13),
            sample(source, x0 + cw * .87, y0 + ch * .13),
            sample(source, x0 + cw * .13, y0 + ch * .87),
            sample(source, x0 + cw * .87, y0 + ch * .87)
        )
        val bg = medianColor(corners)

        var changed = 0
        var total = 0
        var contrastSum = 0.0
        val steps = 5
        for (iy in 0 until steps) {
            for (ix in 0 until steps) {
                val fx = .25 + .50 * ix / (steps - 1)
                val fy = .25 + .50 * iy / (steps - 1)
                val c = sample(source, x0 + cw * fx, y0 + ch * fy)
                val d = colorDistance(c, bg)
                contrastSum += d
                if (d >= 36.0) changed++
                total++
            }
        }
        val fraction = changed.toDouble() / total
        val meanContrast = contrastSum / total
        return (fraction * 0.78 + min(1.0, meanContrast / 100.0) * 0.22).coerceIn(0.0, 1.0)
    }

    private fun sample(source: PixelSource, x: Double, y: Double): Int {
        val ix = x.toInt().coerceIn(0, source.width - 1)
        val iy = y.toInt().coerceIn(0, source.height - 1)
        return source.argb(ix, iy)
    }

    private fun medianColor(colors: List<Int>): Int {
        fun channel(shift: Int): Int {
            val values = colors.map { (it shr shift) and 0xff }.sorted()
            return values[values.size / 2]
        }
        val r = channel(16)
        val g = channel(8)
        val b = channel(0)
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun colorDistance(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xff) - ((b shr 16) and 0xff)
        val dg = ((a shr 8) and 0xff) - ((b shr 8) and 0xff)
        val db = (a and 0xff) - (b and 0xff)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    private fun connectedComponents(cells: Set<Cell>, spec: BoardSpec): List<Set<Cell>> {
        val remaining = cells.toMutableSet()
        val result = ArrayList<Set<Cell>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            val component = LinkedHashSet<Cell>()
            val stack = ArrayDeque<Cell>()
            stack += start
            remaining.remove(start)
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                component += c
                for (n in neighbors(c, spec)) {
                    if (remaining.remove(n)) stack += n
                }
            }
            result += component
        }
        return result
    }

    private fun neighbors(c: Cell, spec: BoardSpec): List<Cell> {
        val out = ArrayList<Cell>(4)
        if (c.x > 0) out += Cell(c.x - 1, c.y)
        if (c.x + 1 < spec.columns) out += Cell(c.x + 1, c.y)
        if (c.y > 0) out += Cell(c.x, c.y - 1)
        if (c.y + 1 < spec.rows) out += Cell(c.x, c.y + 1)
        return out
    }
}
