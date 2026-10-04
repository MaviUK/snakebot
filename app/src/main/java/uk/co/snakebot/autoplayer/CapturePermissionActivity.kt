package uk.co.snakebot.autoplayer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Small transparent activity used when START is pressed from the accessibility
 * overlay and screen capture has not been granted yet. Android requires an
 * Activity to show the MediaProjection consent dialog, so this bridges the
 * floating overlay to the system permission prompt and then immediately
 * returns the user to Snake Classic.
 */
class CapturePermissionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        private const val REQUEST_CAPTURE = 2201
    }

    private var autoStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)

        if (CaptureService.instance != null) {
            finishAfterCaptureAlreadyRunning()
            return
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated in Android API; kept for broad compatibility with the capture consent flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, service)
            Toast.makeText(this, "Screen capture enabled", Toast.LENGTH_SHORT).show()

            if (autoStart) {
                if (Prefs.boardBounds(this) == null) {
                    SnakeAccessibilityService.instance?.setOverlayStatus("Capture ON - tap CAL first")
                } else {
                    BotController.start()
                }
            }
        } else {
            SnakeAccessibilityService.instance?.setOverlayStatus("Screen capture permission cancelled")
        }
        finish()
    }

    private fun finishAfterCaptureAlreadyRunning() {
        if (autoStart) {
            if (Prefs.boardBounds(this) == null) {
                SnakeAccessibilityService.instance?.setOverlayStatus("Tap CAL first")
            } else {
                BotController.start()
            }
        }
        finish()
    }
}
