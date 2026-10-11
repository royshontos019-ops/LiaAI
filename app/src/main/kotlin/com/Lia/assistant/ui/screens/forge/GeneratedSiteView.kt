package com.Lia.assistant.ui.screens.forge

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val BASE_URL = "https://localhost/"

/**
 * Shows the website Lia built. It is sandboxed: JavaScript for the page's own scripts, but no
 * bridge to the app, no file or content access, no mixed content, no location. Links to other
 * places open in the system browser. Do not wrap it in AnimatedVisibility or graphicsLayer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeneratedSiteView(
    html: String,
    isFinal: Boolean,
    onPainted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val painted by rememberUpdatedState(onPainted)
    val final by rememberUpdatedState(isFinal)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith(BASE_URL) || url == "about:blank") return false // the page's own anchors
                        openOutside(request.url)
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (!final) return
                        // Tell the screen once the content has really been drawn.
                        view.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                painted()
                            }
                        })
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true

                    private fun openOutside(uri: Uri) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            // No app can open it: ignore.
                        }
                    }
                }
            }
        },
        update = { wv ->
            val key = html.length * 31 + html.hashCode()
            if (wv.tag != key) {
                wv.tag = key
                wv.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { wv ->
            wv.stopLoading()
            wv.destroy()
        },
    )
}
