package dev.champions.dex.components

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*
import kotlin.math.roundToInt

/**
 * Name of the second-level menu a page shows ("" = none). Recreation (rotation, theme) keeps it open; leaving the page
 * for another destination saves it closed, so coming back shows the page itself.
 */
@Composable
fun rememberMenuState(): MutableState<String> {
    val activity = LocalActivity.current
    return rememberSaveable(saver = Saver({ if (activity?.isChangingConfigurations == true) it.value else "" }, { mutableStateOf(it) })) {
        mutableStateOf("")
    }
}

/** Shows [open]'s menu over this area; a closing menu keeps its content while it slides away. */
@Composable
fun MenuHost(
    open: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(String) -> Unit,
) {
    AnimatedContent(
        open,
        modifier.fillMaxSize(),
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
        label = "menu",
    ) { menu -> if (menu.isNotEmpty()) content(menu) }
}

/** Top-rounded shape of every second-level menu, also used by surfaces that rise from the bottom like one. */
val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Panel that rises from the bottom of a [MenuHost] over a scrim. Dragging the handle or title row down past a short
 * distance, or flinging it, closes the menu; a shorter drag springs back.
 */
@Composable
fun AnimatedVisibilityScope.MenuPanel(
    kicker: String,
    kickerColor: Color,
    title: String,
    fill: Boolean,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val duration = if (reduceMotion) 0 else 280
    val density = LocalDensity.current
    val closeDistance = with(density) { 96.dp.toPx() }
    val closeVelocity = with(density) { 125.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val close by rememberUpdatedState(onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight * .94f
        val maxPx = with(density) { maxH.toPx() }
        Box(
            Modifier
                .matchParentSize()
                .animateEnterExit(fadeIn(tween(duration)), fadeOut(tween(duration)))
                .graphicsLayer { alpha = 1f - (drag / maxPx).coerceIn(0f, 1f) }
                .background(pal.n(900).copy(alpha = .42f))
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "关闭菜单", onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fill) Modifier.height(maxH) else Modifier.heightIn(max = maxH))
                .animateEnterExit(
                    slideInVertically(tween(duration, easing = LinearOutSlowInEasing)) {
                        it
                    },
                    slideOutVertically(tween(duration, easing = FastOutLinearInEasing)) { it },
                ).offset { IntOffset(0, drag.roundToInt()) }
                .shadow(16.dp, PanelShape)
                .clip(PanelShape)
                .background(pal.bg)
                .testTag("menu-sheet")
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            Column(
                Modifier.draggable(
                    rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) },
                    Orientation.Vertical,
                    onDragStopped = { velocity ->
                        when {
                            drag > closeDistance || (velocity > closeVelocity && drag > 0f) -> close()
                            reduceMotion -> drag = 0f
                            else -> animate(drag, 0f) { value, _ -> drag = value }
                        }
                    },
                ),
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).semantics {
                        contentDescription = "下拉关闭"
                        dismiss {
                            close()
                            true
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp, 4.dp).clip(PillShape).background(pal.n(400)))
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Kicker(kicker, color = kickerColor, spacing = 1.1.sp)
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    actions()
                    PrimaryButton("完成", onClose, height = 40.dp)
                }
            }
            val list = rememberLazyListState()
            HideKeyboardWhileScrolling(list.isScrollInProgress)
            LazyColumn(
                Modifier.weight(1f, fill = false).testTag("menu-list"),
                list,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@DexPreviews
@Composable
private fun MenuPanelPreview() =
    PreviewSurface(Modifier.height(520.dp), PaddingValues(0.dp)) {
        MenuHost("conditions") {
            MenuPanel("伤害计算", pal.a(700), "场地与状态", fill = false, onClose = {}) {
                item { SheetLabel("天气") }
                item { PillSegment(listOf("无", "晴天", "下雨", "沙暴", "雪"), 1, {}) }
                item { SheetLabel("场地") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleTile("反射壁", true, {}, Modifier.weight(1f), off = pal.surface)
                        ToggleTile("光墙", false, {}, Modifier.weight(1f), off = pal.surface)
                    }
                }
            }
        }
    }
