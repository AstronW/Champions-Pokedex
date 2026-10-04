package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// —— Type chart ——

private fun multiplierLabel(m: Double) = when (m) { 2.0 -> "效果绝佳 ×2"; .5 -> "效果不好 ×½"; 0.0 -> "没有效果 ×0"; else -> "效果一般 ×1" }

@Composable
fun TypeChartScreen(onMenu: () -> Unit) {
    var attack by rememberSaveable { mutableIntStateOf(9) }
    var defend by rememberSaveable { mutableStateOf<Int?>(null) }
    fun lit(at: Int, dt: Int?) = at == attack || (dt != null && dt == defend)
    Column(Modifier.fillMaxSize()) {
        AppHeader("18 × 18", DexRoot.TYPES.title, true, onMenu)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("行 = 攻击属性，列 = 防守属性。点击格子高亮所在行与列，点击行首只看该攻击属性。", Modifier.fillMaxWidth().padding(horizontal = 4.dp), fontSize = 12.sp, color = pal.n(700)) }
            item {
                Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(24.dp)).background(pal.surface).padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        Spacer(Modifier.width(20.dp))
                        TypeNames.indices.forEach { dt ->
                            Text(TypeNames[dt].take(1), Modifier.weight(1f).alpha(if (defend == null || defend == dt) 1f else .45f)
                                .clickable(role = Role.RadioButton, onClickLabel = "防守 ${TypeNames[dt]}") { defend = dt },
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = typeTone(dt).fg, textAlign = TextAlign.Center)
                        }
                    }
                    TypeNames.indices.forEach { at ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            Text(TypeNames[at].take(1), Modifier.width(20.dp).alpha(if (at == attack) 1f else .45f)
                                .clickable(role = Role.RadioButton, onClickLabel = "攻击 ${TypeNames[at]}") { attack = at; defend = null },
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = typeTone(at).fg, textAlign = TextAlign.Center)
                            TypeNames.indices.forEach { dt ->
                                val m = effectiveness(at, dt)
                                val (bg, fg, text) = when (m) {
                                    2.0 -> Triple(pal.g(400), pal.text, "2"); .5 -> Triple(pal.a(300), pal.text, "½")
                                    0.0 -> Triple(pal.n(800), pal.n(100), "0"); else -> Triple(pal.n(100), pal.text, "")
                                }
                                val selected = at == attack && dt == defend
                                Box(Modifier.weight(1f).aspectRatio(1f).alpha(if (lit(at, dt)) 1f else .3f).clip(RoundedCornerShape(5.dp)).background(bg)
                                    .then(if (selected) Modifier.border(2.dp, pal.text, RoundedCornerShape(5.dp)) else Modifier)
                                    .clickable(onClickLabel = "${TypeNames[at]} 攻击 ${TypeNames[dt]}") { attack = at; defend = dt },
                                    contentAlignment = Alignment.Center) {
                                    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = fg)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(pal.g(400) to "×2", pal.a(300) to "×½", pal.n(800) to "×0").forEach { (c, l) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c)); Text(l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                        }
                    }
                }
            }
            item {
                DexCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), spacing = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeTag(attack, fontSize = 13.sp, horizontal = 14.dp, vertical = 4.dp); Text("属性详情", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                    }
                    defend?.let { dt ->
                        Row(Modifier.fillMaxWidth().clip(PillShape).background(pal.bg).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypeTag(attack); Text("→", fontSize = 13.sp, color = pal.n(700)); TypeTag(dt)
                            Text(multiplierLabel(effectiveness(attack, dt)), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        }
                    }
                    val all = TypeNames.indices
                    listOf("攻击时 · 效果绝佳 ×2" to all.filter { effectiveness(attack, it) == 2.0 },
                        "攻击时 · 效果不好 ×½" to all.filter { effectiveness(attack, it) == .5 },
                        "攻击时 · 无效 ×0" to all.filter { effectiveness(attack, it) == 0.0 },
                        "防守时 · 弱点" to all.filter { effectiveness(it, attack) == 2.0 },
                        "防守时 · 抵抗 / 免疫" to all.filter { effectiveness(it, attack) < 1.0 }).forEach { (label, list) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                list.forEach { TypeTag(it, horizontal = 10.dp, vertical = 3.dp) }
                                if (list.isEmpty()) Text("无", fontSize = 12.sp, color = pal.n(600))
                            }
                        }
                    }
                }
            }
        }
    }
}

// —— Speed tiers ——

private fun speedOf(p: Pokemon, mode: Int) = when (mode) { 0 -> actualStat(p.stats[5], 0, 5); 1 -> actualStat(p.stats[5], 32, 5); else -> actualStat(p.stats[5], 32, 5, 110) }

@Composable
fun SpeedScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var me by rememberSaveable { mutableStateOf(model.team.firstOrNull { it != null } ?: dex.species.first().id) }
    var mode by rememberSaveable { mutableIntStateOf(2) }
    var scarf by rememberSaveable { mutableStateOf(false) }
    var tailwind by rememberSaveable { mutableStateOf(false) }
    val mine = dex.pokemonById[me] ?: dex.pokemon.first()
    val myValue = speedOf(mine, mode).let { if (scarf) it * 3 / 2 else it }.let { if (tailwind) it * 2 else it }
    val rows = remember(dex, me, mode, myValue) {
        dex.pokemon.map { it to if (it.id == mine.id) myValue else speedOf(it, mode) }.sortedByDescending { it.second }
    }
    val fastest = rows.first().second.coerceAtLeast(1)
    val faster = rows.count { it.first.id != mine.id && it.second < myValue }
    val tie = rows.count { it.first.id != mine.id && it.second == myValue }
    val options = remember(dex) { dex.pokemon.map { SelectOption(it.id, it.displayName, "${it.number()} · ${it.english} · 速度 ${it.stats[5]}") } }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Lv.50 实数值", DexRoot.SPEED.title, true, onMenu)
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "controls") {
                DexCard(Modifier.padding(bottom = 6.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    SelectField("我的宝可梦", mine.displayName, options, bold = true) { me = it }
                    PillSegment(listOf("无振", "准速 32", "最速 32+"), mode, { mode = it }, container = pal.bg)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleTile("讲究围巾 ×1.5", scarf, { scarf = !scarf }, Modifier.weight(1f))
                        ToggleTile("顺风 ×2", tailwind, { tailwind = !tailwind }, Modifier.weight(1f))
                        ToggleTile("重置", false, { scarf = false; tailwind = false }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("实数值 ", fontSize = 13.sp, color = pal.n(800))
                        Text("$myValue", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 22.sp)
                        Text(" · 快于 $faster 只${if (tie > 0) "，同速 $tie 只" else ""}", fontSize = 13.sp, color = pal.n(800))
                    }
                }
            }
            items(rows, key = { it.first.id }) { (p, v) ->
                val isMe = p.id == mine.id
                Row(Modifier.fillMaxWidth().clip(PillShape).background(if (isMe) pal.a(200) else pal.bg).clickable { onOpen(p.key) }.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Blob(p, 40.dp)
                    Text(p.displayName, Modifier.width(92.dp), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Bar(v / fastest.toFloat(), when { isMe -> pal.accent; v > myValue -> pal.n(400); v == myValue -> pal.a(300); else -> pal.g(400) }, Modifier.weight(1f))
                    Text("$v", Modifier.width(40.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
        }
    }
}

// —— Team builder ——

@Composable
fun TeamScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val members = model.team.mapNotNull { id -> id?.let { dex.pokemonById[it] } }
    Column(Modifier.fillMaxSize()) {
        AppHeader("双打 · 6 只", DexRoot.TEAM.title, true, onMenu)
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("我的队伍 · ${members.size} / 6", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                    Text("点击空位添加", fontSize = 12.sp, color = pal.n(700))
                }
            }
            item(key = "slots") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.team.withIndex().chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (i, id) -> TeamSlot(id?.let { dex.pokemonById[it] }, Modifier.weight(1f), { picking = i }, { model.setTeamSlot(i, null) }, onOpen) }
                        }
                    }
                }
            }
            item(key = "analysis") { TeamAnalysis(members) }
        }
    }
    picking?.let { slot -> PokemonPickerSheet("添加到队伍", dex, { model.setTeamSlot(slot, it.id); picking = null }) { picking = null } }
}

@Composable
private fun TeamSlot(p: Pokemon?, modifier: Modifier, onAdd: () -> Unit, onRemove: () -> Unit, onOpen: (String) -> Unit) {
    if (p == null) {
        val dash = pal.n(400)
        Column(modifier.heightIn(min = 132.dp).clip(RoundedCornerShape(30.dp)).drawBehind {
            drawRoundRect(dash, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))), cornerRadius = CornerRadius(30.dp.toPx()))
        }.clickable(role = Role.Button, onClickLabel = "添加", onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Glyph(GlyphKind.PLUS, Modifier.size(22.dp), pal.n(700)); Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        }
    } else Box(modifier.height(132.dp).clip(RoundedCornerShape(30.dp)).background(typeTone(p.types.first()).blob).clickable { onOpen(p.key) }) {
        Column(Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PokemonArt(p, Modifier.fillMaxWidth().weight(1f))
            Text(p.displayName, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { p.types.distinct().forEach { Box(Modifier.size(10.dp).clip(CircleShape).background(typeTone(it).solid)) } }
        }
        RoundIconButton("移除", onRemove, Modifier.align(Alignment.TopEnd).padding(6.dp).background(pal.bg, CircleShape), size = 26.dp) {
            Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), pal.n(700))
        }
    }
}

/** Team coverage in the same layout as a Pokémon's matchup card; the number after a type is how many members it hits. */
@Composable
private fun TeamAnalysis(members: List<Pokemon>) {
    val weak = TypeNames.indices.associateWith { t -> members.count { effectiveness(t, it.types) > 1 } }
    val resist = TypeNames.indices.associateWith { t -> members.count { effectiveness(t, it.types) < 1 } }
    val shared = TypeNames.indices.filter { weak.getValue(it) >= 2 && weak.getValue(it) > resist.getValue(it) }
    val count = { m: Map<Int, Int> -> { t: Int -> " ${m.getValue(t)}" } }
    TypeGroupsCard("队伍属性分析", listOf(
        TypeGroup("共同弱点", pal.a(800), shared, count(weak)),
        TypeGroup("弱点", pal.a(700), TypeNames.indices.filter { weak.getValue(it) > 0 && it !in shared }.sortedByDescending { weak.getValue(it) }, count(weak)),
        TypeGroup("抵抗/免疫", pal.g(700), TypeNames.indices.filter { resist.getValue(it) > 0 }.sortedByDescending { resist.getValue(it) }, count(resist)),
        TypeGroup("无人抵抗", pal.n(800), TypeNames.indices.filter { resist.getValue(it) == 0 && members.isNotEmpty() }),
    ), "类型后的数字为命中弱点或被抵抗的成员数。共同弱点：两只以上成员怕且抵抗者更少。按属性相性计算，不含特性与道具。",
        empty = "添加成员后显示队伍的弱点与抵抗。")
}

// —— Favorites and compare ——

@Composable
fun FavoritesScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    val favPokemon = dex.pokemon.filter { it.key in model.favorites }
    val favMoves = dex.moves.filter { it.key in model.favorites }
    val favAbilities = dex.abilities.filter { it.key in model.favorites }
    var left by rememberSaveable { mutableStateOf(favPokemon.getOrNull(0)?.id ?: "0003001") }
    var right by rememberSaveable { mutableStateOf(favPokemon.getOrNull(1)?.id ?: "0006000") }
    var picking by rememberSaveable { mutableIntStateOf(-1) }
    val a = dex.pokemonById[left] ?: dex.pokemon.first()
    val b = dex.pokemonById[right] ?: dex.pokemon.last()
    Column(Modifier.fillMaxSize()) {
        AppHeader("我的", DexRoot.FAVORITES.title, true, onMenu)
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "favHead") { SectionHeading("收藏 · ${favPokemon.size}") }
            item(key = "favRow") {
                if (favPokemon.isEmpty()) Text("在图鉴中点击星标收藏", fontSize = 13.sp, color = pal.n(700))
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favPokemon, key = { it.id }) { p ->
                        Column(Modifier.width(86.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpen(p.key) }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Blob(p, 76.dp)
                            Text(p.displayName, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
            item(key = "cmpHead") { SectionHeading("种族值对比") }
            item(key = "cmpPick") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparePick(a, pal.accent, pal.a(100), pal.a(900), Modifier.weight(1f)) { picking = 0 }
                    ComparePick(b, pal.accent2, pal.g(100), pal.g(900), Modifier.weight(1f)) { picking = 1 }
                }
            }
            item(key = "cmp") {
                DexCard(padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
                    StatLabels.forEachIndexed { i, label ->
                        val x = a.stats[i]; val y = b.stats[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$x", Modifier.width(30.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (x > y) pal.a(700) else pal.n(700))
                            Bar(x / 160f, if (x >= y) pal.accent else pal.n(400), Modifier.weight(1f), fromEnd = true)
                            Text(label, Modifier.width(44.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), textAlign = TextAlign.Center)
                            Bar(y / 160f, if (y >= x) pal.g(600) else pal.n(400), Modifier.weight(1f))
                            Text("$y", Modifier.width(30.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (y > x) pal.g(700) else pal.n(700), textAlign = TextAlign.End)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${a.total}", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 20.sp, color = pal.a(700))
                        Text("总和", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), textAlign = TextAlign.Center)
                        Text("${b.total}", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 20.sp, color = pal.g(700))
                    }
                }
            }
            if (favMoves.isNotEmpty()) {
                item(key = "movesHead") { SectionHeading("收藏的招式 · ${favMoves.size}") }
                items(favMoves, key = { "m${it.id}" }) { m -> MoveRow(m, true) { onOpen(m.key) } }
            }
            if (favAbilities.isNotEmpty()) {
                item(key = "abHead") { SectionHeading("收藏的特性 · ${favAbilities.size}") }
                items(favAbilities, key = { "a${it.id}" }) { x -> AbilityRow(x.name, x.description, "${dex.abilityUsers[x.id].orEmpty().size} 只", onClick = { onOpen(x.key) }) }
            }
        }
    }
    if (picking >= 0) PokemonPickerSheet("选择对比对象", dex, { if (picking == 0) left = it.id else right = it.id; picking = -1 }) { picking = -1 }
}

@Composable
private fun ComparePick(p: Pokemon, border: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(PillShape).border(2.dp, border, PillShape).background(bg).clickable(onClickLabel = "更换对比对象", onClick = onClick)
        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(pal.bg), contentAlignment = Alignment.Center) { PokemonArt(p, Modifier.size(40.dp)) }
        Text(p.displayName, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 2)
    }
}
