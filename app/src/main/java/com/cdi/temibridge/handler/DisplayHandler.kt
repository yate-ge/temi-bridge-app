package com.cdi.temibridge.handler

import android.app.Activity
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import com.google.gson.JsonElement
import com.cdi.temibridge.server.InvalidParamsException

class DisplayHandler(
    private val activity: Activity,
    private val webView: WebView,
    private val statusPanel: View
) {
    companion object {
        private const val TAG = "DisplayHandler"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        activity.runOnUiThread {
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = WebChromeClient()
            val settings = webView.settings
            // Core
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Zoom
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            // Media
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            // Mixed content (allow http resources in https pages)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Cache & database
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // Layout
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            // Allow universal access from file URLs (for local HTML loading external resources)
            settings.allowUniversalAccessFromFileURLs = true
        }
    }

    init {
        setupWebView()
    }

    fun register(registry: HandlerRegistry) {
        registry.register("display.loadUrl", ::loadUrl)
        registry.register("display.loadHtml", ::loadHtml)
        registry.register("display.clear", ::clear)
        registry.register("display.getCurrentUrl", ::getCurrentUrl)
        registry.register("display.executeJavaScript", ::executeJavaScript)
    }

    private fun loadUrl(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val url = obj.get("url")?.asString ?: throw InvalidParamsException("url required")
        activity.runOnUiThread {
            statusPanel.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(url)
        }
        Log.i(TAG, "Loading URL: $url")
        return mapOf("status" to "accepted", "url" to url)
    }

    private fun loadHtml(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val html = obj.get("html")?.asString ?: throw InvalidParamsException("html required")
        val baseUrl = obj.get("baseUrl")?.asString ?: "about:blank"
        activity.runOnUiThread {
            statusPanel.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
        Log.i(TAG, "Loading HTML content (${html.length} chars)")
        return mapOf("status" to "accepted", "contentLength" to html.length)
    }

    private fun clear(params: JsonElement?, id: Any?): Any? {
        activity.runOnUiThread {
            webView.loadUrl("about:blank")
            webView.visibility = View.GONE
            statusPanel.visibility = View.VISIBLE
        }
        Log.i(TAG, "Display cleared")
        return mapOf("status" to "cleared")
    }

    private fun getCurrentUrl(params: JsonElement?, id: Any?): Any? {
        var url: String? = null
        val visible = webView.visibility == View.VISIBLE
        activity.runOnUiThread {
            url = webView.url
        }
        // Give UI thread a moment
        Thread.sleep(50)
        return mapOf("url" to (url ?: ""), "visible" to visible)
    }

    private fun executeJavaScript(params: JsonElement?, id: Any?): Any? {
        val obj = params?.asJsonObject ?: throw InvalidParamsException("params required")
        val script = obj.get("script")?.asString ?: throw InvalidParamsException("script required")
        activity.runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
        return mapOf("status" to "executed")
    }
}
