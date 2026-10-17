package dev.champions.dex.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.champions.dex.ENTER_MS
import dev.champions.dex.EXIT_MS
import dev.champions.dex.EnterEasing
import dev.champions.dex.ExitEasing
import dev.champions.dex.LocalReduceMotion
import dev.champions.dex.pal
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Open/closed state of a [DexModalDrawer]. [isOpen] is where the drawer is heading, so it turns true the moment
 * opening starts: the scrim, back handling and "close" all work while the drawer is still sliding in.
 */
@Stable
class DexDrawerState(
    open: Boolean,
) {
    /** 0 = closed, 1 = open; drags write it directly and animations run through the same [draggable] mutex. */
    internal var progress by mutableFloatStateOf(if (open) 1f else 0f)
    internal var width = 0f
    internal var reduceMotion = false
    internal val draggable = DraggableState { delta -> if (width > 0f) progress = (progress + delta / width).coerceIn(0f, 1f) }

    var isOpen by mutableStateOf(open)
        private set

    suspend fun open() = settle(true)

    suspend fun close() = settle(false)

    /** Runs to [open] in a time proportional to the distance left; a drag, which has higher priority, interrupts it. */
    internal suspend fun settle(open: Boolean) {
        isOpen = open
        val target = if (open) 1f else 0f
        draggable.drag(MutatePriority.Default) {
            val distance = abs(target - progress)
            if (reduceMotion || width == 0f || distance == 0f) {
                progress = target
                return@drag
            }
            val spec =
                if (open) {
                    tween<Float>((ENTER_MS * distance).roundToInt(), easing = EnterEasing)
                } else {
                    tween((EXIT_MS * distance).roundToInt(), easing = ExitEasing)
                }
            animate(progress, target, animationSpec = spec) { value, _ -> progress = value }
        }
    }

    companion object {
        val Saver = Saver<DexDrawerState, Boolean>({ it.isOpen }, { DexDrawerState(it) })
    }
}

@Composable
fun rememberDexDrawerState() = rememberSaveable(saver = DexDrawerState.Saver) { DexDrawerState(false) }

/**
 * Modal navigation drawer over [content]. Unlike Material's, a tap on the scrim closes it at any point of the slide,
 * open and close use emphasized tweens rather than a long-tailed spring, and reduced motion makes both instant.
 * Horizontal drags anywhere open or close it while [gesturesEnabled].
 */
@Composable
fun DexModalDrawer(
    state: DexDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    scrimColor: Color = pal.n(900).copy(alpha = .45f),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    SideEffect { state.reduceMotion = reduceMotion }
    val fling = with(LocalDensity.current) { 400.dp.toPx() }
    val showScrim by remember(state) { derivedStateOf { state.progress > 0f } }
    Box(
        modifier
            .fillMaxSize()
            .draggable(
                state.draggable,
                Orientation.Horizontal,
                enabled = gesturesEnabled,
                reverseDirection = LocalLayoutDirection.current == LayoutDirection.Rtl,
                onDragStopped = { velocity ->
                    state.settle(
                        when {
                            velocity > fling -> true
                            velocity < -fling -> false
                            else -> state.progress > .5f
                        },
                    )
                },
            ),
    ) {
        Box { content() }
        if (showScrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.progress }
                    .background(scrimColor)
                    .pointerInput(state) { detectTapGestures { scope.launch { state.close() } } }
                    .semantics {
                        contentDescription = "关闭导航"
                        onClick {
                            scope.launch { state.close() }
                            true
                        }
                    },
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .onSizeChanged { state.width = it.width.toFloat() }
                .offset { IntOffset(((state.progress - 1f) * state.width).roundToInt(), 0) }
                .semantics {
                    paneTitle = "导航菜单"
                    if (state.isOpen) {
                        dismiss {
                            scope.launch { state.close() }
                            true
                        }
                    }
                },
        ) { drawerContent() }
    }
}

@DexPreviews
@Composable
private fun DexModalDrawerPreview() =
    PreviewSurface(Modifier.height(360.dp), PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize()) {
            DexModalDrawer(
                remember { DexDrawerState(true) },
                drawerContent = {
                    Box(
                        Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 44.dp, bottomEnd = 44.dp))
                            .background(pal.bg),
                    ) { Text("导航", Modifier.offset(24.dp, 40.dp)) }
                },
            ) { AppHeader("全国图鉴", "宝可梦", true, {}) }
        }
    }
