package com.alireza.podcasts

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineOverlay: LinearLayout
    private lateinit var btnRetry: Button
    
    private val targetUrl = "https://podcasts.apple.com/"
    
    // Gesture variables
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { showOptionMenu() }
    private var isTwoFingerGestureActive = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val moveTolerance = 40f // Allow slight movement before cancelling long-press

    // Connectivity monitoring
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                hideOfflineState()
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                showOfflineState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        offlineOverlay = findViewById(R.id.offline_overlay)
        btnRetry = findViewById(R.id.btn_retry)

        setupWebView()
        setupGestureDetector()
        setupConnectivityMonitor()

        btnRetry.setOnClickListener {
            if (isNetworkAvailable()) {
                hideOfflineState()
                webView.reload()
            } else {
                Toast.makeText(this, "Still no connection. Check your Wi-Fi or data.", Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            if (isNetworkAvailable()) {
                webView.loadUrl(targetUrl)
            } else {
                showOfflineState()
            }
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrlRouting(request.url.toString())
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrlRouting(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    showOfflineState()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                showOfflineState()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun handleUrlRouting(url: String): Boolean {
        if (url.contains("apple.com") || url.contains("apple-dns.net")) {
            return false
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    private fun setupGestureDetector() {
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        isTwoFingerGestureActive = true
                        touchDownX = (event.getX(0) + event.getX(1)) / 2
                        touchDownY = (event.getY(0) + event.getY(1)) / 2
                        gestureHandler.postDelayed(longPressRunnable, 1000) // 1 second hold
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTwoFingerGestureActive && event.pointerCount == 2) {
                        val currentX = (event.getX(0) + event.getX(1)) / 2
                        val currentY = (event.getY(0) + event.getY(1)) / 2
                        val diffX = Math.abs(currentX - touchDownX)
                        val diffY = Math.abs(currentY - touchDownY)
                        if (diffX > moveTolerance || diffY > moveTolerance) {
                            cancelLongPressGesture()
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPressGesture()
                }
            }
            false // Propagate touches to WebView so scrolling and clicking work
        }
    }

    private fun cancelLongPressGesture() {
        gestureHandler.removeCallbacks(longPressRunnable)
        isTwoFingerGestureActive = false
    }

    private fun showOptionMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val menuHome = dialog.findViewById<LinearLayout>(R.id.menu_home)
        val menuBack = dialog.findViewById<LinearLayout>(R.id.menu_back)
        val menuRefresh = dialog.findViewById<LinearLayout>(R.id.menu_refresh)
        val menuClearCache = dialog.findViewById<LinearLayout>(R.id.menu_clear_cache)
        val menuBattery = dialog.findViewById<LinearLayout>(R.id.menu_battery)

        menuHome.setOnClickListener {
            dialog.dismiss()
            webView.loadUrl(targetUrl)
        }

        menuBack.setOnClickListener {
            dialog.dismiss()
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                Toast.makeText(this, "No page to go back to.", Toast.LENGTH_SHORT).show()
            }
        }

        menuRefresh.setOnClickListener {
            dialog.dismiss()
            webView.reload()
        }

        menuClearCache.setOnClickListener {
            dialog.dismiss()
            webView.clearCache(true)
            Toast.makeText(this, "Cache cleared successfully!", Toast.LENGTH_SHORT).show()
        }

        menuBattery.setOnClickListener {
            dialog.dismiss()
            openBatteryOptimizationSettings()
        }

        dialog.show()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to application settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Unable to open system settings.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupConnectivityMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
    }

    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOfflineState() {
        offlineOverlay.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun hideOfflineState() {
        offlineOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    // Audio background optimization: DO NOT call webView.onPause() to keep background JavaScript and audio running.
    override fun onPause() {
        super.onPause()
        // Kept empty to let WebView continue playing audio in the background
    }

    override fun onResume() {
        super.onResume()
        // Kept empty to match background playback optimization
    }
}
