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

    private var head: Cell? = null
    private var currentDirection: Direction = Direction.RIGHT
    private var lastGestureAt = 0L
    private var lastGestureDirection: Direction? = null

    fun start() {
        head = null
        // Fresh Classic runs in the Play Store build start moving right.
        currentDirection = Direction.RIGHT
        lastGestureAt = 0L
        lastGestureDirection = null
        running.set(true)
        lastStatus = "Fresh-run mode | finding snake..."
        SnakeAccessibilityService.instance?.refreshOverlay()
    }

    fun stop() {
        running.set(false)
        head = null
        currentDirection = Direction.RIGHT
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
            setStatus("Auto board unavailable - tap CAL")
            return
        }

        val spec = Prefs.boardSpec(context)
        var tracked = head

        if (tracked == null) {
            tracked = headDetector.detectInitial(source, bounds, spec)
            if (tracked == null) {
                setStatus("Auto board | finding snake...")
                return
            }
            head = tracked
        } else {
            val expected = tracked.move(currentDirection)
            if (expected.x in 0 until spec.columns &&
                expected.y in 0 until spec.rows &&
                headDetector.isSnakeCell(source, bounds, spec, expected)
            ) {
                tracked = expected
                head = expected
            }
        }

        val desired = cycleDirection(tracked, spec)
        if (desired == null) {
            setStatus("Head " + tracked.x + "," + tracked.y + " | no route")
            return
        }

        setStatus(
            "Head " + tracked.x + "," + tracked.y + " | " +
                currentDirection.name + " -> " + desired.name
        )

        if (desired == currentDirection) return
        if (desired == currentDirection.opposite) {
            // This should not occur on a correctly tracked fresh Hamiltonian
            // run. Holding course is safer than sending an illegal reverse.
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 45L) return
        if (lastGestureDirection == desired && now - lastGestureAt < 140L) return

        SnakeAccessibilityService.instance?.swipe(desired, bounds)
        lastGestureAt = now
        lastGestureDirection = desired
        currentDirection = desired
        setStatus(
            "TURN " + desired.name + " @ " + tracked.x + "," + tracked.y
        )
    }

    private fun setStatus(value: String) {
        lastStatus = value
        SnakeAccessibilityService.instance?.refreshOverlay()
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
