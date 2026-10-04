package uk.co.snakebot.autoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import uk.co.snakebot.autoplayer.core.Direction
import uk.co.snakebot.autoplayer.core.PixelRect
import kotlin.math.max

class SnakeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: SnakeAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName, ignoreCase = true)
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlay: LinearLayout? = null
    private var statusText: TextView? = null
    private var toggleButton: Button? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlayControls()
    }

    override fun onDestroy() {
        hideOverlayControls()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BotController.running.get() || !Prefs.autoRestart(this)) return
        if (event?.packageName?.toString() != BotController.SNAKE_PACKAGE) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            tryAutoRestart(rootInActiveWindow)
        }
    }

    override fun onInterrupt() = Unit

    fun swipe(direction: Direction, board: PixelRect) {
        val cx = (board.left + board.right) / 2f
        val cy = (board.top + board.bottom) / 2f
        val distance = max(80f, minOf(board.width, board.height) * 0.16f)
        val (ex, ey) = step(cx, cy, direction, distance)
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(ex, ey)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 32))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Snake Classic has a two-turn input buffer. A single bent gesture sends:
     *   1) keep going straight on the next tick
     *   2) turn on the tick after that
     *
     * This lets us arm a corner one cell early. It removes the race where the
     * visual capture sees the edge cell only a few milliseconds before the
     * next 50-100ms game tick.
     */
    fun queueCorner(straight: Direction, turn: Direction, board: PixelRect) {
        if (turn == straight || turn == straight.opposite) return

        val cx = (board.left + board.right) / 2f
        val cy = (board.top + board.bottom) / 2f
        val distance = max(90f, minOf(board.width, board.height) * 0.13f)
        val (x1, y1) = step(cx, cy, straight, distance)
        val (x2, y2) = step(x1, y1, turn, distance)

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(x1, y1)
            lineTo(x2, y2)
        }

        // 130ms is deliberate. Snake Classic rejects inputs less than 50ms
        // apart; this duration gives the two legs enough separation while
        // still completing before the second 50ms tick at maximum speed.
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 130))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun step(
        x: Float,
        y: Float,
        direction: Direction,
        distance: Float
    ): Pair<Float, Float> = when (direction) {
        Direction.UP -> x to (y - distance)
        Direction.DOWN -> x to (y + distance)
        Direction.LEFT -> (x - distance) to y
        Direction.RIGHT -> (x + distance) to y
    }

    fun refreshOverlay() {
        mainHandler.post {
            statusText?.text = BotController.lastStatus
            toggleButton?.text = if (BotController.running.get()) "STOP" else "START"
        }
    }

    fun setOverlayStatus(message: String) {
        mainHandler.post { statusText?.text = message }
    }

    fun showOverlayControls() {
        mainHandler.post {
            if (overlay == null) showOverlayInternal()
        }
    }

    fun hideOverlayControls() {
        mainHandler.post {
            overlay?.let { runCatching { windowManager.removeView(it) } }
            overlay = null
            statusText = null
            toggleButton = null
        }
    }

    private fun startBotFromOverlay() {
        if (CaptureService.instance == null) {
            setOverlayStatus("Capture OFF - allow screen capture")
            val intent = Intent(this, CapturePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(CapturePermissionActivity.EXTRA_AUTO_START, true)
            startActivity(intent)
            return
        }
        BotController.start()
    }

    private fun closeBot() {
        BotController.stop()
        CaptureService.instance?.stopSelf()
        hideOverlayControls()
    }

    private fun showOverlayInternal() {
        if (overlay != null) return

        val bg = GradientDrawable().apply {
            cornerRadius = 18f
            setColor(Color.argb(225, 15, 20, 15))
            setStroke(2, Color.rgb(176, 224, 0))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            background = bg
        }
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            text = BotController.lastStatus
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        toggleButton = Button(this).apply {
            text = if (BotController.running.get()) "STOP" else "START"
            setOnClickListener {
                if (BotController.running.get()) BotController.stop()
                else startBotFromOverlay()
            }
        }

        val calibrate = Button(this).apply {
            text = "CAL"
            setOnClickListener {
                val file = CaptureService.instance?.saveCalibrationFrame()
                if (file != null) {
                    val intent = Intent(this@SnakeAccessibilityService, CalibrationActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(CalibrationActivity.EXTRA_PATH, file.absolutePath)
                    startActivity(intent)
                } else {
                    statusText?.text = "Start screen capture first"
                }
            }
        }

        val close = Button(this).apply {
            text = "CLOSE"
            setOnClickListener { closeBot() }
        }

        row.addView(toggleButton)
        row.addView(calibrate)
        row.addView(close)
        root.addView(statusText)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 8
            y = 24
        }
        windowManager.addView(root, params)
        overlay = root
    }

    private fun tryAutoRestart(root: AccessibilityNodeInfo?) {
        if (root == null) return
        val labels = listOf("Play Again", "Retry", "Restart")
        for (label in labels) {
            val matches = root.findAccessibilityNodeInfosByText(label)
            for (node in matches) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    BotController.start()
                    return
                }
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable &&
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ) {
                        BotController.start()
                        return
                    }
                    parent = parent.parent
                }
            }
        }
    }
}
