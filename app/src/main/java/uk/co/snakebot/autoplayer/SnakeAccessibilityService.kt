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
        showOverlay()
    }

    override fun onDestroy() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
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
        val (ex, ey) = when (direction) {
            Direction.UP -> cx to (cy - distance)
            Direction.DOWN -> cx to (cy + distance)
            Direction.LEFT -> (cx - distance) to cy
            Direction.RIGHT -> (cx + distance) to cy
        }
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(ex, ey)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 32))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun refreshOverlay() {
        mainHandler.post {
            statusText?.text = BotController.lastStatus
            toggleButton?.text = if (BotController.running.get()) "STOP" else "START"
        }
    }

    fun setOverlayStatus(message: String) {
        mainHandler.post {
            statusText?.text = message
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
        // The board is pre-programmed from the supplied Samsung screenshot.
        // START can run immediately; CAL remains available as a fallback.
        BotController.start()
    }

    private fun showOverlay() {
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
            text = "Idle"
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toggleButton = Button(this).apply {
            text = "START"
            setOnClickListener {
                if (BotController.running.get()) {
                    BotController.stop()
                } else {
                    startBotFromOverlay()
                }
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
        row.addView(toggleButton)
        row.addView(calibrate)
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
            x = 12
            y = 35
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
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        BotController.start()
                        return
                    }
                    parent = parent.parent
                }
            }
        }
    }
}
