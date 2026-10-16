@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.champions.dex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.*

@Composable
private fun ReferenceChip(label: String, key: String, onOpen: (String) -> Unit) {
    Pill(label, pal.surface, pal.a(800), Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { onOpen(key) }.wrapContentHeight().testTag("link:$key"), fontSize = 12.sp)
}

/** Wiki metadata stays separate from the game's contact/category/numeric fields. */
fun LazyListScope.mechanicsSection(key: String, dex: Dex, onOpen: (String) -> Unit) {
    val mechanics = dex.mechanics
    val relations = mechanics.relationsByEntity[key].orEmpty().filter { it.relation != "section_reference" }
    if (relations.isNotEmpty()) item(key = "categories") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("详细分类 · 百科")
            relations.groupBy { it.relation }.forEach { (relation, entries) ->
                Text(if (relation == "member") "所属分类" else "相关分类", fontSize = 11.sp, color = pal.n(700))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.distinctBy { it.categoryId }.forEach { r -> mechanics.categoriesById[r.categoryId]?.let { ReferenceChip(it.name, it.key, onOpen) } }
                }
            }
        }
    }
    mechanics.targets[key]?.let { target -> item(key = "target") {
        DexCard(padding = PaddingValues(16.dp), spacing = 6.dp) {
            Kicker("招式目标 · 百科")
            Text(if (target.text.isBlank() || target.text == "未知") "百科未标明目标范围。" else target.text, fontSize = 14.sp, lineHeight = 22.sp)
        }
    } }
    val flags = mechanics.flags[key].orEmpty().filter { it.field !in HiddenSpecialFlags }
    if (flags.isNotEmpty()) item(key = "flags") {
        var expanded by rememberSaveable { mutableStateOf(false) }
        DexCard(padding = PaddingValues(16.dp), spacing = 10.dp) {
            Text("特殊判定 · ${flags.size} 项  ${if (expanded) "收起 −" else "展开 +"}",
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { expanded = !expanded }.wrapContentHeight().testTag("special-flags"),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pal.a(800))
            if (expanded) {
                flags.forEach { flag ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(SpecialFlagLabels[flag.field].orEmpty(), Modifier.width(86.dp), fontSize = 12.sp, color = pal.n(700))
                        Text(flag.displayText,
                            Modifier.weight(1f), fontSize = 13.sp, lineHeight = 20.sp, color = if (flag.value == null) pal.n(600) else pal.text)
                    }
                }
                Text("以上为百科记录；未标明的项目保留为空。", fontSize = 11.sp, color = pal.n(700))
            }
            if (mechanics.issues.containsKey(key)) Text("此条目的分类或判定存在来源差异，请结合游戏标记与百科正文查看。", fontSize = 12.sp, lineHeight = 19.sp, color = pal.a(800))
        }
    }
    val refs = mechanics.referencesByEntity[key].orEmpty()
    val direct = refs.filter { it.inDescription }.mapNotNull { mechanics.statusesByBody[it.bodyKey] }.distinctBy { it.key }
    val other = refs.mapNotNull { mechanics.statusesByBody[it.bodyKey] }.distinctBy { it.key }.filterNot { it in direct }
    if (direct.isNotEmpty() || other.isNotEmpty()) item(key = "statusLinks") {
        var showOther by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("提及的状态")
            Text("点击查看游戏说明与百科效果；引用可能包含历史或其他游戏语境。", fontSize = 11.sp, lineHeight = 17.sp, color = pal.n(700))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                direct.forEach { ReferenceChip(it.displayName, it.key, onOpen) }
            }
            if (other.isNotEmpty()) {
                Text("其他章节引用 · ${other.size}  ${if (showOther) "收起 −" else "展开 +"}", Modifier.clickable(role = Role.Button) { showOther = !showOther }.padding(vertical = 10.dp), fontSize = 12.sp, color = pal.a(800))
                if (showOther) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    other.forEach { ReferenceChip(it.displayName, it.key, onOpen) }
                }
            }
        }
    }
}

@Composable
fun StatusListScreen(dex: Dex, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val statuses = remember(dex, query) { val search = SearchQuery(query); dex.mechanics.statuses.filter { search.matches(it.searchText) } }
    val list = rememberLazyListState()
    HideKeyboardWhileScrolling(list.isScrollInProgress)
    ResetScrollOnChange(query) { list.requestScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("${dex.mechanics.statuses.size} 篇离线资料", "状态说明", true, onMenu)
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(query, { query = it }, "搜索状态、天气或别名", null)
            Text("招式、特性与道具引用的百科资料，包含历史和其他游戏内容。", fontSize = 11.sp, lineHeight = 17.sp, color = pal.n(700))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (statuses.isEmpty()) item { EmptyResult(onClear = { query = "" }) }
            items(statuses, key = { it.key }) { status ->
                AbilityRow(status.displayName, status.game.firstOrNull()?.description?.replace('\n', ' ') ?: status.kindLabel,
                    status.kindLabel.takeIf { status.kind != "status_page" }, onClick = { focus.clearFocus(); onOpen(status.key) }, chevron = true)
            }
        }
    }
}

@Composable
fun StatusDetail(status: BattleStatus, dex: Dex, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val references = dex.mechanics.referencesByBody[status.bodyKey].orEmpty().filter { dex.entityName(it.entityKey) != null }
        .groupBy { it.entityKey }
    val moves = references.filterKeys { it.startsWith("move:") }.entries.toList()
    val abilities = references.filterKeys { it.startsWith("ability:") }.entries.toList()
    val items = references.filterKeys { it.startsWith("item:") }.entries.toList()
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(status.kindLabel, status.displayName, false, onBack)
        LazyColumn(Modifier.testTag("status-detail-list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") {
                Text(status.displayName, style = MaterialTheme.typography.headlineLarge)
                Text("百科资料保留原有版本语境；被引用不代表该状态在 Champions 中可用。", Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 19.sp, color = pal.n(700))
                if (status.kind == "redirected_definition") Text("该状态的定义收录于招式页面。", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = pal.a(800))
            }
            if (status.game.isNotEmpty()) item(key = "game") {
                DexCard(padding = PaddingValues(18.dp)) {
                    Kicker("游戏说明 · CHAMPIONS")
                    status.game.distinctBy { it.name to it.description }.forEach { game ->
                        Text(game.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(game.description.ifBlank { "游戏未提供此状态的说明。" }, fontSize = 14.sp, lineHeight = 23.sp)
                    }
                }
            }
            item(key = "section") { Text("百科摘录 · ${status.section}", fontSize = 12.sp, color = pal.n(700)) }
            wikiSection(status.key, dex, onOpen)
            item(key = "referencesHead") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("引用此资料的条目")
                    PillSegment(listOf("招式 · ${moves.size}", "特性 · ${abilities.size}", "道具 · ${items.size}"), tab, { tab = it })
                }
            }
            val shown = listOf(moves, abilities, items)[tab]
            if (shown.isEmpty()) item(key = "referencesEmpty") { Text("没有引用此资料的${listOf("招式", "特性", "道具")[tab]}。", fontSize = 13.sp, color = pal.n(700)) }
            items(shown, key = { it.key }) { (key, refs) ->
                EntityReferenceRow(key, dex, onOpen)
            }
        }
    }
}

@Composable
fun CategoryListScreen(kind: String, dex: Dex, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val categories = remember(dex, kind, query) {
        val search = SearchQuery(query)
        dex.mechanics.categories.filter { c -> search.matches("${c.name} ${c.group}".lowercase()) &&
            dex.mechanics.relationsByCategory[c.id].orEmpty().any { it.entityKey.startsWith("$kind:") && it.relation != "section_reference" } }
    }
    val list = rememberLazyListState()
    HideKeyboardWhileScrolling(list.isScrollInProgress)
    ResetScrollOnChange(query) { list.requestScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("百科分类索引", if (kind == "ability") "特性相关分类" else "招式详细分类", false, onBack)
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { SearchField(query, { query = it }, "搜索分类", null) }
        LazyColumn(state = list, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (categories.isEmpty()) item { EmptyResult(onClear = { query = "" }) }
            items(categories, key = { it.id }) { c ->
                val entries = dex.mechanics.relationsByCategory[c.id].orEmpty()
                val count = entries.filter { it.entityKey.startsWith("$kind:") && it.relation != "section_reference" }.distinctBy { it.entityKey }.size
                AbilityRow(c.name, c.group, "$count 个关联", onClick = { focus.clearFocus(); onOpen(c.key) }, chevron = true)
            }
        }
    }
}

@Composable
fun CategoryDetail(category: MoveCategory, dex: Dex, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val groups = dex.mechanics.relationsByCategory[category.id].orEmpty().groupBy { it.relation }
    var showMentions by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("详细分类", category.name, false, onBack)
        LazyColumn(Modifier.testTag("category-detail-list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "hero") {
                Text(category.name, style = MaterialTheme.typography.headlineLarge)
                Text(category.group, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = pal.n(700))
                Text("按 Champions 收录范围关联；分类成员与相关条目分别列出，具体条件以百科原文为准。", Modifier.padding(top = 10.dp), fontSize = 12.sp, lineHeight = 19.sp, color = pal.n(700))
                Text("52poke · 修订 ${category.revision} ↗", Modifier.clickable(role = Role.Button) { openWeb(context, category.url) }.padding(vertical = 10.dp), fontSize = 12.sp, color = pal.a(800))
                Text("CC BY-NC-SA 3.0 ↗", Modifier.clickable(role = Role.Button) { openWeb(context, "https://creativecommons.org/licenses/by-nc-sa/3.0/") }.padding(vertical = 10.dp), fontSize = 11.sp, color = pal.a(800))
            }
            listOf("member" to "成员招式", "related_move" to "相关招式", "related_ability" to "相关特性", "section_reference" to "段落提及").forEach { (relation, title) ->
                val entries = groups[relation].orEmpty().groupBy { it.entityKey }
                if (entries.isNotEmpty()) {
                    item(key = "heading:$relation") {
                        if (relation == "section_reference") Text("$title · ${entries.size}  ${if (showMentions) "收起 −" else "展开 +"}",
                            Modifier.clickable(role = Role.Button) { showMentions = !showMentions }.padding(vertical = 10.dp), fontSize = 14.sp, color = pal.a(800))
                        else SectionHeading("$title · ${entries.size}")
                    }
                    if (relation != "section_reference" || showMentions) items(entries.entries.toList(), key = { "$relation:${it.key}" }) { (key, rows) ->
                        EntityReferenceRow(key, dex, onOpen)
                    }
                }
            }
        }
    }
}

private fun Dex.entityName(key: String): String? = when (key.substringBefore(':')) {
    "move" -> movesById[key.substringAfter(':').toIntOrNull()]?.name
    "ability" -> abilitiesById[key.substringAfter(':').toIntOrNull()]?.name
    "item" -> itemsById[key.substringAfter(':').toIntOrNull()]?.name
    else -> null
}

@Composable
private fun EntityReferenceRow(key: String, dex: Dex, onOpen: (String) -> Unit) {
    val id = key.substringAfter(':').toIntOrNull()
    when (key.substringBefore(':')) {
        "move" -> dex.movesById[id]?.let { MoveRow(it, false) { onOpen(key) } }
        "ability" -> dex.abilitiesById[id]?.let { AbilityRow(it.name, it.description, null, onClick = { onOpen(key) }, chevron = true) }
        "item" -> dex.itemsById[id]?.let { AbilityRow(it.name, it.description, null, onClick = { onOpen(key) }, chevron = true) }
    }
}
