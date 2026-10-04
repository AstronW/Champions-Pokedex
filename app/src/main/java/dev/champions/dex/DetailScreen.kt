package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun DetailScreen(key: String, dex: Dex, model: DexViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    when {
        key.startsWith("pokemon:") -> dex.pokemonById[key.substringAfter(':')]?.let { PokemonDetail(it, dex, model, onBack, onOpen) }
        key.startsWith("move:") -> dex.movesById[key.substringAfter(':').toIntOrNull()]?.let { MoveDetail(it, dex, model, onBack, onOpen) }
        key.startsWith("ability:") -> dex.abilitiesById[key.substringAfter(':').toIntOrNull()]?.let { AbilityDetail(it, dex, model, onBack, onOpen) }
    }
}

/** Short on-screen label for a form: its form name, or the species name for the regular form. */
val Pokemon.formLabel get() = form.ifBlank { name }

private fun signed(v: Int) = if (v > 0) "+$v" else "−${-v}"

@Composable
fun PokemonDetail(entry: Pokemon, dex: Dex, model: DexViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val variants = remember(entry.no) { dex.forms[entry.no].orEmpty().sortedBy { it.formId } }
    // Switching form stays on this entry, so the layout (and the stat card) keeps its position.
    var selected by rememberSaveable { mutableStateOf(entry.id) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var shiny by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val filter = rememberMoveFilter()
    val p = dex.pokemonById[selected] ?: entry
    val base = variants.firstOrNull() ?: p
    val learnset = remember(p.id) { dex.learnsets[p.id].orEmpty().mapNotNull { dex.movesById[it] } }
    val moves = remember(learnset, filter.key) { filterMoves(learnset, "", filter.type, filter.category, filter.includeUnavailable, filter.sort) }
    val toast = LocalToast.current
    val compact = LocalConfiguration.current.screenHeightDp < 500
    Column(Modifier.fillMaxSize()) {
        AppHeader(p.number(), p.formLabel, false, onBack) { FavoriteButton(p.key in model.favorites, { model.toggleFavorite(p.key); toast(if (p.key in model.favorites) "已加入收藏" else "已取消收藏") }) }
        LazyColumn(Modifier.weight(1f).testTag("pokemon-detail-list"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") { PokemonHero(p, shiny, compact) { shiny = !shiny } }
            if (variants.size > 1) item(key = "forms") {
                PillSegment(variants.map { it.formLabel }, variants.indexOfFirst { it.id == p.id }, { selected = variants[it].id },
                    container = pal.surface, height = 42.dp, fontSize = 15.sp, heading = true, scrollable = variants.size > 3)
            }
            item(key = "tabs") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("基础信息", "招式 · ${learnset.count { it.available }}").forEachIndexed { i, label ->
                        val on = tab == i
                        Box(Modifier.weight(1f).clip(PillShape).border(1.5.dp, if (on) pal.text else pal.divider, PillShape).background(if (on) pal.text else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = i }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (on) pal.bg else pal.n(800), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (tab == 0) {
                item(key = "stats") { StatsCard(p, base) }
                item(key = "abilities") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("特性")
                        p.abilities.distinct().forEach { id ->
                            val a = dex.abilitiesById[id] ?: return@forEach
                            val hidden = p.abilities.getOrNull(2) == id && id !in p.abilities.take(2)
                            AbilityRow(a.name, a.description, if (hidden) "隐藏特性" else null, onClick = { onOpen(a.key) }, chevron = true)
                        }
                    }
                }
                item(key = "body") { BodyTile("体重", String.format(Locale.ROOT, "%.1f kg", p.weight / 10.0), Modifier.fillMaxWidth()) }
                item(key = "weak") { WeaknessCard(p.types) }
            } else {
                item(key = "moveHead") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("可学会的招式 · ${moves.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            val active = filter.active().size
                            Row(Modifier.heightIn(min = 40.dp).clip(PillShape).background(if (active > 0) pal.accent else pal.surface).clickable { sheet = true }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val fg = if (active > 0) pal.bg else pal.text
                                Glyph(GlyphKind.FILTER, Modifier.size(16.dp), fg); Text("筛选 · 排序", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (filter.active().isNotEmpty()) ActiveFilters(null, filter.active())
                    }
                }
                if (moves.isEmpty()) item(key = "noMoves") { EmptyResult("没有符合条件的招式") }
                items(moves, key = { it.id }) { m -> MoveRow(m, false, m.key in model.favorites) { onOpen(m.key) } }
            }
        }
        Row(Modifier.fillMaxWidth().background(pal.bg).padding(start = 18.dp, end = 18.dp, top = if (compact) 8.dp else 14.dp, bottom = if (compact) 8.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("加入队伍", { toast(if (model.addToTeam(p.id)) "已加入队伍" else "队伍已满 6 只") }, Modifier.weight(1f))
            PrimaryButton("用于伤害计算", { onOpen("calculator:${p.id}") }, Modifier.weight(1.3f))
        }
    }
    if (sheet) MoveFilterSheet("筛选招式", filter, moves.size, emptyList()) { sheet = false }
}

@Composable
private fun PokemonHero(p: Pokemon, shiny: Boolean, compact: Boolean, onShiny: () -> Unit) {
    val tone1 = typeTone(p.types.first()); val tone2 = typeTone(p.types.last())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The primary type colour fills the whole hero; a second type tints a soft corner.
        Box(Modifier.fillMaxWidth().height(if (compact) 180.dp else 250.dp).clip(CardShape).background(tone1.blob), contentAlignment = Alignment.Center) {
            if (tone2 != tone1) Box(Modifier.align(Alignment.BottomEnd).offset(x = 60.dp, y = 60.dp).size(180.dp).clip(CircleShape).background(tone2.blob))
            PokemonArt(p, Modifier.fillMaxHeight().aspectRatio(1f).padding(vertical = 8.dp), shiny)
            if (p.shiny.isNotEmpty()) Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { ToggleTile(if (shiny) "★ 异色" else "☆ 异色", shiny, onShiny, Modifier.width(76.dp), off = pal.bg.copy(alpha = .75f)) }
        }
        if (p.sprite.isEmpty()) Text("此形态的图片尚未收录于 Champions 图片源", fontSize = 11.sp, color = pal.n(700))
        Text(p.displayName, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 38.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.types.distinct().forEach { TypeTag(it, fontSize = 13.sp, horizontal = 14.dp, vertical = 4.dp) } }
    }
}

@Composable
private fun StatsCard(p: Pokemon, base: Pokemon) {
    val isForm = p.id != base.id
    DexCard(padding = PaddingValues(18.dp), spacing = 11.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("种族值", Modifier.width(104.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, color = pal.n(700))
            Spacer(Modifier.weight(1f))
            Text("Lv50 最高", Modifier.width(52.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.n(700), textAlign = TextAlign.End)
        }
        p.stats.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(StatLabels[i], Modifier.width(44.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                Row(Modifier.width(52.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("$v", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val d = v - base.stats[i]
                    if (isForm && d != 0) Text(signed(d), Modifier.padding(bottom = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (d > 0) pal.g(700) else pal.a(700))
                }
                Bar(v / 180f, statColor(v), Modifier.weight(1f))
                Text("${if (i == 0) actualStat(v, 32, 0) else actualStat(v, 32, i, 110)}", Modifier.width(52.dp), fontSize = 12.sp, color = pal.n(700), textAlign = TextAlign.End)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("总和", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
            val d = p.total - base.total
            if (isForm && d != 0) Text(signed(d), Modifier.padding(end = 6.dp, bottom = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (d > 0) pal.g(700) else pal.a(700))
            Text("${p.total}", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 22.sp)
        }
    }
}

@Composable
private fun BodyTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(pal.g(100)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.g(800)); Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** A labelled group of type tags; [suffix] adds e.g. a member count after the type name. */
data class TypeGroup(val label: String, val color: androidx.compose.ui.graphics.Color, val types: List<Int>, val suffix: (Int) -> String = { "" })

/** Shared layout for type-matchup summaries (Pokémon detail and team analysis). */
@Composable
fun TypeGroupsCard(title: String, groups: List<TypeGroup>, note: String, empty: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(title)
        DexCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), spacing = 12.dp) {
            val shown = groups.filter { it.types.isNotEmpty() }
            if (shown.isEmpty() && empty != null) Text(empty, fontSize = 12.sp, color = pal.n(700))
            shown.forEach { g ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.label, Modifier.width(62.dp).padding(top = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = g.color)
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        g.types.forEach { TypeTag(it, horizontal = 10.dp, vertical = 3.dp, suffix = g.suffix(it)) }
                    }
                }
            }
            Text(note, fontSize = 11.sp, color = pal.n(700))
        }
    }
}

@Composable
private fun WeaknessCard(types: List<Int>) {
    val groups = listOf(Triple(4.0, "×4 极弱", pal.a(800)), Triple(2.0, "×2 弱点", pal.a(700)), Triple(.5, "×½ 抵抗", pal.g(700)),
        Triple(.25, "×¼ 极抗", pal.g(800)), Triple(0.0, "×0 免疫", pal.n(800)))
        .map { (m, label, color) -> TypeGroup(label, color, TypeNames.indices.filter { effectiveness(it, types) == m }) }
    TypeGroupsCard("受到的属性伤害", groups, "按属性相性计算，不含特性与道具。")
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(pal.surface).padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        Text(value, fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 22.sp, maxLines = 1)
    }
}

@Composable
private fun GameText(description: String) {
    Text(if (description.isBlank() || description == "―") "游戏未提供此条目的说明。" else description.replace('\n', ' '), fontSize = 14.5.sp, lineHeight = 23.sp)
}

/** Learners/holders in a three-column grid of equal tiles tinted by each Pokémon's primary type. */
private fun androidx.compose.foundation.lazy.LazyListScope.pokemonGrid(list: List<Pokemon>, onOpen: (String) -> Unit) {
    items(list.chunked(3), key = { "g:" + it.first().id }) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { p ->
                Column(Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(28.dp)).background(typeTone(p.types.first()).blob).clickable { onOpen(p.key) }
                    .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PokemonArt(p, Modifier.fillMaxWidth().weight(1f))
                    Text(p.displayName, Modifier.height(32.dp).wrapContentHeight(Alignment.CenterVertically), fontSize = 12.5.sp, lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun MoveDetail(move: Move, dex: Dex, model: DexViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val users = dex.moveUsers[move.id].orEmpty()
    val tone = typeTone(move.type)
    Column(Modifier.fillMaxSize()) {
        AppHeader("招式详情", move.name, false, onBack) { FavoriteButton(move.key in model.favorites, { model.toggleFavorite(move.key) }) }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().clip(CardShape).background(tone.bg)) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-40).dp).size(150.dp).alpha(.35f).clip(CircleShape).background(tone.solid))
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(TypeNames[move.type], tone.fg, pal.bg, fontSize = 12.sp); Pill(move.categoryLabel, pal.bg, tone.fg, fontSize = 12.sp)
                        }
                        Text(move.name, style = MaterialTheme.typography.headlineLarge, color = tone.fg)
                    }
                }
            }
            item(key = "metrics") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("威力", move.powerLabel, Modifier.weight(1f)); Metric("命中", move.accuracyLabel, Modifier.weight(1f))
                    Metric("PP", "${move.pp}", Modifier.weight(1f)); Metric("优先度", if (move.priority > 0) "+${move.priority}" else "${move.priority}", Modifier.weight(1f))
                }
            }
            item(key = "tags") {
                // Move flags as categories; the row scrolls sideways once more flags are added.
                val tags = listOfNotNull(if (move.contact) "接触招式" else null, if (!move.available) "当前未开放" else null)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("分类", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tags.isEmpty()) Pill("无", pal.surface, pal.n(600), fontSize = 12.sp)
                        tags.forEach { t -> val closed = t == "当前未开放"; Pill(t, if (closed) pal.a(200) else pal.surface, if (closed) pal.a(800) else pal.n(800), fontSize = 12.sp) }
                    }
                }
            }
            item(key = "game") {
                DexCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Kicker("游戏说明 · CHAMPIONS")
                    GameText(move.description)
                    if (move.accuracy !in 1..100) Text("命中为游戏特殊值，具体判定请参阅效果说明。", fontSize = 11.sp, color = pal.n(700))
                    if (move.power == 1) Text("威力随条件变化，以效果说明为准。", fontSize = 11.sp, color = pal.n(700))
                }
            }
            wikiSection(move.key, dex, onOpen)
            item(key = "usersHead") { SectionHeading("可学会的宝可梦 · ${users.size}") }
            if (users.isEmpty()) item(key = "noUsers") { Text("学习表中没有关联的宝可梦。", fontSize = 13.sp, color = pal.n(700)) }
            pokemonGrid(users, onOpen)
        }
    }
}

@Composable
fun AbilityDetail(ability: Ability, dex: Dex, model: DexViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val holders = dex.abilityUsers[ability.id].orEmpty()
    Column(Modifier.fillMaxSize()) {
        AppHeader("特性详情", ability.name, false, onBack) { FavoriteButton(ability.key in model.favorites, { model.toggleFavorite(ability.key) }) }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().clip(CardShape).background(pal.g(200))) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 24.dp, y = 50.dp).size(150.dp).clip(CircleShape).background(pal.g(300)))
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row { Pill("特性", pal.g(800), pal.bg, fontSize = 12.sp) }
                        Text(ability.name, style = MaterialTheme.typography.headlineLarge, color = pal.g(900))
                    }
                }
            }
            item(key = "game") {
                DexCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Kicker("游戏说明 · CHAMPIONS")
                    GameText(ability.description)
                }
            }
            wikiSection(ability.key, dex, onOpen)
            item(key = "holdersHead") { SectionHeading("拥有此特性的宝可梦 · ${holders.size}") }
            if (holders.isEmpty()) item(key = "noHolders") { Text("没有关联的宝可梦。", fontSize = 13.sp, color = pal.n(700)) }
            pokemonGrid(holders, onOpen)
        }
    }
}
