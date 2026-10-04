package uk.co.snakebot.autoplayer

import android.content.Context
import android.os.SystemClock
import uk.co.snakebot.autoplayer.core.*
import java.util.concurrent.atomic.AtomicBoolean

object BotController {
    const val SNAKE_PACKAGE = "com.pranta.snakeclassic"

    val running = AtomicBoolean(false)
    private val headDetector = HeadDetector()

    @Volatile var lastStatus: String = "Idle"
        private set

    private var lastHead: Cell? = null
    private var currentDirection: Direction? = null
    private var lastGestureAt = 0L
    private var lastGestureDirection: Direction? = null

    fun start() {
        lastHead = null
        currentDirection = null
        lastGestureAt = 0L
        lastGestureDirection = null
        running.set(true)
        lastStatus = "Auto board | finding head..."
        SnakeAccessibilityService.instance?.refreshOverlay()
    }

    fun stop() {
        running.set(false)
        lastHead = null
        currentDirection = null
        lastStatus = "Stopped"
        SnakeAccessibilityService.instance?.refreshOverlay()
    }

    fun processFrame(context: Context, source: PixelSource) {
        if (!running.get()) return

        val bounds = Prefs.ensureSamsungScreenshotPreset(
            context,
            source.width,
            source.height
        ) ?: Prefs.boardBounds(context)

        if (bounds == null) {
            lastStatus = "Auto board unavailable - tap CAL"
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        val spec = Prefs.boardSpec(context)
        val head = headDetector.detect(source, bounds, spec)
        if (head == null) {
            lastStatus = "Auto board | head not found"
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        val previous = lastHead
        if (previous != null && previous != head) {
            inferDirection(previous, head)?.let { currentDirection = it }
        }
        lastHead = head

        val desired = cycleDirection(head, spec)
        if (desired == null) {
            lastStatus = "Head " + head.x + "," + head.y + " | no route"
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        val observed = currentDirection
        if (observed == null) {
            lastStatus = "Head " + head.x + "," + head.y + " | learning direction..."
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        lastStatus = "Head " + head.x + "," + head.y + " | " +
            observed.name + " -> " + desired.name
        SnakeAccessibilityService.instance?.refreshOverlay()

        if (desired == observed || desired == observed.opposite) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 45L) return
        if (lastGestureDirection == desired && now - lastGestureAt < 140L) return

        SnakeAccessibilityService.instance?.swipe(desired, bounds)
        lastGestureAt = now
        lastGestureDirection = desired

        // Snake Classic accepts queued turns quickly; treating the dispatched
        // turn as current immediately prevents duplicate swipes while waiting
        // for the next visual tick.
        currentDirection = desired
    }

    private fun inferDirection(from: Cell, to: Cell): Direction? {
        return when {
            to.y == from.y && to.x > from.x -> Direction.RIGHT
            to.y == from.y && to.x < from.x -> Direction.LEFT
            to.x == from.x && to.y > from.y -> Direction.DOWN
            to.x == from.x && to.y < from.y -> Direction.UP
            else -> null
        }
    }

    private fun cycleDirection(head: Cell, spec: BoardSpec): Direction? {
        val cycle = HamiltonianCycle.build(spec.columns, spec.rows) ?: return null
        if (!HamiltonianCycle.isCycleValid(cycle, spec)) return null
        val index = cycle.indexOf(head)
        if (index < 0) return null
        val next = cycle[(index + 1) % cycle.size]
        return Direction.between(head, next, spec.columns, spec.rows)
    }
}
