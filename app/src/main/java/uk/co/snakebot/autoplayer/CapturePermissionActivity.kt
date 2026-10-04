package uk.co.snakebot.autoplayer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Transparent bridge for Android's MediaProjection consent dialog.
 *
 * START can force a fresh capture session. This matters on Samsung/Android
 * after updating the APK: the foreground capture service can still exist
 * while its old projection has stopped delivering frames.
 */
class CapturePermissionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        const val EXTRA_FORCE_CAPTURE = "force_capture"
        private const val REQUEST_CAPTURE = 2201
    }

    private var autoStart = false
    private var forceCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)
        forceCapture = intent.getBooleanExtra(EXTRA_FORCE_CAPTURE, false)

        if (forceCapture) {
            BotController.stop()
            CaptureService.instance?.stopSelf()
        } else if (CaptureService.instance != null) {
            if (autoStart) BotController.start()
            finish()
            return
        }

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        )
    }

    @Deprecated("Kept for compatibility with the MediaProjection consent flow")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)

            ContextCompat.startForegroundService(this, service)
            Toast.makeText(
                this,
                "Fresh screen capture enabled",
                Toast.LENGTH_SHORT
            ).show()

            // The Samsung board preset is applied from the first captured
            // frame, so manual CAL is not required before arming the bot.
            if (autoStart) BotController.start()
        } else {
            SnakeAccessibilityService.instance?.setOverlayStatus(
                "Screen capture permission cancelled"
            )
        }
        finish()
    }
}
