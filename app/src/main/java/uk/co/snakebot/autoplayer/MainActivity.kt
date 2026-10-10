package uk.co.snakebot.autoplayer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GAME_URL =
            "https://nokia-snake-3310.netlify.app/auto.html"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var offlineView: LinearLayout
    private var pageLoaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 22, 21))
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(16, 22, 21))

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
            }

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded = true
                    showGame()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        pageLoaded = false
                        showOffline()
                    }
                }
            }
        }

        offlineView = buildOfflineView()

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            offlineView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
        enterImmersiveMode()

        if (savedInstanceState == null) {
            webView.loadUrl(GAME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun buildOfflineView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(16, 22, 21))
            visibility = View.GONE

            addView(TextView(this@MainActivity).apply {
                text = "SNAKE"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = "No connection.\nReconnect and try again."
                textSize = 16f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 22, 0, 22)
            })

            addView(Button(this@MainActivity).apply {
                text = "RETRY"
                setOnClickListener {
                    visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    webView.loadUrl(GAME_URL)
                }
            })
        }
    }

    private fun showGame() {
        webView.visibility = View.VISIBLE
        offlineView.visibility = View.GONE
    }

    private fun showOffline() {
        webView.visibility = View.GONE
        offlineView.visibility = View.VISIBLE
    }

    private fun toggleGameMenu() {
        if (!pageLoaded) {
            finish()
            return
        }

        webView.evaluateJavascript(
            """
            (() => {
              const menu = document.getElementById('lcdMenu');
              const button = document.getElementById('menuBtn');
              if (!menu || !button) return false;
              button.click();
              return true;
            })();
            """.trimIndent(),
            null
        )
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    @Deprecated("Handled as an in-game menu button")
    override fun onBackPressed() {
        toggleGameMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
