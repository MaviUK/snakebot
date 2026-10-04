package uk.co.snakebot.autoplayer

import android.content.Context
import uk.co.snakebot.autoplayer.core.BoardSpec
import uk.co.snakebot.autoplayer.core.PixelRect
import kotlin.math.roundToInt

object Prefs {
    private const val NAME = "snake_bot"
    private const val KEY_LEFT = "board_left"
    private const val KEY_TOP = "board_top"
    private const val KEY_RIGHT = "board_right"
    private const val KEY_BOTTOM = "board_bottom"
    private const val KEY_COLS = "board_cols"
    private const val KEY_ROWS = "board_rows"
    private const val KEY_AUTO_RESTART = "auto_restart"
    private const val KEY_MANUAL_BOARD = "manual_board"
    private const val KEY_LAYOUT_PRESET_VERSION = "layout_preset_version"
    private const val KEY_LAYOUT_WIDTH = "layout_width"
    private const val KEY_LAYOUT_HEIGHT = "layout_height"

    // Derived from the supplied Samsung screenshot:
    // - board spans the full captured screen width
    // - top edge is at exactly ~1/6 of capture height
    // - board is square
    private const val LAYOUT_PRESET_VERSION = 2

    fun boardSpec(context: Context): BoardSpec {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return BoardSpec(p.getInt(KEY_COLS, 20), p.getInt(KEY_ROWS, 20))
    }

    fun setBoardSpec(context: Context, spec: BoardSpec) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COLS, spec.columns)
            .putInt(KEY_ROWS, spec.rows)
            .apply()
    }

    fun boardBounds(context: Context): PixelRect? {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (!p.contains(KEY_LEFT)) return null
        return PixelRect(
            p.getInt(KEY_LEFT, 0),
            p.getInt(KEY_TOP, 0),
            p.getInt(KEY_RIGHT, 0),
            p.getInt(KEY_BOTTOM, 0)
        )
    }

    fun setBoardBounds(context: Context, rect: PixelRect) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LEFT, rect.left)
            .putInt(KEY_TOP, rect.top)
            .putInt(KEY_RIGHT, rect.right)
            .putInt(KEY_BOTTOM, rect.bottom)
            .putBoolean(KEY_MANUAL_BOARD, true)
            .apply()
    }

    fun ensureSamsungScreenshotPreset(
        context: Context,
        captureWidth: Int,
        captureHeight: Int
    ): PixelRect? {
        if (captureWidth < 500 || captureHeight < captureWidth * 1.8) {
            return boardBounds(context)
        }

        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val sameGeometry =
            p.getInt(KEY_LAYOUT_WIDTH, -1) == captureWidth &&
            p.getInt(KEY_LAYOUT_HEIGHT, -1) == captureHeight
        val presetCurrent =
            p.getInt(KEY_LAYOUT_PRESET_VERSION, 0) >= LAYOUT_PRESET_VERSION &&
            sameGeometry

        if (presetCurrent && p.getBoolean(KEY_MANUAL_BOARD, false)) {
            return boardBounds(context)
        }

        if (!presetCurrent) {
            val top = (captureHeight / 6.0).roundToInt()
            val bottom = (top + captureWidth).coerceAtMost(captureHeight)
            if (bottom - top < captureWidth * 0.95) return boardBounds(context)

            val rect = PixelRect(
                left = 0,
                top = top,
                right = captureWidth,
                bottom = bottom
            )

            p.edit()
                .putInt(KEY_LEFT, rect.left)
                .putInt(KEY_TOP, rect.top)
                .putInt(KEY_RIGHT, rect.right)
                .putInt(KEY_BOTTOM, rect.bottom)
                .putInt(KEY_COLS, 20)
                .putInt(KEY_ROWS, 20)
                .putBoolean(KEY_MANUAL_BOARD, false)
                .putInt(KEY_LAYOUT_PRESET_VERSION, LAYOUT_PRESET_VERSION)
                .putInt(KEY_LAYOUT_WIDTH, captureWidth)
                .putInt(KEY_LAYOUT_HEIGHT, captureHeight)
                .apply()
            return rect
        }

        return boardBounds(context)
    }

    fun isAutoPreset(context: Context): Boolean {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return p.getInt(KEY_LAYOUT_PRESET_VERSION, 0) >= LAYOUT_PRESET_VERSION &&
            !p.getBoolean(KEY_MANUAL_BOARD, false)
    }

    fun autoRestart(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_RESTART, true)

    fun setAutoRestart(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_RESTART, enabled).apply()
    }
}
