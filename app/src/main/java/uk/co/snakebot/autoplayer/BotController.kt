package uk.co.snakebot.autoplayer

import android.content.Context
import android.os.SystemClock
import uk.co.snakebot.autoplayer.core.*
import java.util.concurrent.atomic.AtomicBoolean

object BotController {
    const val SNAKE_PACKAGE = "com.pranta.snakeclassic"

    val running = AtomicBoolean(false)
    private val detector = BoardDetector()
    private val tracker = GameTracker()
    private val player = AutoPlayer()

    @Volatile var lastStatus: String = "Idle"
        private set
    private var lastMove: Direction? = null
    private var lastGestureAt = 0L

    fun start() {
        tracker.reset()
        lastMove = null
        lastGestureAt = 0L
        running.set(true)
        lastStatus = "Armed - auto board"
        SnakeAccessibilityService.instance?.refreshOverlay()
    }

    fun stop() {
        running.set(false)
        tracker.reset()
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
        val snapshot = detector.detect(source, bounds, spec)
        if (snapshot == null) {
            lastStatus = if (Prefs.isAutoPreset(context)) {
                "Auto board set | looking for snake..."
            } else {
                "Looking for snake..."
            }
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        val state = tracker.update(snapshot) ?: run {
            lastStatus = "Tracking head... start a fresh run"
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }
        val desired = player.nextDirection(state) ?: run {
            lastStatus = "No safe move"
            SnakeAccessibilityService.instance?.refreshOverlay()
            return
        }

        lastStatus = state.body.size.toString() + " cells | " +
            state.head.x.toString() + "," + state.head.y.toString() +
            " | " + desired.name
        SnakeAccessibilityService.instance?.refreshOverlay()

        if (desired == state.direction || desired == lastMove) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 48L) return
        lastGestureAt = now
        lastMove = desired
        SnakeAccessibilityService.instance?.swipe(desired, bounds)
    }
}
