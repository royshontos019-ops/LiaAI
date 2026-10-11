package com.Lia.assistant.ui.screens.forge

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.forge.ForgeController
import com.Lia.assistant.forge.ForgeHtml
import com.Lia.assistant.forge.ForgePhase
import com.Lia.assistant.forge.ForgeShare
import com.Lia.assistant.forge.StartResult
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The Website Forge screen (route "forge"). While a site is being built it shows the 3D reactor
 * (or a plain drawing of it) with a quiet HUD. When the page is ready it plays the reveal and then
 * shows the page full screen with a small toolbar.
 */
@Composable
fun ForgeScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = NovaTheme.colors
    val state by ForgeController.state.collectAsStateWithLifecycle()

    // While this screen is up, no "your website is ready" notification is needed.
    DisposableEffect(Unit) {
        ForgeController.screenVisible = true
        onDispose { ForgeController.screenVisible = false }
    }

    val lowRam = remember { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }
    var webGlFailed by remember { mutableStateOf(false) }
    val useFallback = lowRam || webGlFailed

    // The build whose reveal already played (or that was already finished when this screen opened).
    var revealedBuildId by rememberSaveable { mutableStateOf(if (state.phase == ForgePhase.DONE) state.buildId else -1L) }
    val building = state.phase == ForgePhase.BUILDING
    val showResult = state.phase == ForgePhase.DONE && revealedBuildId == state.buildId
    val showScene = state.phase != ForgePhase.IDLE && !showResult

    ImmersiveWhile(building)

    // The generated page: refreshed with the unfinished page now and then, then the final one.
    var siteHtml by remember { mutableStateOf("") }
    var sitePainted by remember(state.buildId) { mutableStateOf(false) }
    LaunchedEffect(state.buildId, state.phase) {
        when (state.phase) {
            ForgePhase.BUILDING -> {
                if (lowRam) return@LaunchedEffect
                var lastLength = 0
                while (isActive) {
                    delay(1_500)
                    val partial = ForgeController.partialHtml()
                    if (partial.length - lastLength >= 4_096 && ForgeHtml.looksLikeHtml(partial)) {
                        lastLength = partial.length
                        siteHtml = ForgeHtml.repairTruncated(partial)
                    }
                }
            }
            ForgePhase.DONE -> {
                siteHtml = ForgeController.partialHtml()
                delay(2_500) // wait for the paint, but never longer than this
                sitePainted = true
            }
            else -> Unit
        }
    }
    val reveal = state.phase == ForgePhase.DONE && !showResult && sitePainted

    // Toolbar: shows, then hides itself after 4 seconds.
    var toolbarVisible by remember { mutableStateOf(true) }
    var nudge by remember { mutableIntStateOf(0) }
    LaunchedEffect(toolbarVisible, nudge, showResult) {
        if (showResult && toolbarVisible) {
            delay(4_000)
            toolbarVisible = false
        }
    }
    LaunchedEffect(showResult) { if (showResult) toolbarVisible = true }

    var showList by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }
    var listVersion by remember { mutableIntStateOf(0) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun report(result: StartResult) { if (!result.started) toast(result.message) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val file = state.file
        if (uri != null && file != null) toast(if (ForgeShare.copyTo(context, file, uri)) "Saved" else "Could not save the file")
    }

    BackHandler { onClose() }

    Box(modifier.fillMaxSize().background(colors.background)) {
        if (siteHtml.isNotEmpty() && state.phase != ForgePhase.IDLE) {
            GeneratedSiteView(
                html = siteHtml,
                isFinal = state.phase == ForgePhase.DONE,
                onPainted = { sitePainted = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showScene) {
            if (useFallback) {
                ForgeFallbackScene(
                    state = state,
                    reveal = reveal,
                    onRevealFinished = { revealedBuildId = ForgeController.state.value.buildId },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ForgeWebGlScene(
                    state = state,
                    reveal = reveal,
                    onRevealFinished = { revealedBuildId = ForgeController.state.value.buildId },
                    onWebGLFailed = { webGlFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ForgeHud(
                state = state,
                onCancel = { ForgeController.cancel() },
                onRetry = { report(ForgeController.retry(context)) },
                onClose = onClose,
            )
        }

        if (state.phase == ForgePhase.IDLE) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No website yet", style = NovaTheme.type.voice, color = colors.textPrimary)
                Text("Ask Lia to build one, or open one you made before.", style = NovaTheme.type.body, color = colors.textSecondary)
                NovaButton(text = "My websites", onClick = { showList = true })
                NovaButton(text = "Close", onClick = onClose, style = NovaButtonStyle.TEXT)
            }
        }

        if (showResult) {
            val file = state.file
            ResultToolbar(
                visible = toolbarVisible,
                onToggle = { toolbarVisible = !toolbarVisible },
                onBack = onClose,
                onShare = { nudge++; if (file != null && !ForgeShare.share(context, file)) toast("Could not share") },
                onSave = { nudge++; saveLauncher.launch("website.html") },
                onBrowser = { nudge++; if (file == null || !ForgeShare.openInBrowser(context, file)) toast("No browser could open it") },
                onEdit = { nudge++; showEdit = true },
                onRebuild = { nudge++; report(ForgeController.retry(context)) },
                onMyWebsites = { nudge++; showList = true },
                onDelete = { nudge++; confirmDelete = file },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showList) {
        val files = remember(showList, listVersion) { ForgeController.store.list() }
        MyWebsitesDialog(
            files = files,
            onOpen = { file ->
                showList = false
                ForgeController.openSaved(file)
                revealedBuildId = ForgeController.state.value.buildId
                siteHtml = ForgeController.partialHtml()
            },
            onDelete = { confirmDelete = it },
            onDismiss = { showList = false },
        )
    }

    if (showEdit) {
        var change by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Change this website") },
            text = { NovaTextField(value = change, onValueChange = { change = it }, label = "What should change?") },
            confirmButton = {
                TextButton(onClick = {
                    showEdit = false
                    if (change.isNotBlank()) report(ForgeController.edit(context, change))
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this website?") },
            text = { Text("It will be removed from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    ForgeShare.delete(target)
                    if (state.file == target) {
                        ForgeController.reset()
                        siteHtml = ""
                        onClose()
                    } else {
                        listVersion++ // refresh the list
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

/** Hides the status and navigation bars while [active] (a build is running). */
@Composable
private fun ImmersiveWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        val window = view.context.findActivity()?.window
        val controller: WindowInsetsControllerCompat? = window?.let { WindowCompat.getInsetsController(it, view) }
        if (active) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
