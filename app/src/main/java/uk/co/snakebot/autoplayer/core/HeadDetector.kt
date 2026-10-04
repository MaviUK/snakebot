package uk.co.snakebot.autoplayer.core

/**
 * Cell-based detector tuned and verified against the user's Galaxy recording.
 *
 * Instead of looking for one global lime-colour centroid, we measure the
 * centre of each logical 20x20 board cell. That prevents the bot from locking
 * on to static UI elements such as the score, border or scroll handle.
 */
class HeadDetector {

    fun detectInitial(source: PixelSource, bounds: PixelRect, spec: BoardSpec): Cell? {
        if (!valid(source, bounds)) return null

        val candidates = ArrayList<Pair<Cell, Signal>>()
        for (y in 0 until spec.rows) {
            for (x in 0 until spec.columns) {
                val cell = Cell(x, y)
                val signal = signal(source, bounds, spec, cell)
                if (signal.active) candidates += cell to signal
            }
        }

        if (candidates.size < 2) return null

        // Snake Classic starts a fresh Classic run moving right. After
        // filtering out non-green UI, the right-most active snake cell is the
        // head. Prefer the row with the most active snake cells first.
        val bestRow = candidates
            .groupBy { it.first.y }
            .maxWithOrNull(
                compareBy<Map.Entry<Int, List<Pair<Cell, Signal>>>> { it.value.size }
                    .thenBy { row -> row.value.sumOf { it.second.quality } }
            )
            ?.key
            ?: return null

        return candidates
            .asSequence()
            .filter { it.first.y == bestRow }
            .maxWithOrNull(
                compareBy<Pair<Cell, Signal>> { it.first.x }
                    .thenBy { it.second.quality }
            )
            ?.first
    }

    fun isSnakeCell(
        source: PixelSource,
        bounds: PixelRect,
        spec: BoardSpec,
        cell: Cell
    ): Boolean {
        if (!valid(source, bounds)) return false
        if (cell.x !in 0 until spec.columns || cell.y !in 0 until spec.rows) return false
        return signal(source, bounds, spec, cell).active
    }

    private fun signal(
        source: PixelSource,
        bounds: PixelRect,
        spec: BoardSpec,
        cell: Cell
    ): Signal {
        val x0 = bounds.left + bounds.width * cell.x.toDouble() / spec.columns
        val x1 = bounds.left + bounds.width * (cell.x + 1).toDouble() / spec.columns
        val y0 = bounds.top + bounds.height * cell.y.toDouble() / spec.rows
        val y1 = bounds.top + bounds.height * (cell.y + 1).toDouble() / spec.rows
        val cw = x1 - x0
        val ch = y1 - y0

        // Sample only the central 56% of the logical cell so grid/border lines
        // do not influence the result.
        val greens = ArrayList<Sample>(25)
        val steps = 5
        for (iy in 0 until steps) {
            val fy = .22 + .56 * iy / (steps - 1)
            for (ix in 0 until steps) {
                val fx = .22 + .56 * ix / (steps - 1)
                val px = (x0 + cw * fx).toInt().coerceIn(0, source.width - 1)
                val py = (y0 + ch * fy).toInt().coerceIn(0, source.height - 1)
                val argb = source.argb(px, py)
                greens += Sample(
                    r = (argb shr 16) and 0xff,
                    g = (argb shr 8) and 0xff,
                    b = argb and 0xff
                )
            }
        }

        greens.sortBy { it.g }
        val bright = greens.takeLast(6)
        val r = bright.sumOf { it.r }.toDouble() / bright.size
        val g = bright.sumOf { it.g }.toDouble() / bright.size
        val b = bright.sumOf { it.b }.toDouble() / bright.size
        val dominance = g - maxOf(r, b)

        // Verified frame-by-frame on the supplied recording:
        // snake cells: g >= ~80 and dominance >= ~24
        // score/UI:   g < 60 or dominance < ~18
        // scroll bar: high brightness but near-zero dominance
        val active = g >= 70.0 && dominance >= 18.0
        val quality = g + dominance * 1.5
        return Signal(active, quality)
    }

    private fun valid(source: PixelSource, bounds: PixelRect): Boolean =
        bounds.width > 0 &&
            bounds.height > 0 &&
            bounds.left >= 0 &&
            bounds.top >= 0 &&
            bounds.right <= source.width &&
            bounds.bottom <= source.height

    private data class Sample(val r: Int, val g: Int, val b: Int)
    private data class Signal(val active: Boolean, val quality: Double)
}
