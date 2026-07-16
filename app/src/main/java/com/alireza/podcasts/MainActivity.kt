package com.alireza.podcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.alireza.podcasts.RoutingUtils.RouteType

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineOverlay: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnMenu: ImageButton
    
    // Config URLs (mapped from strings.xml)
    private val targetUrl by lazy { getString(R.string.target_url) }
    
    // Gesture variables
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { showOptionMenu() }
    private var isTwoFingerGestureActive = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val moveTolerance = 40f // Allow slight movement before cancelling long-press

    // Connectivity monitoring
    private lateinit var connectivityManager: ConnectivityManager
    private var wasOffline = false
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateConnectivityUi()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateConnectivityUi()
        }

        override fun onLost(network: Network) {
            // Hand-off protection: wait 300ms to verify if another validated connection takes over
            gestureHandler.postDelayed({
                updateConnectivityUi()
            }, 300)
        }
    }

    // Media receiver for system lockscreen inputs
    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isDestroyed || isFinishing) return
            when (intent?.action) {
                MediaPlaybackService.BROADCAST_PLAY -> {
                    webView.post {
                        webView.evaluateJavascript(
                            "const v = document.querySelector('video, audio'); if (v) v.play();", 
                            null
                        )
                    }
                }
                MediaPlaybackService.BROADCAST_PAUSE -> {
                    webView.post {
                        webView.evaluateJavascript(
                            "const v = document.querySelector('video, audio'); if (v) v.pause();", 
                            null
                        )
                    }
                }
                MediaPlaybackService.BROADCAST_SEEK -> {
                    val seekPos = intent.getLongExtra(MediaPlaybackService.EXTRA_SEEK_POS, 0L)
                    webView.post {
                        webView.evaluateJavascript(
                            "const v = document.querySelector('video, audio'); if (v) v.currentTime = ${seekPos / 1000.0};", 
                            null
                        )
                    }
                }
            }
        }
    }

    // Notification permission request callback
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.toast_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize splash screen before onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        offlineOverlay = findViewById(R.id.offline_overlay)
        btnRetry = findViewById(R.id.btn_retry)
        btnMenu = findViewById(R.id.btn_menu)

        setupWebView()
        setupGestureDetector()
        setupConnectivityMonitor()
        setupMediaReceiver()
        checkNotificationPermission()

        btnMenu.setOnClickListener {
            showOptionMenu()
        }

        btnRetry.setOnClickListener {
            if (isNetworkAvailable()) {
                hideOfflineState()
                wasOffline = false
                webView.reload()
            } else {
                Toast.makeText(this, getString(R.string.toast_no_connection), Toast.LENGTH_SHORT).show()
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

        // Restore state exactly once in onCreate if savedInstanceState is not null
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            if (isNetworkAvailable()) {
                webView.loadUrl(targetUrl)
            } else {
                showOfflineState()
                wasOffline = true
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

        // Set WebView Download Listener
        webView.setDownloadListener { url, _, _, _, _ ->
            if (isDestroyed || isFinishing) return@setDownloadListener
            openCustomTab(url)
        }

        // Add Javascript Bridge for Media Session syncing
        webView.addJavascriptInterface(MediaBridge(), "AndroidMediaBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (request.isForMainFrame) {
                    return handleUrlRouting(url)
                }
                return false
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
                injectMediaSessionBridge()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode
                    // Only show offline warning layout for connection failures (DNS, connect, timeouts)
                    if (errorCode == ERROR_HOST_LOOKUP || 
                        errorCode == ERROR_CONNECT || 
                        errorCode == ERROR_TIMEOUT || 
                        errorCode == ERROR_UNKNOWN) {
                        showOfflineState()
                        wasOffline = true
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (errorCode == ERROR_HOST_LOOKUP || 
                    errorCode == ERROR_CONNECT || 
                    errorCode == ERROR_TIMEOUT || 
                    errorCode == ERROR_UNKNOWN) {
                    showOfflineState()
                    wasOffline = true
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: return
                    Toast.makeText(this@MainActivity, getString(R.string.toast_http_error, statusCode), Toast.LENGTH_LONG).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                    injectMediaSessionBridge()
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun handleUrlRouting(url: String): Boolean {
        return when (RoutingUtils.getRouteType(url)) {
            RouteType.INTERNAL_WEBVIEW -> {
                false // Allow standard internal load
            }
            RouteType.DEEP_LINK_INTENT -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.toast_no_app_found), Toast.LENGTH_SHORT).show()
                }
                true
            }
            RouteType.EXTERNAL_CUSTOM_TAB -> {
                openCustomTab(url)
                true
            }
            RouteType.BLOCKED -> {
                true // Block navigation silently
            }
        }
    }

    private fun openCustomTab(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            try {
                // Fallback browser intent
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, getString(R.string.toast_no_app_found), Toast.LENGTH_SHORT).show()
            }
        }
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
            false // Propagate touches to WebView
        }
    }

    private fun cancelLongPressGesture() {
        gestureHandler.removeCallbacks(longPressRunnable)
        isTwoFingerGestureActive = false
    }

    private fun showOptionMenu() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_menu)
        
        // Remove background overlay so top rounded corners are visible
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val menuHome = dialog.findViewById<LinearLayout>(R.id.menu_home)
        val menuBack = dialog.findViewById<LinearLayout>(R.id.menu_back)
        val menuRefresh = dialog.findViewById<LinearLayout>(R.id.menu_refresh)
        val menuClearCache = dialog.findViewById<LinearLayout>(R.id.menu_clear_cache)
        val menuBattery = dialog.findViewById<LinearLayout>(R.id.menu_battery)
        val menuGithub = dialog.findViewById<LinearLayout>(R.id.menu_github)

        menuHome?.setOnClickListener {
            dialog.dismiss()
            webView.loadUrl(targetUrl)
        }

        menuBack?.setOnClickListener {
            dialog.dismiss()
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                Toast.makeText(this, getString(R.string.toast_no_back_page), Toast.LENGTH_SHORT).show()
            }
        }

        menuRefresh?.setOnClickListener {
            dialog.dismiss()
            webView.reload()
        }

        menuClearCache?.setOnClickListener {
            dialog.dismiss()
            webView.clearCache(true)
            Toast.makeText(this, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
        }

        menuBattery?.setOnClickListener {
            dialog.dismiss()
            openBatteryOptimizationSettings()
        }

        menuGithub?.setOnClickListener {
            dialog.dismiss()
            openCustomTab(getString(R.string.git_repo_url))
        }

        dialog.show()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, getString(R.string.toast_settings_error), Toast.LENGTH_SHORT).show()
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
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateConnectivityUi() {
        if (isDestroyed || isFinishing) return
        val hasInternet = isNetworkAvailable()
        runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            if (hasInternet) {
                hideOfflineState()
                if (wasOffline) {
                    wasOffline = false
                    webView.reload()
                }
            } else {
                showOfflineState()
                wasOffline = true
            }
        }
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

    private fun setupMediaReceiver() {
        val filter = IntentFilter().apply {
            addAction(MediaPlaybackService.BROADCAST_PLAY)
            addAction(MediaPlaybackService.BROADCAST_PAUSE)
            addAction(MediaPlaybackService.BROADCAST_SEEK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaReceiver, filter)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun injectMediaSessionBridge() {
        // Polling JavaScript to extract HTML5 player metadata and pipe to Kotlin bridge
        val js = """
            (function() {
                if (window.hasAppleMediaSessionHooked) return;
                window.hasAppleMediaSessionHooked = true;
                
                let lastPlaying = false;
                let lastTitle = '';
                let lastArtist = '';
                let lastImg = '';
                let lastDuration = 0;
                
                function syncPlayerState() {
                    const video = document.querySelector('video, audio');
                    if (!video) return;

                    const isPlaying = !video.paused;
                    const duration = Math.floor((video.duration || 0) * 1000);
                    const position = Math.floor((video.currentTime || 0) * 1000);

                    // Extract title and artist using selectors from Apple Podcast site structure
                    const titleEl = document.querySelector('.product-header__title, .audio-player__title, [class*="title"]');
                    const artistEl = document.querySelector('.product-header__subtitle, .audio-player__subtitle, [class*="subtitle"]');
                    const imgEl = document.querySelector('.product-header__image img, .audio-player__artwork img, [class*="artwork"] img');

                    const title = titleEl ? titleEl.innerText.trim() : 'Apple Podcasts';
                    const artist = artistEl ? artistEl.innerText.trim() : 'Playing';
                    const img = imgEl ? imgEl.src : '';

                    // Only bridge if playback details actually changed (avoid constant JNI overhead)
                    if (isPlaying !== lastPlaying || title !== lastTitle || artist !== lastArtist || img !== lastImg || Math.abs(duration - lastDuration) > 2000) {
                        lastPlaying = isPlaying;
                        lastTitle = title;
                        lastArtist = artist;
                        lastImg = img;
                        lastDuration = duration;
                        
                        AndroidMediaBridge.onPlaybackStateChanged(isPlaying, title, artist, img, duration, position);
                    }
                }
                setInterval(syncPlayerState, 1000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        // Unregister Network Callback safely
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Unregister Media broadcast receiver
        try {
            unregisterReceiver(mediaReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Clean up gesture handler
        gestureHandler.removeCallbacksAndMessages(null)

        // Webview clean teardown
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        webView.destroy()

        super.onDestroy()
    }

    // Audio background optimization: DO NOT call webView.onPause()
    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    // JS Media Bridge interface class
    inner class MediaBridge {
        @android.webkit.JavascriptInterface
        fun onPlaybackStateChanged(isPlaying: Boolean, title: String, artist: String, imageUrl: String, duration: Long, position: Long) {
            if (isDestroyed || isFinishing) return
            val intent = Intent(this@MainActivity, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_UPDATE_STATE
                putExtra(MediaPlaybackService.EXTRA_IS_PLAYING, isPlaying)
                putExtra(MediaPlaybackService.EXTRA_TITLE, title)
                putExtra(MediaPlaybackService.EXTRA_ARTIST, artist)
                putExtra(MediaPlaybackService.EXTRA_IMAGE_URL, imageUrl)
                putExtra(MediaPlaybackService.EXTRA_DURATION, duration)
                putExtra(MediaPlaybackService.EXTRA_POSITION, position)
            }
            if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
