package com.latent.camera.ui

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.latent.camera.camera.CameraController
import com.latent.camera.camera.CameraState
import com.latent.camera.camera.FocusPoint
import com.latent.camera.look.RecipeController
import com.latent.camera.settings.AidsSettings
import com.latent.camera.settings.CaptureSettings
import com.latent.camera.ui.theme.LatentInk
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Vertical rhythm: a mode strip in the surround, the square high, the capture deck
 * low in the thumb zone. Chrome that describes the picture sits on the frame; chrome
 * that changes a mode stays off it.
 */
private val ViewportTopGap = 4.dp
internal val RecipeStripHeight = 48.dp
private val DeckBottomGap = 20.dp

/** Long enough to register as a shutter, short enough not to cost you the next frame. */
private const val BlackoutMillis = 190

/**
 * How far a horizontal swipe travels per recipe. Deliberately longer than a flick:
 * the strip below is for browsing, and this gesture is for the one you already know
 * is next.
 */
private val RecipeSwipeStep = 88.dp

@Composable
fun CameraScreen(
    controller: CameraController,
    recipes: RecipeController,
    aidsSettings: AidsSettings,
    captureSettings: CaptureSettings,
    latestCaptureUri: Uri?,
    onOpenGallery: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val allRecipes by recipes.recipes.collectAsStateWithLifecycle()
    val activeRecipe by recipes.active.collectAsStateWithLifecycle()
    val aids by aidsSettings.state.collectAsStateWithLifecycle()
    val capture by captureSettings.state.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var editingRecipe by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    var aidsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var manualParam by remember { mutableStateOf(ManualParam.Iso) }

    LaunchedEffect(activeRecipe) { controller.setRecipe(activeRecipe) }
    LaunchedEffect(aids) { controller.setAids(aids) }
    LaunchedEffect(capture.saveMode) { controller.setSaveMode(capture.saveMode) }

    // Pick a sensible first dial when opening manual.
    LaunchedEffect(manualOpen, state.capabilities) {
        if (!manualOpen) return@LaunchedEffect
        val caps = state.capabilities ?: return@LaunchedEffect
        manualParam = when {
            state.manual.focusDioptres != null && caps.supportsManualFocus -> ManualParam.Focus
            state.manual.iso != null && caps.supportsManualIso -> ManualParam.Iso
            state.manual.exposureTimeNs != null && caps.supportsManualShutter -> ManualParam.Shutter
            caps.supportsManualIso -> ManualParam.Iso
            caps.supportsManualShutter -> ManualParam.Shutter
            caps.supportsManualFocus -> ManualParam.Focus
            else -> ManualParam.Iso
        }
    }

    val blackout = remember { Animatable(0f) }

    val onShutter: () -> Unit = onShutter@{
        if (state.countdown > 0) {
            controller.shutter()
            return@onShutter
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (state.timerSeconds <= 0) {
            scope.launch {
                blackout.snapTo(1f)
                blackout.animateTo(0f, tween(BlackoutMillis, easing = LinearEasing))
            }
        }
        controller.shutter()
    }

    var previousFrames by remember { mutableStateOf(0) }
    LaunchedEffect(state.framesThisSession) {
        if (state.framesThisSession > previousFrames && state.timerSeconds > 0) {
            blackout.snapTo(1f)
            blackout.animateTo(0f, tween(BlackoutMillis, easing = LinearEasing))
        }
        previousFrames = state.framesThisSession
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        ViewfinderTopBar(
            state = state,
            aidsOpen = aidsOpen,
            onTimerCycle = {
                controller.setTimerSeconds(cycleTimerSeconds(state.timerSeconds))
            },
            onSilentToggle = { controller.setSilentShutter(!state.silentShutter) },
            onToggleAids = { aidsOpen = !aidsOpen },
            onSettings = { settingsOpen = true },
        )

        Spacer(Modifier.height(ViewportTopGap))

        SquareViewport(
            controller = controller,
            state = state,
            recipes = recipes,
            recipeCount = allRecipes.size,
            activeRecipeName = activeRecipe.name,
            blackout = { blackout.value },
            showFocusRail = manualOpen || state.manual.focusDioptres != null,
            onDismissError = controller::clearError,
            onClearFocusLock = controller::clearFocusLock,
            onFocusChange = controller::setManualFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.framesThisSession > 0 || state.inFlight > 0) {
                Text(
                    text = buildString {
                        append(state.framesThisSession)
                        if (state.inFlight > 0) append("  ·  saving")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LatentInk.Soft,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // The aids row is a panel now, not permanent chrome. Five text toggles
            // sitting under the frame on every shot was the loudest thing on screen
            // and the least often changed.
            AnimatedVisibility(visible = aidsOpen) {
                AidsBar(aids = aids, settings = aidsSettings)
            }

            if (manualOpen) {
                ManualDeck(
                    state = state,
                    param = manualParam,
                    onParamChange = { manualParam = it },
                    onIso = controller::setManualIso,
                    onShutter = controller::setManualShutter,
                    onFocus = controller::setManualFocus,
                    onClear = {
                        controller.clearManual()
                        manualOpen = false
                    },
                )
            } else {
                RecipeStrip(
                    recipes = allRecipes,
                    activeIndex = allRecipes.indexOfFirst { it.id == activeRecipe.id }
                        .coerceAtLeast(0),
                    onSelect = recipes::selectAt,
                    onEditActive = { editingRecipe = true },
                )
            }

            Spacer(Modifier.height(12.dp))

            CaptureBar(
                state = state,
                shutterEnabled = state.isReady,
                manualOpen = manualOpen,
                lastCaptureUri = latestCaptureUri,
                onShutter = onShutter,
                onOpenGallery = onOpenGallery,
                onOpenManual = { manualOpen = !manualOpen },
            )

            Spacer(Modifier.height(DeckBottomGap))
        }
    }

    if (editingRecipe) {
        RecipeSheet(
            recipe = activeRecipe,
            onChange = recipes::edit,
            onRename = recipes::rename,
            onDuplicate = recipes::duplicate,
            onDelete = { recipes.delete(onRefused = { editingRecipe = false }) },
            onReset = recipes::reset,
            onDismiss = {
                recipes.commit()
                editingRecipe = false
            },
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            saveMode = capture.saveMode,
            exportBorder = capture.exportBorder,
            dngSupported = state.capabilities?.supportsRawJpeg == true,
            hardwareLevel = state.capabilities?.hardwareLevelName,
            onSaveMode = captureSettings::setSaveMode,
            onExportBorder = captureSettings::setExportBorder,
            onDismiss = { settingsOpen = false },
        )
    }
}

/**
 * The frame is 1:1. TextureView (COMPATIBLE) so the square clip is reliable —
 * SurfaceView can bleed the cropped 4:3 edges on some devices.
 */
@Composable
private fun SquareViewport(
    controller: CameraController,
    state: CameraState,
    recipes: RecipeController,
    recipeCount: Int,
    activeRecipeName: String,
    blackout: () -> Float,
    showFocusRail: Boolean,
    onDismissError: () -> Unit,
    onClearFocusLock: () -> Unit,
    onFocusChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        controller.bind(
            lifecycleOwner,
            previewView.surfaceProvider,
        ) { previewView.meteringPointFactory }
    }

    // Leaving the viewfinder for the contact sheet takes the camera down with it.
    DisposableEffect(controller) {
        onDispose { controller.unbind() }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(Color.Black),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val recipeStepPx = with(density) { RecipeSwipeStep.toPx() }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture bookkeeping. The detector reports pixels; turning those into whole
        // EV steps and whole recipes belongs here, next to the ranges they clamp to.
        var dragging by remember { mutableStateOf<ViewfinderDrag?>(null) }
        var travel by remember { mutableFloatStateOf(0f) }

        /**
         * Where the recipe strip was when this swipe began, and how far it has been
         * moved from there. The swipe is resolved against its own start rather than
         * against the live selection because the selection arrives back through a
         * StateFlow — reading it mid-gesture would sample a value one or two frames
         * stale and drop steps out of a fast swipe.
         */
        var recipeAnchor by remember { mutableStateOf(0) }
        var recipeSteps by remember { mutableStateOf(0) }

        val evRange = state.capabilities?.evRange
        val evAvailable = state.capabilities?.supportsEv == true && !state.manual.isExposureManual

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller, recipes, widthPx, heightPx, recipeCount, evAvailable) {
                    detectViewfinderGestures(
                        onTap = { offset ->
                            controller.focusAt(offset.x, offset.y, widthPx, heightPx)
                        },
                        onHold = { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.lockFocusAt(offset.x, offset.y, widthPx, heightPx)
                        },
                        onZoom = { controller.zoomBy(it) },
                        onDragStart = { axis ->
                            travel = 0f
                            recipeAnchor = recipes.indexOfActive()
                            recipeSteps = 0
                            // Exposure has nowhere to go under manual exposure, so the
                            // drag simply does not start rather than silently doing
                            // nothing for the length of the gesture.
                            dragging = when (axis) {
                                ViewfinderDrag.Exposure -> if (evAvailable) axis else null
                                ViewfinderDrag.Recipe -> if (recipeCount > 1) axis else null
                            }
                        },
                        onDrag = { axis, along ->
                            if (dragging != axis) return@detectViewfinderGestures
                            travel += along
                            when (axis) {
                                ViewfinderDrag.Exposure -> {
                                    val range = evRange ?: return@detectViewfinderGestures
                                    // The whole range spans a little over the height
                                    // of the frame, so the full dial is reachable in
                                    // one drag without it being twitchy.
                                    val span = (range.last - range.first).coerceAtLeast(1)
                                    val stepPx = heightPx / (span * 1.4f)
                                    if (stepPx <= 0f) return@detectViewfinderGestures
                                    while (abs(travel) >= stepPx) {
                                        val step = sign(travel).toInt()
                                        travel -= step * stepPx
                                        if (controller.nudgeExposure(step)) {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.SegmentTick,
                                            )
                                        }
                                    }
                                }

                                ViewfinderDrag.Recipe -> {
                                    if (recipeStepPx <= 0f) return@detectViewfinderGestures
                                    val wanted = (travel / recipeStepPx).toInt()
                                    if (wanted == recipeSteps) return@detectViewfinderGestures
                                    val next = (recipeAnchor + wanted)
                                        .coerceIn(0, recipeCount - 1)
                                    recipeSteps = wanted
                                    if (next != recipes.indexOfActive()) {
                                        recipes.selectAt(next)
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.SegmentTick,
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            dragging = null
                            travel = 0f
                        },
                    )
                },
        )

        // Aids under chrome: grids and level sit on the frame; peaking/zebra are GL.
        GridOverlay(mode = state.aids.grid)
        if (state.aids.level) {
            LevelOverlay(rollDegrees = state.rollDegrees)
        }
        if (state.aids.histogram) {
            HistogramOverlay(
                bins = state.histogramBins,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 56.dp),
            )
        }

        MeterOverlay(
            state = state,
            onDismissError = onDismissError,
            onClearFocusLock = onClearFocusLock,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        val capabilities = state.capabilities

        if (capabilities != null && evAvailable) {
            ExposureLadder(
                exposureIndex = state.exposureIndex,
                range = capabilities.evRange,
                stepEv = capabilities.evStepEv,
                visible = dragging == ViewfinderDrag.Exposure,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        RecipeFlash(
            name = activeRecipeName,
            visible = dragging == ViewfinderDrag.Recipe,
            modifier = Modifier.align(Alignment.Center),
        )

        if (showFocusRail && capabilities?.supportsManualFocus == true) {
            val current = state.manual.focusDioptres
                ?: state.metered.focusDistanceDioptres
                ?: 0f
            FocusRail(
                dioptres = current.coerceIn(0f, capabilities.minFocusDistanceDioptres),
                maxDioptres = capabilities.minFocusDistanceDioptres,
                onChange = onFocusChange,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        state.focus?.let { focus ->
            FocusReticleOverlay(focus = focus, viewportWidth = maxWidth)
        }

        if (state.countdown > 0) {
            Text(
                text = state.countdown.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                color = LatentInk.Full,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.50f),
                    ),
                )
                .padding(bottom = 14.dp, top = 28.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            LensPicker(
                lenses = state.lenses,
                active = state.activeLens,
                zoomRatio = state.zoomRatio,
                onSelect = controller::selectLens,
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val alpha = blackout()
            if (alpha > 0f) drawRect(color = Color.Black, alpha = alpha)
        }
    }
}

@Composable
private fun FocusReticleOverlay(focus: FocusPoint, viewportWidth: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    val sizePx = with(density) { 64.dp.toPx() }
    val widthPx = with(density) { viewportWidth.toPx() }
    Box(modifier = Modifier.fillMaxSize()) {
        FocusReticle(
            locked = focus.locked,
            modifier = Modifier.offset {
                IntOffset(
                    x = (focus.x * widthPx - sizePx / 2f).roundToInt(),
                    y = (focus.y * widthPx - sizePx / 2f).roundToInt(),
                )
            },
        )
    }
}
