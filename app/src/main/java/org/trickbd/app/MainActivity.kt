package org.trickbd.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.activity.OnBackPressedCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Minimal native WebView wrapper. Site and update repository are configured in BuildConfig/companion constants. */
class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var errorView: View? = null
    private var pageLoaded = false
    private var lastUpdateCheckAt = 0L
    private val executor = Executors.newSingleThreadExecutor()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val START_URL = "https://www.trickbd.org/"
        private const val SITE_HOST = "www.trickbd.org"
        private const val ROOT_HOST = "trickbd.org"
        // Configure this after confirming that the public repository exists and publishes APK release assets.
        private const val GITHUB_OWNER = "rironib"
        private const val GITHUB_REPO = "trickbd"
        private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val PREFS = "trickbd_app_prefs"
        private const val PREF_LAST_PROMPTED_TAG = "last_prompted_release_tag"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TrickBD"
        buildLayout()
        setupWebView()
        setupConnectivityListener()
        registerBackHandler()
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadHome()
        }
        checkForUpdates(force = true)
    }

    private fun buildLayout() {
        root = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        webView = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            visibility = View.GONE
            progressDrawable.setTint(0xFF1769E0.toInt())
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true // Required for the modern TrickBD web application.
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return true
                if (scheme == "https" && isAllowedHost(uri.host)) return false
                if (scheme == "http") {
                    // Never allow cleartext web content in the WebView.
                    showError(getString(R.string.secure_conn_not_found_title), getString(R.string.secure_conn_not_found_desc))
                    return true
                }
                if (scheme in setOf("mailto", "tel", "sms")) {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                    catch (_: Exception) { Toast.makeText(this@MainActivity, R.string.unable_open_link, Toast.LENGTH_SHORT).show(); true }
                }
                if (scheme == "https") {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                    catch (_: Exception) { true }
                }
                // Block intent:, file:, javascript:, data: and unknown schemes.
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageLoaded = false
                hideError()
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                progress.visibility = View.GONE
                hideError()
                CookieManager.getInstance().flush()
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    showErrorForNetwork(description)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error.description?.toString()
                    } else {
                        null
                    }
                    showErrorForNetwork(desc)
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                    showError(getString(R.string.server_unavailable_title), getString(R.string.server_unavailable_desc))
                }
            }

            override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel() // Never bypass certificate errors.
                showError(getString(R.string.secure_conn_failed_title), getString(R.string.secure_conn_failed_desc))
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                root.removeView(webView)
                webView.destroy()
                webView = WebView(this@MainActivity)
                root.addView(webView, 0, FrameLayout.LayoutParams(-1, -1))
                setupWebView()
                showError(getString(R.string.reload_required_title), getString(R.string.reload_required_desc))
                return true
            }
        }
    }

    private fun isAllowedHost(host: String?): Boolean {
        val normalized = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return normalized == ROOT_HOST || normalized == SITE_HOST
    }

    private fun loadHome() {
        hideError()
        if (!hasInternet()) {
            showError(getString(R.string.no_internet_title), getString(R.string.no_internet_desc))
            return
        }
        webView.loadUrl(START_URL)
    }

    private fun showErrorForNetwork(detail: String?) {
        val offline = !hasInternet()
        showError(
            if (offline) getString(R.string.no_internet_title) else getString(R.string.page_load_failed_title),
            if (offline) getString(R.string.connect_internet_desc) else getString(R.string.page_load_failed_desc)
        )
    }

    private fun showError(title: String, message: String) {
        progress.visibility = View.GONE
        errorView?.let { root.removeView(it) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 20f; gravity = Gravity.CENTER
                setTextColor(0xFF172033.toInt())
            }, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(this@MainActivity).apply {
                text = message; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(0xFF667085.toInt()); setPadding(0, dp(12), 0, dp(20))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(Button(this@MainActivity).apply {
                setText(R.string.try_again)
                setOnClickListener { loadHome() }
            })
        }
        errorView = panel
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
    }

    private fun hideError() {
        errorView?.let { root.removeView(it) }
        errorView = null
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (errorView != null) {
                    hideError()
                    if (webView.canGoBack()) webView.goBack() else loadHome()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(R.string.exit_confirm_title)
                        .setNegativeButton(R.string.stay, null)
                        .setPositiveButton(R.string.exit) { _, _ -> finish() }
                        .show()
                }
            }
        })
    }

    private fun setupConnectivityListener() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (errorView != null && !pageLoaded) loadHome()
                }
            }
        }
        try {
            connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
        } catch (_: Exception) { /* App still works without live connectivity callbacks. */ }
    }

    @Suppress("DEPRECATION")
    private fun hasInternet(): Boolean {
        val cm = connectivityManager ?: (getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            val netInfo = cm.activeNetworkInfo ?: return false
            return netInfo.isConnected
        }
    }

    private fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckAt = now
        if (!hasInternet()) return
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "TrickBD-Android-Updater")
                }
                if (connection.responseCode !in 200..299) return@execute
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) return@execute
                val tag = json.optString("tag_name", "").trim()
                val latest = normalizeVersion(tag)
                val current = normalizeVersion(BuildConfig.VERSION_NAME)
                if (tag.isBlank() || latest == null || current == null || compareVersions(latest, current) <= 0) return@execute
                val assets = json.optJSONArray("assets") ?: return@execute
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/")) {
                        apkUrl = url; break
                    }
                }
                val releaseUrl = json.optString("html_url", "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases")
                val releaseName = json.optString("name", "TrickBD $tag")
                val notes = json.optString("body", "A new version is available.")
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (prefs.getString(PREF_LAST_PROMPTED_TAG, "") == tag) return@execute
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val builder = AlertDialog.Builder(this)
                        .setTitle("New Update Available")
                        .setMessage("$releaseName\n\nCurrent: ${BuildConfig.VERSION_NAME}\nNew: $tag\n\n${notes.take(1800)}")
                        .setNegativeButton("Later") { _, _ -> prefs.edit().putString(PREF_LAST_PROMPTED_TAG, tag).apply() }
                        .setNeutralButton("Release notes") { _, _ -> openHttps(releaseUrl) }
                    if (apkUrl != null) builder.setPositiveButton("Download APK") { _, _ -> openHttps(apkUrl) }
                    else builder.setPositiveButton("Open Release") { _, _ -> openHttps(releaseUrl) }
                    builder.setOnCancelListener { prefs.edit().putString(PREF_LAST_PROMPTED_TAG, tag).apply() }
                    builder.show()
                }
            } catch (_: Exception) {
                // Silent failure: update checks must never interrupt normal app use.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun openHttps(value: String?) {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return
        if (uri.scheme != "https") return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun normalizeVersion(raw: String): List<Int>? {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = cleaned.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }.toMutableList()
        while (numbers.size < 3) numbers.add(0)
        return numbers
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates()
    }

    override fun onDestroy() {
        networkCallback?.let { callback -> runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
        executor.shutdownNow()
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
