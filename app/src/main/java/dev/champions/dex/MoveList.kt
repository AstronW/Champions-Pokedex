package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.champions.dex.components.ActiveFilter
import dev.champions.dex.components.ActiveFilters
import dev.champions.dex.components.DexPreviews
import dev.champions.dex.components.DexSheet
import dev.champions.dex.components.EmptyResult
import dev.champions.dex.components.FilterSheet
import dev.champions.dex.components.GhostButton
import dev.champions.dex.components.HideKeyboardWhileScrolling
import dev.champions.dex.components.MoveHeaderRow
import dev.champions.dex.components.MoveRow
import dev.champions.dex.components.PreviewData
import dev.champions.dex.components.PreviewSurface
import dev.champions.dex.components.SearchField

/*
 * Every move list (the catalogue, a learnset, the calculator's move menu and the team's move picker) is built from the
 * same pieces: a saved [MoveFilterState], [MoveSearchBar] with [MoveFilterSheet] behind its filter button, and
 * [moveItems] rows under a sticky column header. Each screen only configures which moves, columns and extra filters apply.
 */

val MoveCategories = listOf("全部", "物理", "特殊", "变化")

/** Saved move search, filters and sort; [key] changes whenever the result may change. */
class MoveFilterState(
    query: String,
    type: Int?,
    category: Int?,
    unavailable: Boolean,
    sort: MoveSort,
) {
    var query by mutableStateOf(query)
    var type by mutableStateOf(type)
    var category by mutableStateOf(category)
    var includeUnavailable by mutableStateOf(unavailable)
    var sort by mutableStateOf(sort)
    val key get() = listOf(query, type, category, includeUnavailable, sort).joinToString("\u0000")

    /** Clears the filters and sort; the search text stays, as it has its own clear button. */
    fun reset() {
        type = null
        category = null
        includeUnavailable = false
        sort = MoveSort.DEFAULT
    }

    fun apply(
        moves: List<Move>,
        allowUnavailable: Boolean = true,
    ) = filterMoves(moves, query, type, category, allowUnavailable && includeUnavailable, sort)

    fun active(): List<ActiveFilter> =
        buildList {
            category?.let { add(ActiveFilter(MoveCategories[it + 1]) { category = null }) }
            type?.let { add(ActiveFilter(TypeNames[it], it) { type = null }) }
            if (sort != MoveSort.DEFAULT) add(ActiveFilter("按${sort.label}排序") { sort = MoveSort.DEFAULT })
            if (includeUnavailable) add(ActiveFilter("含未开放") { includeUnavailable = false })
        }

    companion object {
        val Saver =
            listSaver<MoveFilterState, Any?>(
                { listOf(it.query, it.type, it.category, it.includeUnavailable, it.sort.name) },
                { MoveFilterState(it[0] as String, it[1] as Int?, it[2] as Int?, it[3] as Boolean, MoveSort.valueOf(it[4] as String)) },
            )
    }
}

@Composable
fun rememberMoveFilter() = rememberSaveable(saver = MoveFilterState.Saver) { MoveFilterState("", null, null, false, MoveSort.DEFAULT) }

/** [categories] narrows the category choice, such as to attacking moves only; [extra] adds screen-specific switches. */
@Composable
fun MoveFilterSheet(
    title: String,
    filter: MoveFilterState,
    shown: Int,
    extra: List<Triple<String, Boolean, () -> Unit>>,
    allowUnavailable: Boolean = true,
    categories: List<String> = MoveCategories,
    onDismiss: () -> Unit,
) {
    FilterSheet(
        title,
        categories,
        (filter.category ?: -1) + 1,
        { filter.category = if (it == 0) null else it - 1 },
        filter.type,
        { filter.type = it },
        MoveSort.entries.map { it.label },
        filter.sort.ordinal,
        { filter.sort = MoveSort.entries[it] },
        (
            if (allowUnavailable) {
                listOf(Triple("含未开放", filter.includeUnavailable) { filter.includeUnavailable = !filter.includeUnavailable })
            } else {
                emptyList()
            }
        ) + extra,
        "显示 $shown 个招式",
        { filter.reset() },
        onDismiss,
    )
}

/**
 * Search field over the active filters, the same above every move list. The filter button opens the screen's
 * [MoveFilterSheet]; [extra] lists filters the screen keeps itself, and [trailing] sits beside the active filters.
 */
@Composable
fun MoveSearchBar(
    filter: MoveFilterState,
    placeholder: String,
    caption: String,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
    extra: List<ActiveFilter> = emptyList(),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val filters = filter.active() + extra
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(filter.query, { filter.query = it }, placeholder, filters.size) {
            focus.clearFocus()
            onFilter()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ActiveFilters(caption, filters) }
            trailing()
        }
    }
}

/**
 * [moves] as [MoveRow]s under a column header that stays pinned while they scroll. The list's background must be
 * [pal.bg], which the header paints over the rows passing beneath it.
 */
fun LazyListScope.moveItems(
    moves: List<Move>,
    showAccuracy: Boolean,
    favorite: (Move) -> Boolean = { false },
    selected: Int? = null,
    note: (Move) -> String? = { null },
    onClick: (Move) -> Unit,
) {
    if (moves.isEmpty()) return
    stickyHeader(key = "moveHeader", contentType = "moveHeader") {
        MoveHeaderRow(Modifier.background(pal.bg).padding(vertical = 6.dp), showAccuracy)
    }
    items(moves, key = { it.id }, contentType = { "move" }) { m ->
        MoveRow(m, showAccuracy, favorite(m), m.id == selected, note(m)) { onClick(m) }
    }
}

/** Move picker in a bottom sheet: search, filters and the shared rows; [onPick] with null clears the slot. */
@Composable
fun MovePickerSheet(
    title: String,
    moves: List<Move>,
    current: Move?,
    onPick: (Move?) -> Unit,
    onDismiss: () -> Unit,
) {
    val filter = rememberMoveFilter()
    var filtering by rememberSaveable { mutableStateOf(false) }
    val shown = remember(moves, filter.key) { filter.apply(moves, allowUnavailable = false) }
    DexSheet(onDismiss) {
        MovePickerContent(title, filter, moves.size, shown, current, onPick, onDismiss, { filtering = true }, Modifier.fillMaxHeight(.85f))
    }
    if (filtering) MoveFilterSheet("筛选招式", filter, shown.size, emptyList(), allowUnavailable = false) { filtering = false }
}

/** The body of [MovePickerSheet]: [shown] are the [total] moves left by [filter]. */
@Composable
fun MovePickerContent(
    title: String,
    filter: MoveFilterState,
    total: Int,
    shown: List<Move>,
    current: Move?,
    onPick: (Move?) -> Unit,
    onCancel: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (current != null) GhostButton("清除") { onPick(null) }
            GhostButton("取消", onCancel)
        }
        MoveSearchBar(filter, "搜索名称、英文或编号", "${shown.size} / $total 个", onFilter)
        // Opens at the current choice, two rows down so the rows above show there is more (the header is item 0).
        val list = rememberLazyListState((shown.indexOfFirst { it.id == current?.id } - 1).coerceAtLeast(0))
        HideKeyboardWhileScrolling(list.isScrollInProgress)
        LazyColumn(
            Modifier.weight(1f),
            list,
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyResult(onClear = {
                        filter.query = ""
                        filter.reset()
                    })
                }
            }
            moveItems(shown, showAccuracy = true, selected = current?.id, onClick = { onPick(it) })
        }
    }
}

@DexPreviews
@Composable
private fun MovePickerPreview() =
    PreviewSurface(Modifier.height(480.dp), PaddingValues(top = 12.dp)) {
        val filter = rememberMoveFilter()
        MovePickerContent("招式 1", filter, 12, PreviewData.moves, PreviewData.earthquake, {}, {}, {})
    }
