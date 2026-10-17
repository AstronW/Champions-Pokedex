package dev.champions.dex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.champions.dex.components.*

/** Scroll back to the top whenever the filter key changes, but not when the key is only restored. */
@Composable
internal fun ResetScrollOnChange(
    filterKey: String,
    reset: () -> Unit,
) {
    var previous by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        // A filtered result may be empty, so request the next layout instead of awaiting it.
        if (previous != filterKey) {
            reset()
            previous = filterKey
        }
    }
}

/** Search and active filters stay pinned above the list, so they remain reachable after scrolling. */
@Composable
private fun ListControls(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )

@Composable
fun PokemonListScreen(
    dex: Dex,
    model: DexViewModel,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var allForms by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.NUMBER) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val pokemon =
        remember(dex, query, type, allForms, favoritesOnly, model.favorites, sort) {
            filterPokemon(dex, query, type, allForms, favoritesOnly, model.favorites, sort)
        }
    val grid = rememberLazyGridState()
    HideKeyboardWhileScrolling(grid.isScrollInProgress)
    ResetScrollOnChange(listOf(query, type, allForms, favoritesOnly, sort).joinToString("\u0000")) { grid.requestScrollToItem(0) }

    fun clear() {
        query = ""
        type = null
        favoritesOnly = false
        allForms = false
        sort = SortOrder.NUMBER
    }
    val filters =
        buildList {
            type?.let { add(ActiveFilter(TypeNames[it], it) { type = null }) }
            if (sort != SortOrder.NUMBER) add(ActiveFilter("按${sort.short}排序") { sort = SortOrder.NUMBER })
            if (allForms) add(ActiveFilter("全部形态") { allForms = false })
            if (favoritesOnly) add(ActiveFilter("仅看收藏") { favoritesOnly = false })
        }
    Column(Modifier.fillMaxSize()) {
        AppHeader("全国图鉴 · ${dex.speciesCount} 种", DexRoot.POKEMON.title, true, onMenu)
        ListControls {
            SearchField(query, { query = it }, "搜索名称、英文名或编号", filters.size) {
                focus.clearFocus()
                sheet = true
            }
            ActiveFilters("${pokemon.size} 只", filters)
        }
        LazyVerticalGrid(
            GridCells.Adaptive(340.dp),
            state = grid,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (pokemon.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyResult("没有找到符合条件的宝可梦", ::clear) }
            items(pokemon, key = { it.id }) { p ->
                val variants = dex.forms[p.no].orEmpty()
                val grouped = !allForms && variants.size >= 2 && p.id == variants.first().id
                val mega = grouped && variants.any { "超级" in it.form }
                val badge = if (grouped && !mega) "${variants.size} 种形态" else null
                val speed = sort == SortOrder.SPEED
                PokemonRow(
                    p,
                    mega,
                    badge,
                    if (speed) "速度" else "总和",
                    "${if (speed) p.stats[5] else p.total}",
                    p.key in model.favorites,
                    {
                        focus.clearFocus()
                        onOpen(p.key)
                    },
                    { model.toggleFavorite(p.key) },
                )
            }
        }
    }
    if (sheet) {
        FilterSheet(
            "筛选宝可梦",
            null,
            0,
            {},
            type,
            { type = it },
            SortOrder.entries.map { it.short },
            sort.ordinal,
            {
                sort =
                    SortOrder.entries[it]
            },
            listOf(Triple("全部形态", allForms) { allForms = !allForms }, Triple("仅看收藏", favoritesOnly) { favoritesOnly = !favoritesOnly }),
            "显示 ${pokemon.size} 只宝可梦",
            {
                type = null
                sort = SortOrder.NUMBER
                allForms = false
                favoritesOnly = false
            },
        ) { sheet = false }
    }
}

private val MoveCategories = listOf("全部", "物理", "特殊", "变化")

/** Saved move filter state, shared by the move catalogue and the learnset tab. */
class MoveFilterState(
    type: Int?,
    category: Int?,
    unavailable: Boolean,
    sort: MoveSort,
) {
    var type by mutableStateOf(type)
    var category by mutableStateOf(category)
    var includeUnavailable by mutableStateOf(unavailable)
    var sort by mutableStateOf(sort)
    val key get() = listOf(type, category, includeUnavailable, sort).joinToString("\u0000")

    fun reset() {
        type = null
        category = null
        includeUnavailable = false
        sort = MoveSort.DEFAULT
    }

    fun active(): List<ActiveFilter> =
        buildList {
            category?.let { add(ActiveFilter(MoveCategories[it + 1]) { category = null }) }
            type?.let { add(ActiveFilter(TypeNames[it], it) { type = null }) }
            if (sort != MoveSort.DEFAULT) add(ActiveFilter("按${sort.label}排序") { sort = MoveSort.DEFAULT })
            if (includeUnavailable) add(ActiveFilter("含未开放") { includeUnavailable = false })
        }

    companion object {
        val Saver =
            androidx.compose.runtime.saveable.listSaver<MoveFilterState, Any?>(
                { listOf(it.type, it.category, it.includeUnavailable, it.sort.name) },
                { MoveFilterState(it[0] as Int?, it[1] as Int?, it[2] as Boolean, MoveSort.valueOf(it[3] as String)) },
            )
    }
}

@Composable
fun rememberMoveFilter() = rememberSaveable(saver = MoveFilterState.Saver) { MoveFilterState(null, null, false, MoveSort.DEFAULT) }

@Composable
fun MoveFilterSheet(
    title: String,
    filter: MoveFilterState,
    shown: Int,
    extra: List<Triple<String, Boolean, () -> Unit>>,
    allowUnavailable: Boolean = true,
    onDismiss: () -> Unit,
) {
    FilterSheet(
        title,
        MoveCategories,
        (filter.category ?: -1) + 1,
        {
            filter.category =
                if (it ==
                    0
                ) {
                    null
                } else {
                    it - 1
                }
        },
        filter.type,
        { filter.type = it },
        MoveSort.entries.map { it.label },
        filter.sort.ordinal,
        { filter.sort = MoveSort.entries[it] },
        (
            if (allowUnavailable) {
                listOf(
                    Triple("含未开放", filter.includeUnavailable) {
                        filter.includeUnavailable = !filter.includeUnavailable
                    },
                )
            } else {
                emptyList()
            }
        ) +
            extra,
        "显示 $shown 个招式",
        { filter.reset() },
        onDismiss,
    )
}

@Composable
fun MoveListScreen(
    dex: Dex,
    model: DexViewModel,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val filter = rememberMoveFilter()
    val focus = LocalFocusManager.current
    val moves =
        remember(dex, query, filter.key, favoritesOnly, model.favorites) {
            // The catalogue lists available moves only; unavailable ones stay reachable from learnsets and links.
            filterMoves(dex.availableMoves, query, filter.type, filter.category, false, filter.sort)
                .filter { !favoritesOnly || it.key in model.favorites }
        }
    val list = rememberLazyListState()
    HideKeyboardWhileScrolling(list.isScrollInProgress)
    ResetScrollOnChange(listOf(query, filter.key, favoritesOnly).joinToString("\u0000")) { list.requestScrollToItem(0) }
    val filters = filter.active() + listOfNotNull(if (favoritesOnly) ActiveFilter("仅看收藏") { favoritesOnly = false } else null)
    Column(Modifier.fillMaxSize()) {
        AppHeader("${dex.availableMoves.size} 个招式", DexRoot.MOVES.title, true, onMenu)
        ListControls {
            SearchField(query, { query = it }, "搜索招式", filters.size) {
                focus.clearFocus()
                sheet = true
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ActiveFilters("${moves.size} 个", filters) }
                GhostButton("详细分类") {
                    focus.clearFocus()
                    onOpen("categories:move")
                }
            }
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (moves.isEmpty()) {
                item(key = "empty") {
                    EmptyResult(onClear = {
                        query = ""
                        favoritesOnly = false
                        filter.reset()
                    })
                }
            } else {
                item(key = "head") { MoveHeaderRow() }
            }
            items(moves, key = { it.id }) { m ->
                MoveRow(m, true, m.key in model.favorites) {
                    focus.clearFocus()
                    onOpen(m.key)
                }
            }
        }
    }
    if (sheet) {
        MoveFilterSheet(
            "筛选招式",
            filter,
            moves.size,
            listOf(
                Triple("仅看收藏", favoritesOnly) {
                    favoritesOnly = !favoritesOnly
                },
            ),
            allowUnavailable = false,
        ) {
            sheet =
                false
        }
    }
}

@Composable
fun AbilityListScreen(
    dex: Dex,
    model: DexViewModel,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val abilities =
        remember(dex, query, favoritesOnly, model.favorites) {
            val search = SearchQuery(query)
            dex.abilities.filter { search.matches(it.searchText) && (!favoritesOnly || it.key in model.favorites) }
        }
    val list = rememberLazyListState()
    HideKeyboardWhileScrolling(list.isScrollInProgress)
    ResetScrollOnChange(listOf(query, favoritesOnly).joinToString("\u0000")) { list.requestScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("${dex.abilities.size} 个特性", DexRoot.ABILITIES.title, true, onMenu)
        ListControls {
            SearchField(query, { query = it }, "搜索特性", null)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ActiveFilters("${abilities.size} 个", emptyList()) }
                GhostButton("相关分类") {
                    focus.clearFocus()
                    onOpen("categories:ability")
                }
                ToggleTile("仅看收藏", favoritesOnly, { favoritesOnly = !favoritesOnly }, Modifier.width(96.dp), off = pal.surface)
            }
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (abilities.isEmpty()) {
                item(key = "empty") {
                    EmptyResult(onClear = {
                        query = ""
                        favoritesOnly = false
                    })
                }
            }
            items(abilities, key = { it.id }) { a ->
                AbilityRow(a.name, a.description, null, a.key in model.favorites, {
                    focus.clearFocus()
                    onOpen(a.key)
                })
            }
        }
    }
}
