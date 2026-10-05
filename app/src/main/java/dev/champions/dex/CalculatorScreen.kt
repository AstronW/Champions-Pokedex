package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

/** KO summary from the damage range against current HP. */
internal fun koText(min: Int, max: Int, hp: Int, ohko: Double): String = when {
    max <= 0 -> "没有造成伤害"
    min >= hp -> "确定一击击倒"
    max >= hp -> "${if (ohko % 1.0 == 0.0) ohko.toInt().toString() else ohko.toString()}% 几率一击击倒"
    else -> { val fast = ceil(hp / max.toDouble()).toInt(); val slow = if (min > 0) ceil(hp / min.toDouble()).toInt() else -1
        if (fast == slow) "确定 $fast 发击倒" else if (slow < 0) "至少 $fast 发击倒" else "$fast~$slow 发击倒" }
}

@Composable
fun CalculatorScreen(dex: Dex, model: DexViewModel, modifier: Modifier = Modifier, initialId: String? = null,
                     onNav: () -> Unit, isRoot: Boolean, onOpen: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(defaultCalculation(dex, initialId)) }
    var allMoves by rememberSaveable { mutableStateOf(false) }
    var showRolls by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableIntStateOf(0) }
    val request = remember(input) { JSONObject(input) }
    val catalog by produceState<CalculatorCatalog?>(null, model) {
        value = model.loadCalculatorCatalog()
    }
    var output by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    val calculationInput = input
    LaunchedEffect(calculationInput) {
        if (request.isNull("move")) { output = null; return@LaunchedEffect }
        delay(60)
        val result = withContext(Dispatchers.Default) {
            try { JSONObject(model.calculatorEngine.calculate(calculationInput)) }
            catch (_: Exception) { JSONObject().put("error", "计算器加载失败，请重新打开页面。") }
        }
        output = calculationInput to result
    }
    val result = output?.takeIf { it.first == input }?.second
    fun edit(block: (JSONObject) -> Unit) { input = JSONObject(input).also(block).toString() }
    fun editSide(key: String, block: (JSONObject) -> Unit) = edit { block(it.getJSONObject(key)) }
    fun swap() {
        edit(::swapCalculatorSides)
        allMoves = true
    }
    val attacker = dex.pokemonById[request.getJSONObject("attacker").getString("id")]!!
    val defender = dex.pokemonById[request.getJSONObject("defender").getString("id")]!!
    val selectedMove = dex.movesById[request.optInt("move", -1)]
    val learned = remember(dex, attacker.id) { dex.learnsets[attacker.id].orEmpty().toSet() }
    val moves = remember(dex, learned, allMoves) { dex.attackingMoves.filter { allMoves || it.id in learned } }
    val moveOptions = remember(moves) { moves.map { SelectOption(it.id.toString(), it.name, "${it.english} · ${it.categoryLabel} · 威力 ${it.powerLabel}") } }
    val physical = selectedMove?.category != 1
    val pokemonOptions = remember(dex) { dex.pokemon.map { SelectOption(it.id, it.displayName, "${it.number()} · ${it.english} ${it.formEnglish}") } }
    fun choose(key: String, id: String, preserveSpread: Boolean = false) = edit { q ->
        selectCalculatorPokemon(q, dex, key, id, preserveSpread, allMoves)
    }
    val compact = LocalConfiguration.current.screenHeightDp < 500
    Column(modifier.fillMaxSize()) {
        AppHeader("Lv.50 · 能力点 $PointTotal", "伤害计算", isRoot, onNav) {
            SecondaryButton("交换", ::swap, height = 40.dp, leading = { Glyph(GlyphKind.SWAP, Modifier.size(16.dp), pal.text) })
        }
        val resultCard: @Composable () -> Unit = { CalcResult(attacker, defender, selectedMove, moves.isEmpty(), result, compact, model.calculatorEngine.ready) }
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f).testTag("calculator-list"), state = rememberLazyListState(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "attacker") {
                    SideCard("攻击方", pal.a(700), "attacker", attacker, request.getJSONObject("attacker"), result?.optJSONObject("attackerStats"), physical, dex, catalog, pokemonOptions,
                        { change -> editSide("attacker", change) }, { choose("attacker", it) }, { choose("attacker", it, true) }, { onOpen(attacker.key) })
                }
                item(key = "move") {
                    DexCard(color = pal.a(100), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        SelectField("招式", selectedMove?.name ?: "未选择攻击招式", moveOptions,
                            enabled = moves.isNotEmpty(), bold = true, tag = "pick-move") { edit { q -> q.put("move", it.toInt()); q.put("hits", 0) } }
                        if (selectedMove != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypeTag(selectedMove.type, vertical = 3.dp, horizontal = 10.dp)
                            Pill("${selectedMove.categoryLabel} · 威力 ${selectedMove.powerLabel}", pal.bg, pal.n(800))
                            if (selectedMove.id !in learned) Pill("假设组合", pal.a(200), pal.a(800))
                        }
                        if (selectedMove != null && selectedMove.id !in learned) Text("此招式不在当前形态的学习列表中，作为假设组合计算。", fontSize = 11.sp, color = pal.a(700))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToggleTile("全部开放攻击招式", allMoves, {
                                allMoves = !allMoves
                                if (!allMoves) edit { limitToLearnedMoves(it, dex, attacker.id) }
                            }, Modifier.weight(1f))
                            val hits = catalog?.moveHits?.get(selectedMove?.id)
                            if (hits != null && hits.last > 1) {
                                val options = listOf(SelectOption("0", "自动（招式 / 特性默认）")) + hits.map { SelectOption(it.toString(), "$it 次") }
                                SelectField(null, if (request.getInt("hits") == 0) "命中次数 · 自动" else "命中 ${request.getInt("hits")} 次", options, Modifier.weight(1f)) { v -> edit { it.put("hits", v.toInt()) } }
                            }
                        }
                    }
                }
                item(key = "defender") {
                    SideCard("防守方", pal.g(700), "defender", defender, request.getJSONObject("defender"), result?.optJSONObject("defenderStats"), physical, dex, catalog, pokemonOptions,
                        { change -> editSide("defender", change) }, { choose("defender", it) }, { choose("defender", it, true) }, { onOpen(defender.key) })
                }
                item(key = "field") { FieldCard(request, physical) { block -> edit(block) } }
                if (result != null && !result.has("error")) item(key = "rolls") {
                    DexCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), spacing = 6.dp) {
                        Row(Modifier.fillMaxWidth().clickable { showRolls = !showRolls }, verticalAlignment = Alignment.CenterVertically) {
                            Kicker("伤害分布", modifier = Modifier.weight(1f)); Text(if (showRolls) "收起" else "展开", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.accent)
                        }
                        if (showRolls) {
                            val rows = result.getJSONArray("rolls")
                            repeat(rows.length()) { i -> Text("第 ${i + 1} 段：" + rows.getJSONArray(i).let { r -> (0 until r.length()).joinToString(" · ") { r.getInt(it).toString() } }, fontSize = 12.sp) }
                            Text("每段分别列出等概率随机值；多段伤害范围为各段范围之和。假定招式命中；不计气势披带、挺住与回合末效果。", fontSize = 11.sp, color = pal.n(700))
                        }
                    }
                }
                item(key = "more") {
                    DexCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { more = !more }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Kicker("更多条件")
                                Text("全部能力点与能力等级、HP、异常状态、场地状态", fontSize = 11.sp, color = pal.n(700))
                            }
                            Text(if (more) "收起" else "展开", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.accent)
                        }
                        if (more) {
                            PillSegment(listOf("攻击方", "防守方", "场地"), panel, { panel = it }, container = pal.bg, height = 32.dp, fontSize = 12.sp)
                            if (panel < 2) {
                                val key = if (panel == 0) "attacker" else "defender"
                                key(key) { AdvancedSide(if (panel == 0) attacker else defender, request.getJSONObject(key)) { change -> editSide(key, change) } }
                            } else AdvancedField(request.getJSONObject("field")) { block -> edit { block(it.getJSONObject("field")) } }
                        }
                    }
                }
                item(key = "footer") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("计算规则移植自 Smogon damage-calc 的 Champions 模式；种族值、属性、体重及招式数值使用本图鉴数据。", fontSize = 11.sp, color = pal.n(700))
                        Row { GhostButton("计算内核与开源许可") { onOpen("calculator-license") }; GhostButton("重置") { input = defaultCalculation(dex, initialId); allMoves = false } }
                    }
                }
                if (compact) item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
            }
            if (compact) Box(Modifier.width(320.dp).fillMaxHeight().padding(end = 18.dp, top = 6.dp, bottom = 12.dp)) { resultCard() }
        }
        if (!compact) Box(Modifier.fillMaxWidth().background(pal.bg).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)) { resultCard() }
    }
}

@Composable
private fun CalcResult(attacker: Pokemon, defender: Pokemon, move: Move?, noMoves: Boolean, result: JSONObject?, compact: Boolean, engineReady: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(pal.n(900)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        val e = move?.let { effectiveness(it.type, defender.types) } ?: 1.0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${attacker.displayName} → ${defender.displayName}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(100), maxLines = 2)
            if (move != null) {
                val label = when { e == 0.0 -> "无效"; e >= 4 -> "效果绝佳 ×4"; e >= 2 -> "效果绝佳 ×2"; e < 1 -> "效果不好 ×${if (e == .5) "½" else "¼"}"; else -> "效果一般" }
                Pill(label, pal.n(800), when { e > 1 -> pal.g(300); e < 1 -> pal.a(300); else -> pal.n(300) })
            }
        }
        when {
            move == null -> Text(if (noMoves) "此形态没有可用的攻击招式。可开启“全部开放攻击招式”选择假设招式。" else "请选择攻击招式。",
                Modifier.testTag("calc-no-move"), fontSize = 13.sp, color = pal.n(300))
            result == null -> { Text(if (engineReady) "正在计算…" else "正在准备计算内核…", fontSize = 13.sp, color = pal.n(300)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.a(400), trackColor = pal.n(700)) }
            result.has("error") -> Text(result.getString("error"), Modifier.testTag("calc-error"), fontSize = 13.sp, color = pal.a(300))
            else -> {
                val min = result.getInt("min"); val max = result.getInt("max"); val maxHp = result.getInt("maxHP"); val hp = result.getInt("currentHP")
                val lo = result.getDouble("percentMin"); val hi = result.getDouble("percentMax")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$lo – $hi%", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = if (compact) 26.sp else 30.sp, lineHeight = 32.sp, color = pal.n(100))
                    Text("$min–$max HP", Modifier.testTag("calc-damage").padding(bottom = 3.dp), fontSize = 12.5.sp, color = pal.n(400))
                }
                Text("对方当前 HP $hp / $maxHp · ${move.name}", fontSize = 11.sp, color = pal.n(400))
                val loF = (lo / 100).toFloat().coerceIn(0f, 1f); val hiF = (hi / 100).toFloat().coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth().height(12.dp).clip(PillShape).background(pal.n(700))) {
                    if (hiF < 1f) Box(Modifier.weight(1f - hiF).fillMaxHeight().background(pal.g(400)))
                    if (hiF > loF) Box(Modifier.weight(hiF - loF).fillMaxHeight().background(pal.a(400)))
                    if (loF > 0f) Spacer(Modifier.weight(loF))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(koText(min, max, hp, result.getDouble("ohko")), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pal.a(300))
                    Text("剩余 HP", fontSize = 11.sp, color = pal.n(400))
                }
            }
        }
    }
}

@Composable
private fun SideCard(title: String, titleColor: Color, key: String, p: Pokemon, side: JSONObject, stats: JSONObject?, physical: Boolean, dex: Dex, catalog: CalculatorCatalog?,
                     pokemonOptions: List<SelectOption>, onEdit: ((JSONObject) -> Unit) -> Unit, onSelect: (String) -> Unit, onForm: (String) -> Unit, onOpen: () -> Unit) {
    val attacking = key == "attacker"
    val statIndex = if (attacking) (if (physical) 1 else 3) else (if (physical) 2 else 4)
    DexCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clickable(onClickLabel = "查看图鉴", onClick = onOpen).semantics { contentDescription = "查看图鉴" }) { Blob(p, 60.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker(title, titleColor, spacing = 1.1.sp)
                SelectField(null, p.displayName, pokemonOptions, bold = true, container = pal.bg, tag = "pick-$key", onChoose = onSelect)
            }
        }
        val variants = dex.forms[p.no].orEmpty()
        if (variants.size > 1) PillSegment(variants.map { it.formLabel }, variants.indexOfFirst { it.id == p.id }, { onForm(variants[it].id) },
            container = pal.bg, height = 32.dp, fontSize = 12.sp, scrollable = variants.size > 3)
        if (catalog == null) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.accent, trackColor = pal.n(300)); return@DexCard }
        // Natures are chosen as two independent adjustments (raised / lowered stat). The engine still receives the
        // matching nature name; until both are chosen (and differ) the neutral nature applies.
        val (natPlus, natMinus) = catalog.natureEffects[side.getString("nature")] ?: ("atk" to "atk")
        val up = if (side.has("up")) side.getString("up") else if (natPlus != natMinus) natPlus else ""
        val down = if (side.has("down")) side.getString("down") else if (natPlus != natMinus) natMinus else ""
        fun adjust(newUp: String, newDown: String) = onEdit { s ->
            s.put("up", newUp).put("down", newDown).put("nature", catalog.natureFor(newUp, newDown))
        }
        fun adjustOptions(sign: String, exclude: String) = listOf(SelectOption("", "无")) +
            (1..5).map { calcStats[it] }.filter { it != exclude }.map { SelectOption(it, "$sign${StatLabels[calcStats.indexOf(it)]}") }
        fun label(sign: String, stat: String) = if (stat.isEmpty()) "无" else "$sign${StatLabels[calcStats.indexOf(stat)]}"
        val itemOptions = remember(catalog) { listOf(SelectOption("", "无道具")) + catalog.items.map { SelectOption(it.name, it.label, it.name) } }
        val abilityOptions = remember(dex, p.abilities) { p.abilities.distinct().mapNotNull { dex.abilitiesById[it] }.map { SelectOption(it.id.toString(), it.name, it.english) } }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("提升 ×1.1", label("+", up), adjustOptions("+", down), Modifier.weight(1f)) { adjust(it, down) }
                SelectField("降低 ×0.9", label("−", down), adjustOptions("−", up), Modifier.weight(1f)) { adjust(up, it) }
            }
            if ((up.isEmpty()) != (down.isEmpty())) Text("提升与降低需同时选择才会生效。", fontSize = 11.sp, color = pal.a(700))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("特性", dex.abilitiesById[side.getInt("ability")]?.name ?: "—", abilityOptions, Modifier.weight(1f)) { v -> onEdit { it.put("ability", v.toInt()) } }
                SelectField("道具", itemOptions.firstOrNull { it.id == side.getString("item") }?.label ?: "无道具", itemOptions, Modifier.weight(1f)) { v -> onEdit { it.put("item", v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("${StatLabels[statIndex]}变化", "%+d".format(side.getJSONArray("boosts").getInt(statIndex)).replace("+0", "±0"),
                    (6 downTo -6).map { SelectOption(it.toString(), if (it == 0) "±0" else "%+d".format(it)) }, Modifier.weight(1f)) { v -> onEdit { it.getJSONArray("boosts").put(statIndex, v.toInt()) } }
                Spacer(Modifier.weight(1f))
            }
        }
        fun natureFactor(stat: Int): Int {
            val (plus, minus) = natPlus to natMinus
            return when { plus == minus -> 100; calcStats[stat] == plus -> 110; calcStats[stat] == minus -> 90; else -> 100 }
        }
        fun actual(stat: Int) = stats?.optInt(calcStats[stat])?.takeIf { it > 0 } ?: actualStat(p.stats[stat], side.points(stat).coerceIn(0, 32), stat, natureFactor(stat))
        fun setPoints(stat: Int, value: Int) = onEdit { s ->
            val others = s.pointTotal() - s.points(stat)
            s.getJSONArray("sp").put(stat, value.coerceIn(0, minOf(32, PointTotal - others).coerceAtLeast(0)).toString())
        }
        if (!attacking) PointSlider("HP", side.points(0), actual(0)) { setPoints(0, it) }
        PointSlider(StatLabels[statIndex], side.points(statIndex), actual(statIndex)) { setPoints(statIndex, it) }
        Row {
            Text("每点 +1 实数值 · 单项上限 32", Modifier.weight(1f), fontSize = 11.sp, color = pal.n(700))
            val total = side.pointTotal()
            Text("合计 $total / $PointTotal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (total > PointTotal) pal.a(700) else pal.n(700))
        }
    }
}

@Composable
private fun PointSlider(stat: String, points: Int, actual: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$stat 能力点 ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
            Text("$points", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(" / 32", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
            Text("实数值 ", fontSize = 12.sp, color = pal.n(700)); Text("$actual", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slider(points.coerceIn(0, 32).toFloat(), { onChange(it.roundToInt()) }, Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "$stat 能力点" },
            valueRange = 0f..32f, steps = 31, colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent, inactiveTrackColor = pal.n(300),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
    }
}

private val weathers = listOf("" to "无", "Sun" to "晴天", "Rain" to "下雨", "Sand" to "沙暴", "Snow" to "下雪")
private val terrains = listOf("" to "无", "Electric" to "电气", "Grassy" to "青草", "Misty" to "薄雾", "Psychic" to "精神")

@Composable
private fun FieldCard(request: JSONObject, physical: Boolean, edit: ((JSONObject) -> Unit) -> Unit) {
    val field = request.getJSONObject("field")
    val defenderSide = field.optJSONObject("defenderSide") ?: JSONObject()
    DexCard(padding = PaddingValues(16.dp)) {
        Kicker("天气")
        PillSegment(weathers.map { it.second }, weathers.indexOfFirst { it.first == field.optString("weather") }.coerceAtLeast(0),
            { i -> edit { it.getJSONObject("field").put("weather", weathers[i].first) } }, container = pal.bg, height = 32.dp, fontSize = 12.sp)
        Kicker("场地")
        PillSegment(terrains.map { it.second }, terrains.indexOfFirst { it.first == field.optString("terrain") }.coerceAtLeast(0),
            { i -> edit { it.getJSONObject("field").put("terrain", terrains[i].first) } }, container = pal.bg, height = 32.dp, fontSize = 12.sp)
        Kicker("其他条件")
        val doubles = field.optString("gameType") == "Doubles"
        val burned = request.getJSONObject("attacker").optString("status") == "brn"
        val screen = defenderSide.optBoolean(if (physical) "isReflect" else "isLightScreen")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile("双打", doubles, { edit { it.getJSONObject("field").put("gameType", if (doubles) "Singles" else "Doubles") } }, Modifier.weight(1f))
            ToggleTile("会心", request.getBoolean("critical"), { edit { it.put("critical", !it.getBoolean("critical")) } }, Modifier.weight(1f))
            ToggleTile("灼伤", burned, { edit { it.getJSONObject("attacker").put("status", if (burned) "" else "brn") } }, Modifier.weight(1f))
            ToggleTile("墙壁", screen, { edit { q -> val f = q.getJSONObject("field"); val s = f.optJSONObject("defenderSide") ?: JSONObject()
                s.put(if (physical) "isReflect" else "isLightScreen", !screen); f.put("defenderSide", s) } }, Modifier.weight(1f))
        }
        Text("墙壁按当前招式分类设置防守方的反射壁或光墙。天气特性不会自动改写这里的天气与场地。", fontSize = 11.sp, color = pal.n(700))
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, change: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), maxLines = 1)
        BasicTextField(value, { if (it.length <= 3 && it.all(Char::isDigit)) change(it) },
            Modifier.fillMaxWidth().clip(PillShape).background(pal.bg).border(1.dp, pal.divider, PillShape).padding(horizontal = 14.dp, vertical = 9.dp)
                .semantics { contentDescription = label },
            singleLine = true, textStyle = TextStyle(color = pal.text, fontSize = 14.sp), cursorBrush = SolidColor(pal.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
private fun AdvancedSide(p: Pokemon, side: JSONObject, onEdit: ((JSONObject) -> Unit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("能力点合计 ${side.pointTotal()} / $PointTotal · 单项 0～32", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        calcStats.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i -> NumberField("${StatLabels[i]}能力点", side.getJSONArray("sp").getString(i), Modifier.weight(1f)) { v -> onEdit { it.getJSONArray("sp").put(i, v) } } }
            }
        }
        (1..5).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    SelectField("${StatLabels[i]}等级", "%+d".format(side.getJSONArray("boosts").getInt(i)).replace("+0", "±0"),
                        (6 downTo -6).map { SelectOption(it.toString(), if (it == 0) "±0" else "%+d".format(it)) }, Modifier.weight(1f)) { v -> onEdit { it.getJSONArray("boosts").put(i, v.toInt()) } }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("剩余 HP %（1～100）", side.getString("hp"), Modifier.weight(1f)) { v -> onEdit { it.put("hp", v) } }
            NumberField("已倒下队友（0～5）", side.getString("alliesFainted"), Modifier.weight(1f)) { v -> onEdit { it.put("alliesFainted", v) } }
        }
        val statuses = listOf(SelectOption("", "健康"), SelectOption("brn", "灼伤"), SelectOption("par", "麻痹"), SelectOption("psn", "中毒"), SelectOption("tox", "剧毒"), SelectOption("slp", "睡眠"), SelectOption("frz", "冰冻"))
        val genders = when (p.sex) { 1 -> listOf(SelectOption("M", "雄性")); 2 -> listOf(SelectOption("N", "无性别")); 3 -> listOf(SelectOption("F", "雌性")); else -> listOf(SelectOption("M", "雄性"), SelectOption("F", "雌性")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectField("状态", statuses.first { it.id == side.getString("status") }.label, statuses, Modifier.weight(1f)) { v -> onEdit { it.put("status", v) } }
            SelectField("性别", genders.firstOrNull { it.id == side.getString("gender") }?.label ?: "—", genders, Modifier.weight(1f)) { v -> onEdit { it.put("gender", v) } }
        }
        ToggleTile("特性条件已触发", side.getBoolean("abilityOn"), { onEdit { it.put("abilityOn", !it.getBoolean("abilityOn")) } }, Modifier.fillMaxWidth())
        Text("条件开关用于引火等特性；威吓等入场效果由内核按双方特性自动处理。实数值为能力调整与能力点结算后的基础能力。", fontSize = 11.sp, color = pal.n(700))
    }
}

@Composable
private fun AdvancedField(field: JSONObject, edit: ((JSONObject) -> Unit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("isGravity" to "重力", "isWonderRoom" to "奇妙空间", "isMagicRoom" to "魔法空间").forEach { (key, label) ->
                ToggleTile(label, field.optBoolean(key), { edit { it.put(key, !it.optBoolean(key)) } }, Modifier.weight(1f))
            }
        }
        listOf("attackerSide" to "攻击方场地", "defenderSide" to "防守方场地").forEach { (side, title) ->
            Kicker(title, modifier = Modifier.padding(top = 4.dp))
            listOf("isReflect" to "反射壁", "isLightScreen" to "光墙", "isAuroraVeil" to "极光幕", "isProtected" to "守住", "isTailwind" to "顺风",
                "isHelpingHand" to "帮助", "isFriendGuard" to "友情防守", "isBattery" to "蓄电池", "isPowerSpot" to "能量点", "isSteelySpirit" to "钢之意志",
                "isCharge" to "充电", "isFlowerGift" to "花之礼").chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, label) ->
                        val on = field.optJSONObject(side)?.optBoolean(key) == true
                        ToggleTile(label, on, { edit { f -> val s = f.optJSONObject(side) ?: JSONObject(); s.put(key, !on); f.put(side, s) } }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorLicense(onBack: () -> Unit) {
    val context = LocalContext.current
    val license by produceState("") { value = withContext(Dispatchers.IO) {
        listOf("LICENSE-damage-calc.txt", "LICENSE-Rhino.txt").joinToString("\n\n") { file -> context.assets.open("calculator/$file").bufferedReader().use { it.readText() } }
    } }
    Column {
        AppHeader("伤害计算", "计算内核与开源许可", false, onBack)
        LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 32.dp)) {
            item {
                Text("Smogon damage-calc · Champions\n版本 eb9af6d94e7e\n\n计算规则沿用该版本，不代表所有机制已经过 Champions 实机验证。\n\nRhino 1.7.15.1 · MPL 2.0", fontSize = 13.sp, lineHeight = 20.sp)
                GhostButton("Rhino 对应版本源码 ↗") { openWeb(context, "https://github.com/mozilla/rhino/tree/Rhino1_7_15_1_Release") }
                Text(license, fontSize = 12.sp, lineHeight = 17.sp, color = pal.n(800))
            }
        }
    }
}
