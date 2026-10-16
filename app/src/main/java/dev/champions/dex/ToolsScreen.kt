package dev.champions.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.champions.dex.components.*

// —— Type chart ——

private fun multiplierLabel(m: Double) = when (m) { 2.0 -> "效果绝佳 ×2"; .5 -> "效果不好 ×½"; 0.0 -> "没有效果 ×0"; else -> "效果一般 ×1" }

@Composable
fun TypeChartScreen(onMenu: () -> Unit) {
    var attack by rememberSaveable { mutableStateOf<Int?>(null) }
    var defend by rememberSaveable { mutableStateOf<Int?>(null) }
    // Tapping the current choice again (cell, row or column header) clears it.
    val pick = { at: Int?, dt: Int? ->
        if ((at == null || at == attack) && dt == defend) { if (at != null) attack = null; defend = null }
        else { if (at != null) attack = at; defend = dt }
    }
    val clear = { attack = null; defend = null }
    val hint = "行 = 攻击属性，列 = 防守属性。点击格子高亮所在行与列，点击行首只看该攻击属性；再次点击取消。"
    val matrix: @Composable () -> Unit = {
        TypeMatrix(attack, defend, Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(24.dp)).background(pal.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp), pick)
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("", DexRoot.TYPES.title, true, onMenu)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A wide, short window cannot show the chart and its results one above the other, so the results take their own column.
            val chartPane = minOf(560.dp, maxWidth * .6f)
            if (maxWidth > maxHeight && maxWidth >= 600.dp) Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.width(chartPane).fillMaxHeight().testTag("type-chart-list"),
                    contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { matrix() }
                    item { TypeLegend(attack != null || defend != null, clear) }
                    item { Text(hint, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = pal.n(700)) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("type-result-list"),
                    contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    attack?.let { at -> defend?.let { dt -> item { Matchup(at, dt) } } }
                    attack?.let { at -> item { TypeDetailCard(at) } }
                    if (attack == null) item {
                        DexCard(spacing = 6.dp) {
                            Kicker("属性详情")
                            Text(defend?.let { "已选防守属性 ${TypeNames[it]}，再点一行查看攻防组合。" } ?: "点击表格中的格子或行首，查看攻防组合与属性详情。",
                                fontSize = 13.sp, lineHeight = 20.sp, color = pal.n(800))
                        }
                    }
                }
            } else LazyColumn(Modifier.fillMaxSize().testTag("type-chart-list"), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item { Text(hint, Modifier.fillMaxWidth().padding(horizontal = 4.dp), fontSize = 12.sp, color = pal.n(700)) }
                item { matrix() }
                item { TypeLegend(attack != null || defend != null, clear) }
                // The chosen matchup sits right under the chart, so a tap answers without scrolling to the card.
                attack?.let { at -> defend?.let { dt -> item { Matchup(at, dt) } } }
                attack?.let { at -> item { TypeDetailCard(at) } }
            }
        }
    }
}

@Composable
private fun TypeLegend(selected: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(pal.g(400) to "×2", pal.a(300) to "×½", pal.n(800) to "×0").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c)); Text(l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
            }
        }
        Spacer(Modifier.weight(1f))
        if (selected) GhostButton("清除选择", onClear)
    }
}

@Composable
private fun Matchup(attack: Int, defend: Int) {
    Row(Modifier.fillMaxWidth().clip(PillShape).background(pal.surface).padding(horizontal = 12.dp, vertical = 8.dp).testTag("type-matchup"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TypeTag(attack); Text("→", fontSize = 13.sp, color = pal.n(700)); TypeTag(defend)
        Text(multiplierLabel(effectiveness(attack, defend)), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun TypeDetailCard(attack: Int) {
    DexCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeTag(attack, fontSize = 13.sp, icon = 22.dp, horizontal = 14.dp, vertical = 4.dp); Text("属性详情", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
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

/**
 * The 18×18 chart drawn on one canvas, with type icons as row and column headers. As separate composables each cell
 * carried its own clip, alpha layer, click handler and text, and scrolling the page recomposed and re-laid-out all 324 of them.
 * [onPick] receives (attack, defend): a row header picks only the attack, a column header only the defence.
 */
@Composable
private fun TypeMatrix(attack: Int?, defend: Int?, modifier: Modifier, onPick: (Int?, Int?) -> Unit) {
    val n = TypeNames.size
    val measurer = rememberTextMeasurer()
    val iconBgs = TypeNames.indices.map { typeIconColors(it).first }; val iconFilter = typeIconFilter()
    val icons = TypeNames.indices.map { rememberAsset("types/$it.png") }
    val colors = listOf(pal.g(400), pal.a(300), pal.n(800), pal.n(100))
    val text = pal.text; val light = pal.n(100)
    val cellStyle = TextStyle(fontFamily = BodyFont, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
    val marks = remember(measurer) { listOf("2", "½", "0").map { measurer.measure(it, cellStyle) } }
    val pick by rememberUpdatedState(onPick)
    val summary = when {
        attack != null && defend != null -> "${TypeNames[attack]} 攻击 ${TypeNames[defend]}：${multiplierLabel(effectiveness(attack, defend))}"
        attack != null -> "已选攻击属性 ${TypeNames[attack]}"
        defend != null -> "已选防守属性 ${TypeNames[defend]}"
        else -> "属性克制表，点击格子查看攻防组合"
    }
    BoxWithConstraints(modifier) {
        val gap = 1.5.dp; val head = 20.dp
        val cell = (maxWidth - head - gap * n) / n
        // Header icons are as wide as a cell (at most the header column), the same size along both axes.
        val icon = minOf(cell, head)
        val headRow = icon + 2.dp
        Canvas(Modifier.size(maxWidth, headRow + (cell + gap) * n).semantics { contentDescription = summary }.testTag("type-matrix")
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val g = gap.toPx(); val c = cell.toPx(); val step = c + g
                    val col = ((o.x - head.toPx() - g) / step).toInt().takeIf { o.x > head.toPx() + g && it < n }
                    val row = ((o.y - headRow.toPx() - g) / step).toInt().takeIf { o.y > headRow.toPx() + g && it < n }
                    when {
                        row != null && col != null -> pick(row, col)
                        row != null -> pick(row, null)
                        col != null -> pick(null, col)
                    }
                }
            }) {
            val g = gap.toPx(); val c = cell.toPx(); val step = c + g; val x0 = head.toPx() + g; val y0 = headRow.toPx() + g
            val radius = CornerRadius(5.dp.toPx())
            fun DrawScope.centered(layout: TextLayoutResult, cx: Float, cy: Float, color: androidx.compose.ui.graphics.Color, alpha: Float) =
                drawText(layout, color, Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f), alpha)
            // Cut to a circle like every other type icon; a plain type-coloured dot stands in until the icon has loaded.
            fun DrawScope.typeIcon(t: Int, cx: Float, cy: Float, alpha: Float) {
                val r = icon.toPx() / 2
                val bitmap = icons[t] ?: return drawCircle(iconBgs[t], r, Offset(cx, cy), alpha)
                clipPath(Path().apply { addOval(Rect(Offset(cx, cy), r)) }) {
                    drawImage(bitmap, dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()), dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()), alpha = alpha, colorFilter = iconFilter)
                }
            }
            for (dt in 0 until n) typeIcon(dt, x0 + dt * step + c / 2, headRow.toPx() / 2, if (defend == null || defend == dt) 1f else .45f)
            for (at in 0 until n) {
                val y = y0 + at * step
                typeIcon(at, head.toPx() / 2, y + c / 2, if (attack == null || at == attack) 1f else .45f)
                for (dt in 0 until n) {
                    val x = x0 + dt * step
                    val lit = (attack == null && defend == null) || at == attack || dt == defend
                    val alpha = if (lit) 1f else .3f
                    val kind = when (effectiveness(at, dt)) { 2.0 -> 0; .5 -> 1; 0.0 -> 2; else -> 3 }
                    drawRoundRect(colors[kind], Offset(x, y), Size(c, c), radius, alpha = alpha)
                    if (kind < 3) centered(marks[kind], x + c / 2, y + c / 2, if (kind == 2) light else text, alpha)
                    if (at == attack && dt == defend) drawRoundRect(text, Offset(x + 1.dp.toPx(), y + 1.dp.toPx()), Size(c - 2.dp.toPx(), c - 2.dp.toPx()),
                        radius, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

// —— Speed tiers ——

private fun speedOf(p: Pokemon, mode: Int) = when (mode) { 0 -> actualStat(p.stats[5], 0, 5); 1 -> actualStat(p.stats[5], 32, 5); else -> actualStat(p.stats[5], 32, 5, 110) }

@Composable
fun SpeedScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var me by rememberSaveable { mutableStateOf(model.team.firstNotNullOfOrNull { it }?.let { dex.battleForm(it).id } ?: dex.species.first().id) }
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
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    // Item 0 is the controls card; three faster rows stay visible above mine for context.
    fun locate() = scope.launch {
        val target = (rows.indexOfFirst { it.first.id == mine.id } + 1 - 3).coerceAtLeast(1)
        if (reduceMotion) list.scrollToItem(target) else list.animateScrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Lv.50 实数值", DexRoot.SPEED.title, true, onMenu)
        LazyColumn(state = list, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "controls") {
                DexCard(Modifier.padding(bottom = 6.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    SelectField("我的宝可梦", mine.displayName, options, bold = true) { me = it }
                    PillSegment(listOf("无振", "准速 32", "最速 32+"), mode, { mode = it }, container = pal.bg)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleTile("讲究围巾 ×1.5", scarf, { scarf = !scarf }, Modifier.weight(1f))
                        ToggleTile("顺风 ×2", tailwind, { tailwind = !tailwind }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Value and ranking on separate lines, so large text wraps the ranking rather than splitting the number.
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("实数值 ", fontSize = 13.sp, color = pal.n(800))
                                Text("$myValue", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 22.sp)
                            }
                            Text("快于 $faster 只${if (tie > 0) "，同速 $tie 只" else ""}", fontSize = 13.sp, color = pal.n(800))
                        }
                        GhostButton("定位 ↓") { locate() }
                    }
                }
            }
            items(rows, key = { it.first.id }) { (p, v) ->
                val isMe = p.id == mine.id
                Row(Modifier.fillMaxWidth().clip(PillShape).background(if (isMe) pal.a(200) else pal.bg).clickable { onOpen(p.key) }.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Blob(p, 40.dp)
                    Text(p.displayName, Modifier.width(92.dp), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Bar(v / fastest.toFloat(), when { isMe -> pal.accent; v > myValue -> pal.n(400); v == myValue -> pal.a(300); else -> pal.g(400) }, Modifier.weight(1f))
                    Text("$v", Modifier.width(40.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
        }
    }
}

// —— Team builder ——

/** Team menus: add a Pokémon to an empty slot, replace a member (then return to its set), edit a member's set, or share the team. */
private const val TEAM_PICK = "pick:"
private const val TEAM_SWAP = "swap:"
private const val TEAM_SET = "set:"
private const val TEAM_SHARE = "share"

@Composable
fun TeamScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var menu by rememberMenuState()
    val sets = remember(dex, model.team) { model.team.map { m -> m?.let { MemberSet(dex, it) } } }
    val members = sets.filterNotNull()
    val close = { menu = "" }
    val panel = menu.takeIf { it.startsWith(TEAM_SET) || it == TEAM_SHARE }.orEmpty()
    BackHandler(panel.isNotEmpty(), close)
    Column(Modifier.fillMaxSize()) {
        AppHeader("双打 · 6 只", DexRoot.TEAM.title, true, onMenu) {
            if (members.isNotEmpty()) SecondaryButton("分享", { menu = TEAM_SHARE }, height = 40.dp, leading = { Glyph(GlyphKind.SHARE, Modifier.size(16.dp), pal.text) })
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "head") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("我的队伍 · ${members.size} / $TeamSize", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                        Text(if (members.isEmpty()) "点击空位添加" else "点击成员配置道具与招式", fontSize = 12.sp, color = pal.n(700))
                    }
                }
                item(key = "slots") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sets.withIndex().chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (i, set) ->
                                    TeamSlot(set, Modifier.weight(1f), { menu = "$TEAM_PICK$i" }, { menu = "$TEAM_SET$i" }, { model.setTeamMember(i, null) })
                                }
                            }
                        }
                    }
                }
                item(key = "analysis") { TeamAnalysis(members) }
                item(key = "coverage") { TeamCoverage(members) }
            }
            MenuHost(panel) { open ->
                if (open == TEAM_SHARE) TeamShareSheet(members, dex, close)
                else open.removePrefix(TEAM_SET).toInt().let { slot ->
                    sets[slot]?.let { set ->
                        MemberSheet(slot, set, dex, { model.setTeamMember(slot, it) }, { menu = "$TEAM_SWAP$slot" }, { key -> close(); onOpen(key) }, close)
                    }
                }
            }
        }
    }
    val slot = menu.substringAfter(':').toIntOrNull()
    if (slot != null && panel.isEmpty()) {
        val after = if (menu.startsWith(TEAM_SWAP)) "$TEAM_SET$slot" else ""
        PokemonPickerSheet(if (after.isEmpty()) "添加到队伍" else "更换成员", dex, { model.setTeamSlot(slot, it.id); menu = after }) { menu = after }
    }
}

@Composable
private fun TeamSlot(set: MemberSet?, modifier: Modifier, onAdd: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    if (set == null) {
        val dash = pal.n(400)
        Column(modifier.heightIn(min = 132.dp).clip(RoundedCornerShape(30.dp)).drawBehind {
            drawRoundRect(dash, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))), cornerRadius = CornerRadius(30.dp.toPx()))
        }.clickable(role = Role.Button, onClickLabel = "添加", onClick = onAdd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Glyph(GlyphKind.PLUS, Modifier.size(22.dp), pal.n(700)); Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        }
    } else Box(modifier.height(132.dp).clip(RoundedCornerShape(30.dp)).background(typeTone(set.form.types.first()).blob)
        .clickable(onClickLabel = "配置道具与招式", onClick = onEdit).testTag("team-slot")) {
        val p = set.form
        Column(Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PokemonArt(p, Modifier.fillMaxWidth().weight(1f))
            Text(p.displayName, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { p.types.distinct().forEach { TypeTag(it, showName = false, icon = 16.dp) } }
        }
        set.item?.let { item ->
            AssetImage("items/${item.sprite}.png", item.name, Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).background(pal.bg, CircleShape).padding(3.dp))
        }
        BadgeIconButton("移除", onRemove, Modifier.align(Alignment.TopEnd)) { Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), pal.n(700)) }
    }
}

/** A member's item, ability, stat adjustment and moves, with the matchups they produce. */
@Composable
private fun AnimatedVisibilityScope.MemberSheet(slot: Int, set: MemberSet, dex: Dex, onChange: (TeamMember) -> Unit, onSwap: () -> Unit,
                                                onOpen: (String) -> Unit, onClose: () -> Unit) {
    val m = set.member; val form = set.form
    val learnable = remember(dex, m.id) { dex.learnedMoves[m.id].orEmpty().filter { it.available } }
    val itemOptions = remember(dex, m.id) {
        listOf(SelectOption("", "无道具")) + dex.items.filter { it.holders.isEmpty() || m.id in it.holders }.map { SelectOption("${it.id}", it.name, it.english) }
    }
    val abilityOptions = remember(dex, m.id) {
        dex.pokemonById.getValue(m.id).abilities.distinct().mapNotNull { dex.abilitiesById[it] }.map { SelectOption("${it.id}", it.name, it.english) }
    }
    fun setMove(k: Int, id: Int?) = onChange(m.copy(moves = m.moves.toMutableList().apply {
        when { id == null -> if (k < size) removeAt(k); k < size -> this[k] = id; else -> add(id) }
    }))
    MenuPanel("队伍 · 第 ${slot + 1} 位", pal.a(700), form.displayName, true, onClose, actions = { GhostButton("查看图鉴") { onOpen(form.key) } }) {
        item(key = "member") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Blob(form, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(form.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { form.types.distinct().forEach { TypeTag(it, horizontal = 8.dp) } }
                }
                SecondaryButton("更换", onSwap, height = 40.dp)
            }
        }
        val variants = dex.forms[form.no].orEmpty()
        if (variants.size > 1) item(key = "forms") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetLabel("形态")
                PillSegment(variants.map { it.formLabel }, variants.indexOfFirst { it.id == form.id }, { onChange(dex.withForm(m, variants[it].id)) },
                    container = pal.surface, height = 34.dp, fontSize = 12.sp, scrollable = variants.size > 3)
            }
        }
        item(key = "basic") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField("特性", dex.abilitiesById[set.ability]?.name ?: "—", abilityOptions, Modifier.weight(1f), container = pal.n(100), enabled = !set.isMega) {
                        onChange(m.copy(ability = it.toInt()))
                    }
                    SelectField("道具", set.item?.name ?: "无道具", itemOptions, Modifier.weight(1f), container = pal.n(100), tag = "team-item") { onChange(m.copy(item = it.toIntOrNull())) }
                }
                if (set.isMega) Text("携带超级进化石，按超级进化后的属性与特性分析。", fontSize = 11.sp, color = pal.n(700))
                StatAdjustFields(m.up, m.down) { up, down -> onChange(m.copy(up = up, down = down)) }
            }
        }
        item(key = "moves") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until TeamMoveSlots).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k ->
                            val current = set.moves.getOrNull(k)
                            val options = listOf(SelectOption("", "无")) + learnable.filter { it.id == current?.id || it.id !in m.moves }
                                .map { SelectOption("${it.id}", it.name, "${TypeNames[it.type]} · ${it.categoryLabel} · 威力 ${it.powerLabel}") }
                            SelectField("招式 ${k + 1}", current?.name ?: "未选择", options, Modifier.weight(1f), container = pal.n(100), enabled = current != null || k == m.moves.size,
                                tag = "team-move-$k") { setMove(k, it.toIntOrNull()) }
                        }
                    }
                }
                set.attacks.filter { it.cause != null }.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(a.move.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TypeTag(a.move.type); Text("→", fontSize = 12.sp, color = pal.n(700)); TypeTag(a.type)
                        Text(a.cause.orEmpty(), fontSize = 11.sp, color = pal.n(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "taken") {
            val taken = TypeNames.indices.associateWith(set::taken)
            fun multiplier(t: Int) = " " + multiplierText(taken.getValue(t))
            TypeGroupsCard("受到的属性伤害", listOf(
                TypeGroup("弱点", pal.a(700), TypeNames.indices.filter { taken.getValue(it) > 1 }.sortedByDescending { taken.getValue(it) }, ::multiplier),
                TypeGroup("抵抗", pal.g(700), TypeNames.indices.filter { taken.getValue(it).let { v -> v > 0 && v < 1 } }.sortedBy { taken.getValue(it) }, ::multiplier),
                TypeGroup("免疫", pal.n(800), TypeNames.indices.filter { taken.getValue(it) == 0.0 }),
            ), "含特性与道具：气球按未被击破、抗性树果按生效一次计算。")
        }
        item(key = "hits") {
            val hits = TypeNames.indices.associateWith(set::hits)
            TypeGroupsCard("招式打击面", if (set.attacks.isEmpty()) emptyList() else listOf(
                TypeGroup("效果绝佳", pal.g(700), TypeNames.indices.filter { hits.getValue(it) > 1 }),
                TypeGroup("被抵抗", pal.a(700), TypeNames.indices.filter { hits.getValue(it).let { v -> v > 0 && v < 1 } }),
                TypeGroup("无效", pal.a(800), TypeNames.indices.filter { hits.getValue(it) == 0.0 }),
            ), "对单属性取各攻击招式的最高倍率，招式属性已按特性改变。", empty = "选择攻击招式后显示打击面。")
        }
    }
}

/** Team defence in the same layout as a Pokémon's matchup card; the number after a type is how many members it hits. */
@Composable
private fun TeamAnalysis(sets: List<MemberSet>) {
    val weak = TypeNames.indices.associateWith { t -> sets.count { it.taken(t) > 1 } }
    val resist = TypeNames.indices.associateWith { t -> sets.count { it.taken(t) < 1 } }
    val shared = TypeNames.indices.filter { weak.getValue(it) >= 2 && weak.getValue(it) > resist.getValue(it) }
    val count = { m: Map<Int, Int> -> { t: Int -> " ${m.getValue(t)}" } }
    TypeGroupsCard("队伍属性分析", listOf(
        TypeGroup("共同弱点", pal.a(800), shared, count(weak)),
        TypeGroup("弱点", pal.a(700), TypeNames.indices.filter { weak.getValue(it) > 0 && it !in shared }.sortedByDescending { weak.getValue(it) }, count(weak)),
        TypeGroup("抵抗/免疫", pal.g(700), TypeNames.indices.filter { resist.getValue(it) > 0 }.sortedByDescending { resist.getValue(it) }, count(resist)),
        TypeGroup("无人抵抗", pal.n(800), TypeNames.indices.filter { resist.getValue(it) == 0 && sets.isNotEmpty() }),
    ), "类型后的数字为命中弱点或被抵抗的成员数。共同弱点：两只以上成员怕且抵抗者更少。含特性与道具：气球按未被击破、抗性树果按生效一次计算，" +
        "携带超级进化石的成员按超级进化后的属性与特性计算。",
        empty = "添加成员后显示队伍的弱点与抵抗。")
}

/** Offensive coverage against single types; the number after a type is how many members hit it super effectively. */
@Composable
private fun TeamCoverage(sets: List<MemberSet>) {
    val attackers = sets.filter { it.attacks.isNotEmpty() }
    val best = TypeNames.indices.associateWith { t -> attackers.maxOfOrNull { it.hits(t) } ?: 0.0 }
    val strong = TypeNames.indices.associateWith { t -> attackers.count { it.hits(t) > 1 } }
    val unset = sets.size - attackers.size
    TypeGroupsCard("队伍打击面", if (attackers.isEmpty()) emptyList() else listOf(
        TypeGroup("效果绝佳", pal.g(700), TypeNames.indices.filter { strong.getValue(it) > 0 }.sortedByDescending { strong.getValue(it) }) { " ${strong.getValue(it)}" },
        TypeGroup("最多一般", pal.n(800), TypeNames.indices.filter { best.getValue(it) == 1.0 }),
        TypeGroup("均被抵抗", pal.a(700), TypeNames.indices.filter { best.getValue(it).let { v -> v > 0 && v < 1 } }),
        TypeGroup("无法命中", pal.a(800), TypeNames.indices.filter { best.getValue(it) == 0.0 }),
    ), "类型后的数字为能打出效果绝佳的成员数。按招式实际属性计算：冰冻皮肤、龙皮肤等特性会改变一般属性招式，携带超级进化石按超级进化后的特性计算；" +
        "固定伤害招式只计是否无效。" + if (unset > 0) "有 $unset 只成员未选择攻击招式。" else "",
        empty = if (sets.isEmpty()) "添加成员后显示队伍打击面。" else "为成员选择攻击招式后显示打击面。")
}

// —— Favorites and compare ——

@Composable
fun FavoritesScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    val favPokemon = dex.pokemon.filter { it.key in model.favorites }
    val favMoves = dex.moves.filter { it.key in model.favorites }
    val favAbilities = dex.abilities.filter { it.key in model.favorites }
    val favItems = dex.items.filter { it.key in model.favorites }
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
                        Column(Modifier.width(86.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen(p.key) }, horizontalAlignment = Alignment.CenterHorizontally,
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
            if (favItems.isNotEmpty()) {
                item(key = "itemHead") { SectionHeading("收藏的道具 · ${favItems.size}") }
                items(favItems, key = { "i${it.id}" }) { x -> AbilityRow(x.name, x.description, null, onClick = { onOpen(x.key) }, chevron = true) }
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
