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
    private var lastGestureAt = 0L
    private var lastQueuedCornerIndex = -1
    private var lastFallbackCornerIndex = -1

    fun start() {
        head = null
        lastGestureAt = 0L
        lastQueuedCornerIndex = -1
        lastFallbackCornerIndex = -1
        running.set(true)
        lastStatus = "Fresh-run mode | finding snake..."
        SnakeAccessibilityService.instance?.refreshOverlay()
    }

    fun stop() {
        running.set(false)
        head = null
        lastQueuedCornerIndex = -1
        lastFallbackCornerIndex = -1
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
        val cycle = HamiltonianCycle.build(spec.columns, spec.rows)
        if (cycle == null || !HamiltonianCycle.isCycleValid(cycle, spec)) {
            setStatus("No safe route for this board")
            return
        }

        var tracked = head
        if (tracked == null) {
            tracked = headDetector.detectInitial(source, bounds, spec)
            if (tracked == null) {
                setStatus("Auto board | finding snake...")
                return
            }
            head = tracked
        } else {
            // Catch up over several cells if MediaProjection/bitmap processing
            // misses one or more visual ticks. Every skipped cell the head has
            // just traversed is now part of the snake body, so the forward arc
            // of the Hamiltonian route is a reliable recovery path.
            val startIndex = cycle.indexOf(tracked)
            if (startIndex >= 0) {
                var best = tracked
                for (step in 1..5) {
                    val candidate = cycle[(startIndex + step) % cycle.size]
                    if (headDetector.isSnakeCell(source, bounds, spec, candidate)) {
                        best = candidate
                    } else {
                        break
                    }
                }
                tracked = best
                head = best
            }
        }

        val index = cycle.indexOf(tracked)
        if (index < 0) {
            setStatus("Head lost | re-syncing...")
            head = null
            return
        }

        val previous = cycle[(index - 1 + cycle.size) % cycle.size]
        val next = cycle[(index + 1) % cycle.size]
        val afterNext = cycle[(index + 2) % cycle.size]

        val incoming = Direction.between(previous, tracked, spec.columns, spec.rows)
        val outgoing = Direction.between(tracked, next, spec.columns, spec.rows)
        val nextOutgoing = Direction.between(next, afterNext, spec.columns, spec.rows)

        if (incoming == null || outgoing == null || nextOutgoing == null) {
            setStatus("Route sync error")
            return
        }

        val now = SystemClock.elapsedRealtime()

        // If the NEXT cell is a corner, fill Snake Classic's two-slot input
        // buffer now: keep straight for one tick, then make the turn.
        if (nextOutgoing != outgoing) {
            val cornerIndex = (index + 1) % cycle.size
            if (cornerIndex != lastQueuedCornerIndex && now - lastGestureAt >= 140L) {
                SnakeAccessibilityService.instance?.queueCorner(
                    outgoing,
                    nextOutgoing,
                    bounds
                )
                lastGestureAt = now
                lastQueuedCornerIndex = cornerIndex
                setStatus(
                    "QUEUE " + outgoing.name + " -> " + nextOutgoing.name +
                        " | corner " + next.x + "," + next.y
                )
                return
            }
        }

        // Fallback for a bot that was started on the corner itself or if a
        // queued gesture could not be sent. Repeating the already-queued turn
        // is harmless in Snake Classic; its input buffer treats same-direction
        // input as the current intent.
        if (outgoing != incoming) {
            if (index != lastFallbackCornerIndex && now - lastGestureAt >= 150L) {
                SnakeAccessibilityService.instance?.swipe(outgoing, bounds)
                lastGestureAt = now
                lastFallbackCornerIndex = index
                setStatus(
                    "TURN " + outgoing.name + " @ " + tracked.x + "," + tracked.y
                )
                return
            }
        }

        // Clear old corner markers once we are safely past them.
        if (lastQueuedCornerIndex >= 0 &&
            index != lastQueuedCornerIndex &&
            index != (lastQueuedCornerIndex - 1 + cycle.size) % cycle.size
        ) {
            lastQueuedCornerIndex = -1
        }
        if (lastFallbackCornerIndex >= 0 && index != lastFallbackCornerIndex) {
            lastFallbackCornerIndex = -1
        }

        setStatus(
            "Head " + tracked.x + "," + tracked.y +
                " | " + incoming.name + " -> " + outgoing.name
        )
    }

    private fun setStatus(value: String) {
        lastStatus = value
        SnakeAccessibilityService.instance?.refreshOverlay()
    }
}
