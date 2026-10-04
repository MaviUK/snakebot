package uk.co.snakebot.autoplayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import uk.co.snakebot.autoplayer.core.BoardSpec

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var boardSpinner: Spinner

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(CaptureService.EXTRA_DATA, result.data)
            ContextCompat.startForegroundService(this, service)
            Toast.makeText(this, "Screen capture started", Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }

        fun addTitle(text: String, size: Float) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
            })
        }

        addTitle("Snake Auto Player", 28f)
        addTitle(
            "Galaxy preset: Classic 20 x 20. Start a fresh run, then press START in the floating controls.",
            15f
        )

        status = TextView(this).apply { textSize = 14f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Enable accessibility control"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Button(this).apply {
            text = "Start screen capture"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                captureLauncher.launch(manager.createScreenCaptureIntent())
            }
        })

        root.addView(Button(this).apply {
            text = "Show floating controls"
            setOnClickListener {
                val service = SnakeAccessibilityService.instance
                if (service != null) {
                    service.showOverlayControls()
                    Toast.makeText(this@MainActivity, "Controls shown", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Enable Snake Auto Player in Accessibility first",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        root.addView(TextView(this).apply { text = "Board size"; textSize = 15f })
        val labels = arrayOf(
            "15 x 15", "20 x 20", "25 x 25", "30 x 30", "35 x 35",
            "40 x 40", "18 x 32 Tall", "24 x 42 Tall Plus", "50 x 50"
        )
        val specs = arrayOf(
            BoardSpec(15,15), BoardSpec(20,20), BoardSpec(25,25), BoardSpec(30,30),
            BoardSpec(35,35), BoardSpec(40,40), BoardSpec(18,32), BoardSpec(24,42), BoardSpec(50,50)
        )

        boardSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
            val current = Prefs.boardSpec(this@MainActivity)
            setSelection(specs.indexOfFirst { it == current }.coerceAtLeast(1))
            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        Prefs.setBoardSpec(this@MainActivity, specs[position])
                    }
                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?
                    ) = Unit
                }
        }
        root.addView(boardSpinner)

        val autoRestart = CheckBox(this).apply {
            text = "Auto-restart after a crash"
            isChecked = Prefs.autoRestart(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setAutoRestart(this@MainActivity, checked)
            }
        }
        root.addView(autoRestart)

        root.addView(Button(this).apply {
            text = "Open Snake Classic"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(BotController.SNAKE_PACKAGE)
                if (launch != null) startActivity(launch)
                else Toast.makeText(
                    this@MainActivity,
                    "Snake Classic is not installed",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Stop capture & hide controls"
            setOnClickListener {
                BotController.stop()
                CaptureService.instance?.stopSelf()
                SnakeAccessibilityService.instance?.hideOverlayControls()
                refreshStatus()
            }
        })

        return root
    }

    private fun refreshStatus() {
        val access = if (SnakeAccessibilityService.isEnabled(this)) "ON" else "OFF"
        val capture = if (CaptureService.instance != null) "ON" else "OFF"
        status.text = "Accessibility: " + access + "   |   Capture: " + capture
    }
}
