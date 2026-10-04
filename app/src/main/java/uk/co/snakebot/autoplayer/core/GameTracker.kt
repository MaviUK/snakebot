package uk.co.snakebot.autoplayer.core

/**
 * Converts unordered visual snake cells into an ordered body by tracking the
 * head from frame to frame.
 */
class GameTracker {
    private var body: MutableList<Cell>? = null
    private var direction: Direction = Direction.RIGHT
    private var previousCells: Set<Cell>? = null

    fun reset() {
        body = null
        direction = Direction.RIGHT
        previousCells = null
    }

    fun update(snapshot: BoardSnapshot): TrackedGameState? {
        val cells = snapshot.snakeCells
        if (cells.size < 3) return null

        var currentBody = body
        if (currentBody == null) {
            val sourceInitial = listOf(Cell(4, 10), Cell(3, 10), Cell(2, 10))
            if (sourceInitial.all { it in cells }) {
                currentBody = sourceInitial.toMutableList()
                body = currentBody
                direction = Direction.RIGHT
                previousCells = cells
            } else {
                // The current Play Store build can render the fresh three-cell
                // snake in a different horizontal location from the public
                // source. The supplied screenshot clearly shows the face on
                // the rightmost cell, so initialise any clean horizontal
                // three-to-six-cell chain from its right endpoint.
                guessFreshBody(cells, snapshot.spec)?.let { guessed ->
                    currentBody = guessed.toMutableList()
                    body = currentBody
                    if (guessed.size > 1) {
                        Direction.between(
                            guessed[1], guessed[0], snapshot.spec.columns, snapshot.spec.rows
                        )?.let { direction = it }
                    }
                    previousCells = cells
                }

                if (currentBody == null) {
                    previousCells?.let { prev ->
                        val added = cells - prev
                        if (added.size == 1) {
                            val head = added.first()
                            val ordered = orderFromHead(head, cells)
                            if (ordered != null) {
                                currentBody = ordered.toMutableList()
                                body = currentBody
                                if (ordered.size > 1) {
                                    Direction.between(
                                        ordered[1], ordered[0], snapshot.spec.columns, snapshot.spec.rows
                                    )?.let { direction = it }
                                }
                            }
                        }
                    }
                    previousCells = cells
                    if (currentBody == null) return null
                }
            }
        } else {
            val oldSet = currentBody.toSet()
            val added = cells - oldSet
            if (added.size == 1) {
                val newHead = added.first()
                val oldHead = currentBody.first()
                val newDirection = Direction.between(
                    oldHead, newHead, snapshot.spec.columns, snapshot.spec.rows
                )
                if (newDirection != null) {
                    val grew = cells.size > oldSet.size
                    val updated = ArrayList<Cell>(currentBody.size + if (grew) 1 else 0)
                    updated += newHead
                    updated += currentBody
                    if (!grew && updated.isNotEmpty()) updated.removeAt(updated.lastIndex)

                    if (updated.toSet() == cells) {
                        currentBody = updated
                        body = updated
                        direction = newDirection
                    } else {
                        orderFromHead(newHead, cells)?.let { recovered ->
                            currentBody = recovered.toMutableList()
                            body = currentBody
                            direction = newDirection
                        }
                    }
                }
            } else if (added.isNotEmpty()) {
                val candidateHeads = added.filter { c ->
                    neighbors(c, snapshot.spec).count { it in cells } <= 1
                }
                val candidate = candidateHeads.firstOrNull()
                if (candidate != null) {
                    orderFromHead(candidate, cells)?.let { recovered ->
                        val oldHead = currentBody.first()
                        currentBody = recovered.toMutableList()
                        body = currentBody
                        Direction.between(
                            oldHead, candidate, snapshot.spec.columns, snapshot.spec.rows
                        )?.let { direction = it }
                    }
                }
            }
            previousCells = cells
        }

        val ordered = body ?: return null
        return TrackedGameState(
            spec = snapshot.spec,
            body = ordered.toList(),
            direction = direction,
            targets = snapshot.targetCells,
            confidence = snapshot.confidence
        )
    }

    private fun guessFreshBody(cells: Set<Cell>, spec: BoardSpec): List<Cell>? {
        if (cells.size !in 3..6) return null
        val sameRow = cells.map { it.y }.distinct().size == 1
        if (!sameRow) return null

        val endpoints = cells.filter { c ->
            neighbors(c, spec).count { it in cells } == 1
        }
        if (endpoints.size != 2) return null

        val head = endpoints.maxByOrNull { it.x } ?: return null
        return orderFromHead(head, cells)
    }

    private fun orderFromHead(head: Cell, cells: Set<Cell>): List<Cell>? {
        val out = ArrayList<Cell>(cells.size)
        var prev: Cell? = null
        var cur = head
        while (true) {
            out += cur
            if (out.size == cells.size) return out
            val next = orthogonalNeighbors(cur).filter { it in cells && it != prev && it !in out }
            if (next.size != 1) return null
            prev = cur
            cur = next.first()
        }
    }

    private fun neighbors(c: Cell, spec: BoardSpec): List<Cell> =
        orthogonalNeighbors(c).filter { it.x in 0 until spec.columns && it.y in 0 until spec.rows }

    private fun orthogonalNeighbors(c: Cell) = listOf(
        Cell(c.x + 1, c.y), Cell(c.x - 1, c.y), Cell(c.x, c.y + 1), Cell(c.x, c.y - 1)
    )
}
