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
import android.app.DownloadManager
import android.os.Environment
import android.widget.TextView
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
    private lateinit var instructionsOverlay: View
    private lateinit var btnUnderstand: Button
    
    // Download Banner views & state
    private lateinit var downloadBanner: View
    private lateinit var txtDownloadDetails: TextView
    private lateinit var btnCloseBanner: View
    private var lastSeekTime = 0L
    private var currentStreamUrl = ""
    private var dismissedStreamUrl = ""
    private var currentTrackTitle = "Episode"
    private var currentTrackArtist = "Apple Podcasts"
    
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
                            """
                            (function() {
                                function findAudioElement() {
                                    function findRecursive(root) {
                                        const video = root.querySelector('video, audio');
                                        if (video) return video;
                                        const elements = root.querySelectorAll('*');
                                        for (const el of elements) {
                                            if (el.shadowRoot) {
                                                const found = findRecursive(el.shadowRoot);
                                                if (found) return found;
                                            }
                                        }
                                        return null;
                                    }
                                    return findRecursive(document);
                                }
                                const v = findAudioElement();
                                if (v) v.play();
                            })();
                            """.trimIndent(), 
                            null
                        )
                    }
                }
                MediaPlaybackService.BROADCAST_PAUSE -> {
                    webView.post {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                function findAudioElement() {
                                    function findRecursive(root) {
                                        const video = root.querySelector('video, audio');
                                        if (video) return video;
                                        const elements = root.querySelectorAll('*');
                                        for (const el of elements) {
                                            if (el.shadowRoot) {
                                                const found = findRecursive(el.shadowRoot);
                                                if (found) return found;
                                            }
                                        }
                                        return null;
                                    }
                                    return findRecursive(document);
                                }
                                const v = findAudioElement();
                                if (v) v.pause();
                            })();
                            """.trimIndent(), 
                            null
                        )
                    }
                }
                MediaPlaybackService.BROADCAST_SEEK -> {
                    val seekPos = intent.getLongExtra(MediaPlaybackService.EXTRA_SEEK_POS, 0L)
                    lastSeekTime = System.currentTimeMillis() // Set seek cool-down timestamp
                    webView.post {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                function findAudioElement() {
                                    function findRecursive(root) {
                                        const video = root.querySelector('video, audio');
                                        if (video) return video;
                                        const elements = root.querySelectorAll('*');
                                        for (const el of elements) {
                                            if (el.shadowRoot) {
                                                const found = findRecursive(el.shadowRoot);
                                                if (found) return found;
                                            }
                                        }
                                        return null;
                                    }
                                    return findRecursive(document);
                                }
                                const v = findAudioElement();
                                if (v) v.currentTime = ${seekPos / 1000.0};
                            })();
                            """.trimIndent(), 
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
        instructionsOverlay = findViewById(R.id.instructions_overlay)
        btnUnderstand = findViewById(R.id.btn_understand)
        
        // Download banner layout initialization
        downloadBanner = findViewById(R.id.download_banner)
        txtDownloadDetails = findViewById(R.id.txt_download_details)
        btnCloseBanner = findViewById(R.id.btn_close_banner)

        setupWebView()
        setupGestureDetector()
        setupConnectivityMonitor()
        setupMediaReceiver()
        checkNotificationPermission()

        // First-run layout state management
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasSeenOverlay = prefs.getBoolean("has_seen_overlay_v1", false)
        if (hasSeenOverlay) {
            instructionsOverlay.visibility = View.GONE
        } else {
            instructionsOverlay.visibility = View.VISIBLE
        }

        btnUnderstand.setOnClickListener {
            instructionsOverlay.visibility = View.GONE
            prefs.edit().putBoolean("has_seen_overlay_v1", true).apply()
        }

        btnCloseBanner.setOnClickListener {
            downloadBanner.visibility = View.GONE
            dismissedStreamUrl = currentStreamUrl
        }

        downloadBanner.setOnClickListener {
            triggerPodcastDownload()
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
                        gestureHandler.postDelayed(longPressRunnable, 500) // 0.5 second hold
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
        
        // Dim the background to 75% opacity
        dialog.window?.setDimAmount(0.75f)
        
        // Remove background overlay so top rounded corners are visible
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        // Set version info dynamically on option menu footer
        val menuVersion = dialog.findViewById<TextView>(R.id.menu_version)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.1"
        }
        menuVersion?.text = "Version $versionName"

        val menuHome = dialog.findViewById<LinearLayout>(R.id.menu_home)
        val menuBack = dialog.findViewById<LinearLayout>(R.id.menu_back)
        val menuRefresh = dialog.findViewById<LinearLayout>(R.id.menu_refresh)
        val menuClearCache = dialog.findViewById<LinearLayout>(R.id.menu_clear_cache)
        val menuDownload = dialog.findViewById<LinearLayout>(R.id.menu_download)
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
            webView.reload() // Force page reload after cache clearing
            // Reset overlay state so it shows up again on next launch
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("has_seen_overlay_v1", false).apply()
            Toast.makeText(this, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
        }

        menuDownload?.setOnClickListener {
            dialog.dismiss()
            triggerPodcastDownload()
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
                let lastPosition = 0;

                // Recursive function to search for audio elements inside custom element Shadow roots
                function findAudioElement() {
                    function findRecursive(root) {
                        const video = root.querySelector('video, audio');
                        if (video) return video;
                        const elements = root.querySelectorAll('*');
                        for (const el of elements) {
                            if (el.shadowRoot) {
                                const found = findRecursive(el.shadowRoot);
                                if (found) return found;
                            }
                        }
                        return null;
                    }
                    return findRecursive(document);
                }

                // Helper to clean scraped text and ignore promotional copy
                function cleanText(text) {
                    if (!text) return '';
                    const lower = text.toLowerCase();
                    if (lower.includes("sign in") || lower.includes("sign up") || lower.includes("save your place") || lower.includes("follow shows") || lower.includes("downloading shows") || lower.includes("subscription benefits") || lower.includes("guest")) {
                        return '';
                    }
                    return text.trim();
                }
                
                function syncPlayerState() {
                    const video = findAudioElement();
                    if (!video) return;

                    const isPlaying = !video.paused;
                    const duration = Math.floor((video.duration || 0) * 1000);
                    const position = Math.floor((video.currentTime || 0) * 1000);
                    const streamUrl = video.src || '';

                    // Prioritize extracting metadata from Apple's bottom mini player LCD marquees
                    let title = '';
                    let artist = '';
                    const playerBar = document.querySelector('.player-bar, .chrome-player, .player-lcd');
                    if (playerBar) {
                        const primaryMarquee = playerBar.querySelector('.marquee--primary, .player-lcd__title, .player-lcd__metadata [class*="primary"]');
                        const secondaryMarquee = playerBar.querySelector('.marquee--secondary, .player-lcd__subtitle, .player-lcd__metadata [class*="secondary"]');
                        
                        if (primaryMarquee) title = cleanText(primaryMarquee.innerText);
                        if (secondaryMarquee) artist = cleanText(secondaryMarquee.innerText);
                    }

                    // Fallback to page headers if mini-player fields are empty (applying filters)
                    if (!title) {
                        const titleEl = document.querySelector('.product-header__title, .audio-player__title, [class*="title"], .episode-metadata__primary .marquee-line__fragment');
                        title = cleanText(titleEl ? titleEl.innerText : '');
                    }
                    if (!title) {
                        title = 'Apple Podcasts';
                    }

                    if (!artist) {
                        const showLink = document.querySelector('.episode-header__show-link, .podcast-header__show-link, .product-header__title, [class*="show-link"]');
                        artist = cleanText(showLink ? showLink.innerText : '');
                    }
                    if (!artist) {
                        const artistEl = document.querySelector('.product-header__subtitle, .audio-player__subtitle, [class*="subtitle"], .episode-metadata__secondary button.lcd-meta-line__fragment');
                        artist = cleanText(artistEl ? artistEl.innerText : '');
                    }
                    if (!artist) {
                        artist = 'Playing';
                    }
                    
                    let img = '';
                    const artworkContainer = document.querySelector('.artwork-container');
                    if (artworkContainer) {
                        const sourceEl = artworkContainer.querySelector('source[type="image/jpeg"]') 
                                       || artworkContainer.querySelector('source[type="image/webp"]');
                        if (sourceEl) {
                            const srcset = sourceEl.getAttribute('srcset');
                            if (srcset) {
                                const sources = srcset.split(',').map(s => s.trim());
                                if (sources.length > 0) {
                                    const largestSource = sources[sources.length - 1];
                                    img = largestSource.split(' ')[0];
                                }
                            }
                        }
                    }
                    if (!img) {
                        const imgEl = document.querySelector('.product-header__image img, .audio-player__artwork img, [class*="artwork"] img');
                        if (imgEl && imgEl.src && !imgEl.src.includes('1x1.gif')) {
                            img = imgEl.src;
                        }
                    }

                    // Only bridge if playback details actually changed (avoid constant JNI overhead)
                    if (isPlaying !== lastPlaying || title !== lastTitle || artist !== lastArtist || img !== lastImg || Math.abs(duration - lastDuration) > 2000 || Math.abs(position - lastPosition) > 2000) {
                        lastPlaying = isPlaying;
                        lastTitle = title;
                        lastArtist = artist;
                        lastImg = img;
                        lastDuration = duration;
                        lastPosition = position;
                        
                        AndroidMediaBridge.onPlaybackStateChanged(isPlaying, title, artist, img, duration, position, streamUrl);
                    }
                }

                // Inject Download option inside Apple's native contextual menu DOM
                function injectDownloadMenuItem() {
                    const menuList = document.querySelector('.contextual-menu__list');
                    if (!menuList) return;
                    if (menuList.querySelector('.custom-download-item')) return;
                    
                    const li = document.createElement('li');
                    li.className = 'contextual-menu-item custom-download-item';
                    
                    const button = document.createElement('button');
                    button.title = 'Download Episode';
                    button.type = 'button';
                    button.style.width = '100%';
                    button.style.textAlign = 'left';
                    button.style.background = 'transparent';
                    button.style.border = 'none';
                    button.style.padding = '0';
                    
                    const wrapper = document.createElement('span');
                    wrapper.className = 'contextual-menu-item__option-wrapper';
                    
                    const text = document.createElement('span');
                    text.className = 'contextual-menu-item__option-text';
                    text.innerText = 'Download Episode';
                    
                    const iconContainer = document.createElement('span');
                    iconContainer.className = 'contextual-menu-item__icon-container';
                    
                    iconContainer.innerHTML = `
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 16px; height: 16px; margin-top: 4px;">
                            <path d="M21 15V16.2C21 17.8802 21 18.7202 20.673 19.362C20.3854 19.9265 19.9265 20.3854 19.362 20.673C18.7202 21 17.8802 21 16.2 21H7.8C6.11984 21 5.27976 21 4.63803 20.673C4.07354 20.3854 3.6146 19.9265 3.32698 19.362C3 18.7202 3 17.8802 3 16.2V15M17 10L12 15M12 15L7 10M12 15V3"></path>
                        </svg>
                    `;
                    
                    wrapper.appendChild(text);
                    wrapper.appendChild(iconContainer);
                    button.appendChild(wrapper);
                    li.appendChild(button);
                    
                    button.onclick = function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        const trigger = document.querySelector('.contextual-menu__trigger');
                        if (trigger) trigger.click();
                        AndroidMediaBridge.triggerDownload();
                    };
                    
                    menuList.appendChild(li);
                }

                setInterval(syncPlayerState, 1000);
                setInterval(injectDownloadMenuItem, 300);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        // Stop background media service to clear lockscreen notification and active timers
        try {
            val serviceIntent = Intent(this, MediaPlaybackService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        fun onPlaybackStateChanged(isPlaying: Boolean, title: String, artist: String, imageUrl: String, duration: Long, position: Long, streamUrl: String) {
            if (isDestroyed || isFinishing) return
            
            // Ignore incoming position reports within 2000ms of a lockscreen seek to prevent snapping
            if (System.currentTimeMillis() - lastSeekTime < 2000) {
                return
            }

            runOnUiThread {
                handleStreamUrlUpdate(streamUrl, title, artist)
            }

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

        @android.webkit.JavascriptInterface
        fun triggerDownload() {
            runOnUiThread {
                triggerPodcastDownload()
            }
        }
    }

    private fun handleStreamUrlUpdate(streamUrl: String, title: String, artist: String) {
        if (isDestroyed || isFinishing) return
        currentTrackTitle = title
        currentTrackArtist = artist
        
        // Show banner only if the stream URL is valid
        if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
            currentStreamUrl = streamUrl
            
            // Reset visibility if this is a new track
            if (currentStreamUrl != dismissedStreamUrl) {
                // Show ONLY the podcast show name (artist) to avoid episode title clutter
                txtDownloadDetails.text = artist
                downloadBanner.visibility = View.VISIBLE
            }
        } else {
            // Do not hide the banner if they hide or pause the podcast player, only close manually
        }
    }

    private fun triggerPodcastDownload() {
        val url = currentStreamUrl
        if (url.isEmpty() || !url.startsWith("http")) {
            Toast.makeText(this, "No valid download link found.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Format name exactly as: [Podcast Name] - [Episode Title].mp3
            val cleanPodcast = currentTrackArtist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val cleanEpisode = currentTrackTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val filename = "$cleanPodcast - $cleanEpisode.mp3"

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(currentTrackTitle)
                setDescription("Downloading $currentTrackArtist...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download started: $filename", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
