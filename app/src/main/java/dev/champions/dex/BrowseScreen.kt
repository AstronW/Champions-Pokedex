package dev.champions.dex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.champions.dex.components.AbilityRow
import dev.champions.dex.components.ActiveFilter
import dev.champions.dex.components.ActiveFilters
import dev.champions.dex.components.AppHeader
import dev.champions.dex.components.EmptyResult
import dev.champions.dex.components.FilterSheet
import dev.champions.dex.components.GhostButton
import dev.champions.dex.components.HideKeyboardWhileScrolling
import dev.champions.dex.components.PokemonRow
import dev.champions.dex.components.SearchField
import dev.champions.dex.components.ToggleTile

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

@Composable
fun MoveListScreen(
    dex: Dex,
    model: DexViewModel,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val filter = rememberMoveFilter()
    val focus = LocalFocusManager.current
    val moves =
        remember(dex, filter.key, favoritesOnly, model.favorites) {
            // The catalogue lists available moves only; unavailable ones stay reachable from learnsets and links.
            filter.apply(dex.availableMoves, allowUnavailable = false).filter { !favoritesOnly || it.key in model.favorites }
        }
    val list = rememberLazyListState()
    HideKeyboardWhileScrolling(list.isScrollInProgress)
    ResetScrollOnChange(listOf(filter.key, favoritesOnly).joinToString("\u0000")) { list.requestScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("${dex.availableMoves.size} 个招式", DexRoot.MOVES.title, true, onMenu)
        ListControls {
            MoveSearchBar(
                filter,
                "搜索招式",
                "${moves.size} 个",
                { sheet = true },
                extra = listOfNotNull(if (favoritesOnly) ActiveFilter("仅看收藏") { favoritesOnly = false } else null),
            ) {
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
                        filter.query = ""
                        favoritesOnly = false
                        filter.reset()
                    })
                }
            }
            moveItems(moves, showAccuracy = true, favorite = { it.key in model.favorites }) {
                focus.clearFocus()
                onOpen(it.key)
            }
        }
    }
    if (sheet) {
        MoveFilterSheet(
            "筛选招式",
            filter,
            moves.size,
            listOf(Triple("仅看收藏", favoritesOnly) { favoritesOnly = !favoritesOnly }),
            allowUnavailable = false,
        ) { sheet = false }
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
