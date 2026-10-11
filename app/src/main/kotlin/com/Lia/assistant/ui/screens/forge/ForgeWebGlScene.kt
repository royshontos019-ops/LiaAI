package com.Lia.assistant.ui.screens.forge

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.Lia.assistant.forge.ForgeSceneProtocol
import com.Lia.assistant.forge.ForgeThemeJson
import com.Lia.assistant.forge.ForgeUiState
import com.Lia.assistant.ui.theme.NovaTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val ASSET_HOST = "appassets.androidplatform.net"
private const val SCENE_URL = "https://$ASSET_HOST/assets/forge/forge.html"

/** The only three things the page may tell us. Nothing else is exposed to it. */
private class SceneBridge(
    private val main: Handler,
    private val ready: () -> Unit,
    private val finished: () -> Unit,
    private val failed: () -> Unit,
) {
    @JavascriptInterface fun onReady() { main.post(ready) }

    @JavascriptInterface fun onRevealFinished() { main.post(finished) }

    @JavascriptInterface fun onWebGLFailed() { main.post(failed) }
}

/**
 * The 3D Forge Reactor. It sits full-bleed with the theme background behind it. Do not put this in
 * AnimatedVisibility or graphicsLayer: a WebView inside those renders black.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ForgeWebGlScene(
    state: ForgeUiState,
    reveal: Boolean,
    onRevealFinished: () -> Unit,
    onWebGLFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val latest by rememberUpdatedState(state)
    val finished by rememberUpdatedState(onRevealFinished)
    val failed by rememberUpdatedState(onWebGLFailed)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val loader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(colors.background.toArgb())
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    mediaPlaybackRequiresUserGesture = true
                    setSupportZoom(false)
                }
                webViewClient = object : WebViewClient() {
                    // Nothing else may ever load in here.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        return if (url.host == ASSET_HOST) {
                            loader.shouldInterceptRequest(url)
                        } else {
                            WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        failed()
                        return true
                    }
                }
                addJavascriptInterface(
                    SceneBridge(main, { ready = true }, { finished() }, { failed() }),
                    "ForgeBridge",
                )
                webView = this
                loadUrl(SCENE_URL)
            }
        },
        onRelease = { wv ->
            wv.evaluateJavascript("try{Forge.dispose()}catch(e){}", null)
            wv.stopLoading()
            wv.removeJavascriptInterface("ForgeBridge")
            wv.destroy()
        },
    )

    // Start the scene, then push what changed every 60 ms.
    LaunchedEffect(ready, webView) {
        val wv = webView ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val init = "Forge.init(${ForgeThemeJson.theme(colors)}, ${ForgeThemeJson.options(lowRam = false, reducedMotion = reduced)})"
        wv.evaluateJavascript(init, null)
        var last: ForgeUiState? = null
        while (isActive) {
            val now = latest
            ForgeSceneProtocol.diff(last, now)?.let { wv.evaluateJavascript("Forge.update(${ForgeSceneProtocol.toJs(it)})", null) }
            last = now
            delay(60)
        }
    }

    LaunchedEffect(reveal, ready, webView) {
        if (reveal && ready) webView?.evaluateJavascript("Forge.reveal()", null)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wv.evaluateJavascript("try{Forge.setPaused(true)}catch(e){}", null)
                    wv.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    wv.onResume()
                    wv.evaluateJavascript("try{Forge.setPaused(false)}catch(e){}", null)
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
