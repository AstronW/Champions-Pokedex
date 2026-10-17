package dev.champions.dex.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.Dex
import dev.champions.dex.ENTER_MS
import dev.champions.dex.EXIT_MS
import dev.champions.dex.EnterEasing
import dev.champions.dex.ExitEasing
import dev.champions.dex.LocalReduceMotion
import dev.champions.dex.MoveSort
import dev.champions.dex.Pokemon
import dev.champions.dex.SearchQuery
import dev.champions.dex.pal
import kotlin.math.roundToInt

/**
 * Where [DexSheet]s are drawn: a layer above the whole app inside the activity's own window. Material's
 * ModalBottomSheet builds a dialog window on every open, which held the sheet back well after the tap; this layer
 * shows the sheet on the next frame.
 */
@Stable
class SheetHostState {
    internal val sheets = mutableStateListOf<SheetEntry>()
}

internal class SheetEntry(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var onDismiss by mutableStateOf(onDismiss)
    var content by mutableStateOf(content)
}

val LocalSheetHost = staticCompositionLocalOf<SheetHostState?> { null }

/** Bottom sheet over the whole app while it is in composition; without a [SheetHost] (previews) it draws in place. */
@Composable
fun DexSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalSheetHost.current
    if (host == null) {
        AnimatedVisibility(true) { SheetPanel(onDismiss, content) }
        return
    }
    val entry = remember { SheetEntry(onDismiss, content) }
    SideEffect {
        entry.onDismiss = onDismiss
        entry.content = content
    }
    DisposableEffect(host, entry) {
        host.sheets += entry
        onDispose { host.sheets -= entry }
    }
}

/** Draws the newest open [DexSheet]; a sheet that has left composition keeps its content while it slides away. */
@Composable
fun SheetHost(
    state: SheetHostState,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        state.sheets.lastOrNull(),
        modifier.fillMaxSize(),
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
        label = "sheet",
    ) { entry -> if (entry != null) SheetPanel(entry.onDismiss, entry.content) }
}

/**
 * Sheet rising from the bottom over a scrim. Back, a scrim tap, or dragging the sheet down past a short distance
 * (or flinging it) dismisses it; a shorter drag springs back.
 */
@Composable
private fun AnimatedVisibilityScope.SheetPanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val enter = if (reduceMotion) 0 else ENTER_MS
    val exit = if (reduceMotion) 0 else EXIT_MS
    val density = LocalDensity.current
    val closeDistance = with(density) { 96.dp.toPx() }
    val closeVelocity = with(density) { 125.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val close by rememberUpdatedState(onDismiss)
    BackHandler(transition.targetState == EnterExitState.Visible) { close() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .animateEnterExit(fadeIn(tween(enter)), fadeOut(tween(exit)))
                .background(pal.n(900).copy(alpha = .45f))
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "关闭", onClick = { close() }),
        )
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .animateEnterExit(
                        slideInVertically(tween(enter, easing = EnterEasing)) { it },
                        slideOutVertically(tween(exit, easing = ExitEasing)) { it },
                    ).offset { IntOffset(0, drag.roundToInt()) }
                    .shadow(16.dp, SheetShape)
                    .clip(SheetShape)
                    .background(pal.bg)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .draggable(
                        rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { velocity ->
                            when {
                                drag > closeDistance || (velocity > closeVelocity && drag > 0f) -> close()
                                reduceMotion -> drag = 0f
                                else -> animate(drag, 0f) { value, _ -> drag = value }
                            }
                        },
                    ).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).semantics {
                        contentDescription = "下拉关闭"
                        dismiss {
                            close()
                            true
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(44.dp, 5.dp).clip(PillShape).background(pal.n(400))) }
                content()
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)

/** Filter sheet: category (moves only), type, sort and extra switches. */
@Composable
fun FilterSheet(
    title: String,
    categories: List<String>?,
    category: Int,
    onCategory: (Int) -> Unit,
    type: Int?,
    onType: (Int?) -> Unit,
    sorts: List<String>,
    sort: Int,
    onSort: (Int) -> Unit,
    toggles: List<Triple<String, Boolean, () -> Unit>>,
    doneLabel: String,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    DexSheet(onDismiss) {
        FilterSheetContent(
            title,
            categories,
            category,
            onCategory,
            type,
            onType,
            sorts,
            sort,
            onSort,
            toggles,
            doneLabel,
            onReset,
            onDismiss,
        )
    }
}

/** The body of [FilterSheet], apart from the sheet so it can be previewed. */
@Composable
fun FilterSheetContent(
    title: String,
    categories: List<String>?,
    category: Int,
    onCategory: (Int) -> Unit,
    type: Int?,
    onType: (Int?) -> Unit,
    sorts: List<String>,
    sort: Int,
    onSort: (Int) -> Unit,
    toggles: List<Triple<String, Boolean, () -> Unit>>,
    doneLabel: String,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        // Scrolls when taller than the sheet, as in landscape, so the done button stays reachable.
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            GhostButton("重置", onReset)
        }
        if (categories != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetLabel("分类")
                PillSegment(categories, category, onCategory)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetLabel("属性")
            TypeGrid(type, onType)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetLabel("排序")
            PillSegment(sorts, sort, onSort)
        }
        if (toggles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                toggles.forEach { (label, on, flip) -> ToggleTile(label, on, flip, Modifier.weight(1f), off = pal.surface) }
            }
        }
        PrimaryButton(doneLabel, onDone, Modifier.fillMaxWidth(), 50.dp)
    }
}

/** Searchable Pokémon list in a bottom sheet. */
@Composable
fun PokemonPickerSheet(
    title: String,
    dex: Dex,
    onPick: (Pokemon) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val list =
        remember(dex, query) {
            val search = SearchQuery(query)
            dex.pokemon.filter { search.matches(it.searchText) }
        }
    DexSheet(onDismiss) {
        PokemonPickerContent(title, query, { query = it }, list, onPick, onDismiss, Modifier.fillMaxHeight(.85f))
    }
}

/** The body of [PokemonPickerSheet]: search field over the [list] matching [query]. */
@Composable
fun PokemonPickerContent(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    list: List<Pokemon>,
    onPick: (Pokemon) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            GhostButton("取消", onCancel)
        }
        SearchField(query, onQuery, "搜索名称、英文或编号", null)
        val state = rememberLazyListState()
        HideKeyboardWhileScrolling(state.isScrollInProgress)
        LazyColumn(
            Modifier.weight(1f),
            state,
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(list, key = { it.id }) { p -> PokemonPickerRow(p) { onPick(p) } }
        }
    }
}

@Composable
fun PokemonPickerRow(
    p: Pokemon,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                PillShape,
            ).background(pal.surface)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Blob(p, 46.dp)
        Text(p.displayName, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        TypeTagColumn(p.types)
    }
}

@DexPreviews
@Composable
private fun FilterSheetPreview() =
    PreviewSurface(padding = PaddingValues(0.dp)) {
        FilterSheetContent(
            "招式筛选",
            listOf("全部", "物理", "特殊", "变化"),
            1,
            {},
            9,
            {},
            MoveSort.entries.map { it.label },
            1,
            {},
            listOf(Triple("含未开放", true, {}), Triple("仅看收藏", false, {})),
            "显示 128 个招式",
            {},
            {},
        )
    }

@DexPreviews
@Composable
private fun PokemonPickerPreview() =
    PreviewSurface(Modifier.height(420.dp), PaddingValues(top = 12.dp)) {
        PokemonPickerContent("选择宝可梦", "", {}, PreviewData.pokemon, {}, {})
    }
