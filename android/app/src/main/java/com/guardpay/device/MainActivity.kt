package com.guardpay.device

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * GuardPay Android shell: renders the same React checkout as the web build and
 * feeds it real call-state observations from the phone. Only the binary call
 * state is read — never audio, phone numbers, contacts, SMS, or location.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var deviceId: String
    private lateinit var reporter: CallStateReporter

    private var callback: TelephonyCallback? = null
    private var legacyListener: android.telephony.PhoneStateListener? = null
    private var activeCall = false
    private var callStateKnown = false
    private var lastObservedAt: String? = null
    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceId = DeviceIdStore(this).deviceId().toString()
        reporter = CallStateReporter(BuildConfig.GUARDPAY_API_BASE_URL, BuildConfig.GUARDPAY_DEVICE_TOKEN)
        telephonyManager = getSystemService(TelephonyManager::class.java)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            // Debug builds may talk to a local HTTP Sandbox backend. Release
            // builds are HTTPS-only and never silently downgrade to cleartext.
            settings.mixedContentMode =
                if (BuildConfig.DEBUG) {
                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                } else {
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            addJavascriptInterface(NativeBridge(), "GuardPayNative")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val uri = request.url
                    if (request.method == "GET" && isAppAssetHost(uri)) {
                        return serveAsset(uri)
                    }
                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    if (isAppAssetHost(uri)) return false
                    openExternal(uri)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    notifyWeb(activeCall, lastObservedAt)
                }
            }
            // Served from the APK's assets by shouldInterceptRequest(), but the
            // URL stays a normal HTTPS origin so storage, routing, and CORS work.
            loadUrl(APP_START_URL)
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startObserving()
        } else if (!permissionRequested) {
            permissionRequested = true
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startObserving()
        } else {
            Toast.makeText(
                this,
                "Call-state permission is needed to detect an active call.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopObserving()
    }

    override fun onDestroy() {
        stopObserving()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun startObserving() {
        if (callback != null || legacyListener != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb =
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        publish(CallStateMapper.isActiveCall(state))
                    }
                }
            callback = cb
            telephonyManager.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb)
        } else {
            @Suppress("DEPRECATION")
            val listener =
                object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        // phoneNumber is intentionally ignored — never stored or sent.
                        publish(CallStateMapper.isActiveCall(state))
                    }
                }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }

        val current =
            try {
                CallStateMapper.isActiveCall(telephonyManager.callState)
            } catch (_: SecurityException) {
                false
            }
        publish(current)
    }

    private fun stopObserving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            callback = null
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
            legacyListener = null
        }
    }

    private fun publish(nextActiveCall: Boolean) {
        activeCall = nextActiveCall
        callStateKnown = true
        val observedAt = Instant.now().toString()
        lastObservedAt = observedAt
        reporter.report(deviceId, nextActiveCall) { /* Risk uses the stored state. */ }
        notifyWeb(nextActiveCall, observedAt)
    }

    private fun notifyWeb(active: Boolean, observedAt: String?) {
        val at = JSONObject.quote(observedAt ?: "")
        val script =
            "window.__guardPayNativeCallState && window.__guardPayNativeCallState($active, $at);"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun isAppAssetHost(uri: Uri): Boolean =
        uri.host == "appassets.androidplatform.net"

    /** Serves the bundled Vite build; extension-less paths fall back to index.html. */
    private fun serveAsset(uri: Uri): WebResourceResponse {
        val path = uri.path.orEmpty().trimStart('/')
        if (path.contains("..")) return notFound()
        val lastSegment = path.substringAfterLast('/')
        val assetPath = if (path.isEmpty() || !lastSegment.contains('.')) "index.html" else path
        return try {
            WebResourceResponse(mimeType(assetPath), "utf-8", assets.open(assetPath))
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun mimeType(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found to open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Minimal, non-secret bridge used by the bundled React app. */
    inner class NativeBridge {
        @JavascriptInterface
        fun getDeviceId(): String = deviceId

        @JavascriptInterface
        fun getApiBaseUrl(): String = BuildConfig.GUARDPAY_API_BASE_URL

        @JavascriptInterface
        fun isCallStateKnown(): Boolean = callStateKnown

        @JavascriptInterface
        fun isCallActive(): Boolean = activeCall

        @JavascriptInterface
        fun getLastObservedAt(): String = lastObservedAt ?: ""

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread { openExternal(Uri.parse(url)) }
        }
    }

    companion object {
        private const val PERMISSION_CODE = 1001
        // Open directly on the protected checkout; the header still links everywhere else.
        private const val APP_START_URL = "https://appassets.androidplatform.net/payment"
    }
}
