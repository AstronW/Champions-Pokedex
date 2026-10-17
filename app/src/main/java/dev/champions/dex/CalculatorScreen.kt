@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.champions.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.AppHeader
import dev.champions.dex.components.Blob
import dev.champions.dex.components.EmptyResult
import dev.champions.dex.components.GhostButton
import dev.champions.dex.components.Glyph
import dev.champions.dex.components.GlyphKind
import dev.champions.dex.components.Kicker
import dev.champions.dex.components.MenuHost
import dev.champions.dex.components.MenuPanel
import dev.champions.dex.components.MoveTypeIcon
import dev.champions.dex.components.PanelShape
import dev.champions.dex.components.Pill
import dev.champions.dex.components.PillSegment
import dev.champions.dex.components.PillShape
import dev.champions.dex.components.SearchField
import dev.champions.dex.components.SecondaryButton
import dev.champions.dex.components.SelectField
import dev.champions.dex.components.SelectOption
import dev.champions.dex.components.SheetLabel
import dev.champions.dex.components.ToggleTile
import dev.champions.dex.components.TypeChoice
import dev.champions.dex.components.TypeTag
import dev.champions.dex.components.TypeTagColumn
import dev.champions.dex.components.TypeTagRow
import dev.champions.dex.components.categoryColors
import dev.champions.dex.components.number
import dev.champions.dex.components.rememberMenuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

/** KO summary from the damage range against current HP. */
internal fun koText(
    min: Int,
    max: Int,
    hp: Int,
    ohko: Double,
): String =
    when {
        max <= 0 -> {
            "没有造成伤害"
        }

        min >= hp -> {
            "确定一击击倒"
        }

        max >= hp -> {
            "${if (ohko % 1.0 == 0.0) ohko.toInt().toString() else ohko.toString()}% 几率一击击倒"
        }

        else -> {
            val fast = ceil(hp / max.toDouble()).toInt()
            val slow = if (min > 0) ceil(hp / min.toDouble()).toInt() else -1
            if (fast == slow) {
                "确定 $fast 发击倒"
            } else if (slow < 0) {
                "至少 $fast 发击倒"
            } else {
                "$fast~$slow 发击倒"
            }
        }
    }

private val weathers = listOf("" to "无", "Sun" to "晴天", "Rain" to "下雨", "Sand" to "沙暴", "Snow" to "下雪")
private val terrains = listOf("" to "无", "Electric" to "电气", "Grassy" to "青草", "Misty" to "薄雾", "Psychic" to "精神")
private val statuses = listOf("" to "健康", "brn" to "灼伤", "par" to "麻痹", "psn" to "中毒", "tox" to "剧毒", "slp" to "睡眠", "frz" to "冰冻")
private val InnerShape = RoundedCornerShape(16.dp)

/** Second-level menu names: Pokémon picker and set configuration for one side. */
private const val PICK = "pick:"
private const val CONFIG = "config:"

@Composable
fun CalculatorScreen(
    dex: Dex,
    model: DexViewModel,
    modifier: Modifier = Modifier,
    initialId: String? = null,
    onNav: () -> Unit,
    isRoot: Boolean,
    onOpen: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(defaultCalculation(dex, initialId)) }
    var allMoves by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberMenuState()
    val request = remember(input) { JSONObject(input) }
    val catalog by produceState<CalculatorCatalog?>(null, model) {
        value = model.loadCalculatorCatalog()
    }
    val engine = remember(dex, catalog) { catalog?.let { CalculatorEngine(dex, it) } }
    // The native engine takes well under a millisecond, so the result follows every edit directly.
    val result = remember(request, engine) { if (request.isNull("move")) null else engine?.calculate(request) }

    fun edit(block: (JSONObject) -> Unit) {
        input = JSONObject(input).also(block).toString()
    }

    fun editSide(
        key: String,
        block: (JSONObject) -> Unit,
    ) = edit { block(it.getJSONObject(key)) }

    fun swap() {
        edit(::swapCalculatorSides)
        allMoves = true
    }

    fun toggleAllMoves() {
        allMoves = !allMoves
        if (!allMoves) edit { limitToLearnedMoves(it, dex, request.getJSONObject("attacker").getString("id")) }
    }
    val attacker = dex.pokemonById[request.getJSONObject("attacker").getString("id")]!!
    val defender = dex.pokemonById[request.getJSONObject("defender").getString("id")]!!
    val selectedMove = dex.movesById[request.optInt("move", -1)]
    val learned = remember(dex, attacker.id) { dex.learnsets[attacker.id].orEmpty().toSet() }
    val noMoves = remember(dex, learned, allMoves) { dex.attackingMoves.none { allMoves || it.id in learned } }

    fun choose(
        key: String,
        id: String,
        preserveSpread: Boolean = false,
    ) = edit { q ->
        selectCalculatorPokemon(q, dex, key, id, preserveSpread, allMoves)
    }
    val close = { sheet = "" }

    // A detail opens above this page while it stays composed, so the menu closes first.
    fun navigate(key: String) {
        close()
        onOpen(key)
    }
    BackHandler(sheet.isNotEmpty(), close)
    val compact =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        } < 500.dp
    Column(modifier.fillMaxSize()) {
        AppHeader("Lv.50 · 能力点 $POINT_TOTAL", "伤害计算", isRoot, onNav) {
            SecondaryButton("交换", ::swap, height = 40.dp, leading = { Glyph(GlyphKind.SWAP, Modifier.size(16.dp), pal.text) })
        }
        val resultCard: @Composable (Modifier) -> Unit = { m ->
            CalcResult(attacker, defender, selectedMove, noMoves, result, compact, { sheet = "result" }, m)
        }
        // Menus rise over the result card as well, so nothing at the bottom of a menu sits behind it.
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight().testTag("calculator-list"),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "sides") {
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SideSummary(
                                    "攻击方",
                                    pal.a(700),
                                    "attacker",
                                    attacker,
                                    request.getJSONObject("attacker"),
                                    dex,
                                    catalog,
                                    compact,
                                    { sheet = PICK + "attacker" },
                                    { sheet = CONFIG + "attacker" },
                                    Modifier.weight(1f).fillMaxHeight(),
                                )
                                SideSummary(
                                    "防守方",
                                    pal.g(700),
                                    "defender",
                                    defender,
                                    request.getJSONObject("defender"),
                                    dex,
                                    catalog,
                                    compact,
                                    { sheet = PICK + "defender" },
                                    { sheet = CONFIG + "defender" },
                                    Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                        item(key = "field") { FieldSummary(request.getJSONObject("field")) { sheet = "field" } }
                        item(key = "move") { MoveSummary(selectedMove, learned, noMoves, request, compact) { sheet = "move" } }
                    }
                    if (compact) resultCard(Modifier.width(300.dp).fillMaxHeight())
                }
                if (!compact) resultCard(Modifier.fillMaxWidth())
            }
            MenuHost(sheet) { menu ->
                when {
                    menu.startsWith(PICK) -> {
                        val key = menu.removePrefix(PICK)
                        val p = if (key == "attacker") attacker else defender
                        PokemonSheet(
                            if (key == "attacker") "攻击方" else "防守方",
                            if (key == "attacker") pal.a(700) else pal.g(700),
                            p,
                            dex,
                            model.team.map { it?.let { m -> dex.battleForm(m).id } },
                            model.favorites,
                            {
                                choose(key, it)
                                close()
                            },
                            { choose(key, it, true) },
                            { navigate(p.key) },
                            close,
                        )
                    }

                    menu.startsWith(CONFIG) -> {
                        val key = menu.removePrefix(CONFIG)
                        val p = if (key == "attacker") attacker else defender
                        ConfigSheet(
                            if (key ==
                                "attacker"
                            ) {
                                "攻击方"
                            } else {
                                "防守方"
                            },
                            if (key == "attacker") pal.a(700) else pal.g(700),
                            p,
                            request.getJSONObject(key),
                            (result as? CalcDamage)?.let {
                                if (key ==
                                    "attacker"
                                ) {
                                    it.attackerStats
                                } else {
                                    it.defenderStats
                                }
                            },
                            dex,
                            catalog,
                            { change -> editSide(key, change) },
                            close,
                        )
                    }

                    menu == "field" -> {
                        FieldSheet(request.getJSONObject("field"), { block -> edit { block(it.getJSONObject("field")) } }, close)
                    }

                    menu == "move" -> {
                        MoveSheet(dex, attacker, defender, request, learned, allMoves, catalog, ::toggleAllMoves, ::edit, close)
                    }

                    menu == "result" -> {
                        ResultSheet(result, { navigate("calculator-license") }, {
                            input = defaultCalculation(dex, initialId)
                            allMoves =
                                false
                            close()
                        }, close)
                    }
                }
            }
        }
    }
}

// —— Result ——

@Composable
private fun CalcResult(
    attacker: Pokemon,
    defender: Pokemon,
    move: Move?,
    noMoves: Boolean,
    result: CalcOutcome?,
    compact: Boolean,
    onDetails: () -> Unit,
    modifier: Modifier,
) {
    val shape = if (compact) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else PanelShape
    Column(
        modifier
            .shadow(14.dp, shape)
            .clip(shape)
            .background(pal.n(100))
            .padding(start = 20.dp, end = 20.dp, top = if (compact) 18.dp else 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val e = move?.let { effectiveness(it.type, defender.types) } ?: 1.0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${attacker.displayName} → ${defender.displayName}" + (move?.let { " · ${it.name}" } ?: ""),
                Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = pal.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (move != null && !compact) EffectPill(e)
        }
        if (move != null && compact) EffectPill(e)
        when {
            move == null -> {
                Text(
                    if (noMoves) "此形态没有可用的攻击招式。可在招式菜单开启“全部开放攻击招式”选择假设招式。" else "请选择攻击招式。",
                    Modifier.testTag("calc-no-move"),
                    fontSize = 13.sp,
                    color = pal.n(800),
                )
            }

            result == null -> {
                Text("正在载入计算数据…", fontSize = 13.sp, color = pal.n(800))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.accent, trackColor = pal.surface)
            }

            result is CalcFailure -> {
                Text(result.message, Modifier.testTag("calc-error"), fontSize = 13.sp, color = pal.a(800))
            }

            result is CalcDamage -> {
                val min = result.min
                val max = result.max
                val maxHp = result.maxHP
                val hp = result.currentHP
                val lo = result.percentMin
                val hi = result.percentMax
                val percent = @Composable {
                    Text(
                        "$lo – $hi%",
                        fontFamily = HeadingFont,
                        fontWeight = HeadingWeight,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        color = pal.text,
                    )
                }
                val hpText = @Composable { align: TextAlign ->
                    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
                        Text(
                            "$min–$max HP",
                            Modifier.testTag("calc-damage"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = pal.text,
                        )
                        Text("对方 HP $hp / $maxHp", fontSize = 11.sp, color = pal.n(700))
                    }
                }
                if (compact) {
                    percent()
                    hpText(TextAlign.Start)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { percent() }
                        hpText(TextAlign.End)
                    }
                }
                val loF = (lo / 100).toFloat().coerceIn(0f, 1f)
                val hiF = (hi / 100).toFloat().coerceIn(0f, 1f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(PillShape)
                        .background(pal.surface),
                ) {
                    if (hiF < 1f) Box(Modifier.weight(1f - hiF).fillMaxHeight().background(pal.g(600)))
                    if (hiF > loF) Box(Modifier.weight(hiF - loF).fillMaxHeight().background(pal.g(300)))
                    if (loF > 0f) Spacer(Modifier.weight(loF))
                }
                if (compact) Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(koText(min, max, hp, result.ohko), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = pal.a(800))
                        Text("剩余 HP ${(hp - max).coerceAtLeast(0)}–${(hp - min).coerceAtLeast(0)}", fontSize = 11.sp, color = pal.n(700))
                    }
                    SecondaryButton("伤害分布", onDetails, height = 40.dp)
                }
                return@Column
            }
        }
        if (compact) Spacer(Modifier.weight(1f))
        Row {
            Spacer(Modifier.weight(1f))
            SecondaryButton("说明", onDetails, height = 40.dp)
        }
    }
}

@Composable
private fun EffectPill(e: Double) {
    val label =
        when {
            e == 0.0 -> "无效"
            e > 1 -> "效果绝佳 ${multiplierText(e)}"
            e < 1 -> "效果不好 ${multiplierText(e)}"
            else -> "效果一般"
        }
    when {
        e > 1 -> Pill(label, pal.a(200), pal.a(800))
        e < 1 -> Pill(label, pal.g(200), pal.g(800))
        else -> Pill(label, pal.surface, pal.n(800))
    }
}

// —— Summaries on the page ——

private fun natureAdjust(
    side: JSONObject,
    catalog: CalculatorCatalog,
): Pair<String, String> {
    // Natures are chosen as two independent adjustments (raised / lowered stat). The engine still receives the
    // matching nature name; until both are chosen (and differ) the neutral nature applies.
    val (plus, minus) = catalog.natureEffects[side.getString("nature")] ?: ("atk" to "atk")
    val up =
        if (side.has("up")) {
            side.getString("up")
        } else if (plus != minus) {
            plus
        } else {
            ""
        }
    val down =
        if (side.has("down")) {
            side.getString("down")
        } else if (plus != minus) {
            minus
        } else {
            ""
        }
    return up to down
}

/** Raised and lowered stat pickers, shared with team sets; choosing only one side has no effect. */
@Composable
internal fun StatAdjustFields(
    up: String,
    down: String,
    onChange: (up: String, down: String) -> Unit,
) {
    fun options(
        sign: String,
        exclude: String,
    ) = listOf(SelectOption("", "无")) +
        (1..5).map { calcStats[it] }.filter { it != exclude }.map { SelectOption(it, "$sign${statLabel(it)}") }

    fun label(
        sign: String,
        stat: String,
    ) = if (stat.isEmpty()) "无" else "$sign${statLabel(stat)}"
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectField(
            "能力调整 · 提升 ×1.1",
            label("+", up),
            options("+", down),
            Modifier.weight(1f),
            container = pal.n(100),
        ) { onChange(it, down) }
        SelectField("能力调整 · 降低 ×0.9", label("−", down), options("−", up), Modifier.weight(1f), container = pal.n(100)) { onChange(up, it) }
    }
    if (up.isEmpty() != down.isEmpty()) Text("提升与降低需同时选择才会生效。", fontSize = 11.sp, color = pal.a(800))
}

@Composable
private fun SideSummary(
    title: String,
    color: Color,
    key: String,
    p: Pokemon,
    side: JSONObject,
    dex: Dex,
    catalog: CalculatorCatalog?,
    compact: Boolean,
    onPick: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier,
) {
    val pick = Modifier.clip(InnerShape).clickable(onClickLabel = "选择${title}宝可梦", onClick = onPick).testTag("pick-$key")
    val config: @Composable (Modifier) -> Unit = { m ->
        Column(
            m
                .clip(
                    InnerShape,
                ).background(pal.bg)
                .clickable(onClickLabel = "配置$title", onClick = onConfig)
                .testTag("config-$key")
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dex.abilitiesById[side.getInt("ability")]?.name ?: "—",
                    Modifier.weight(1f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Glyph(GlyphKind.CHEVRON, Modifier.size(14.dp), pal.n(700))
            }
            val item = side.getString("item")
            Text(
                if (item.isEmpty()) "无道具" else catalog?.items?.firstOrNull { it.name == item }?.label ?: item,
                fontSize = 12.5.sp,
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (catalog !=
                null
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (up, down) = natureAdjust(side, catalog)
                    when {
                        up.isNotEmpty() && down.isNotEmpty() -> {
                            Pill("${statLabel(up)} ×1.1", pal.a(200), pal.a(800), fontSize = 10.5.sp)
                            Pill("${statLabel(down)} ×0.9", pal.g(200), pal.g(800), fontSize = 10.5.sp)
                        }

                        up.isEmpty() && down.isEmpty() -> {
                            Pill("无能力调整", pal.surface, pal.n(800), fontSize = 10.5.sp)
                        }

                        else -> {
                            Pill("能力调整未生效", pal.surface, pal.a(800), fontSize = 10.5.sp)
                        }
                    }
                    statuses
                        .firstOrNull {
                            it.first == side.getString("status") && it.first.isNotEmpty()
                        }?.let { Pill(it.second, pal.n(800), pal.bg, fontSize = 10.5.sp) }
                }
            }
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(pal.surface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (compact) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    pick.width(104.dp).fillMaxHeight().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Kicker(title, color = color, spacing = 1.1.sp)
                    Blob(p, 48.dp)
                    Text(
                        p.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    // The column is too narrow for two named types side by side, so they stack.
                    p.types.distinct().forEach { TypeTag(it, expanded = true, horizontal = 3.dp) }
                }
                config(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Kicker(title, color = color, spacing = 1.1.sp)
            }
            Column(pick.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Blob(p, 50.dp)
                    Text(
                        p.displayName,
                        Modifier.weight(1f),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Below the name the types get the card's full width, enough for two names with icons.
                TypeTagRow(p.types)
            }
            Spacer(Modifier.weight(1f))
            config(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryCard(
    kicker: String,
    tag: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp),
            ).background(pal.surface)
            .clickable(onClickLabel = kicker, onClick = onClick)
            .testTag(tag)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(kicker)
            content()
        }
        Glyph(GlyphKind.CHEVRON, Modifier.size(20.dp), pal.n(700))
    }
}

@Composable
private fun FieldSummary(
    field: JSONObject,
    onClick: () -> Unit,
) {
    val pills =
        buildList {
            add(if (field.optString("gameType") == "Doubles") "双打" else "单打")
            add(weathers.firstOrNull { it.first == field.optString("weather") && it.first.isNotEmpty() }?.second ?: "无天气")
            add(terrains.firstOrNull { it.first == field.optString("terrain") && it.first.isNotEmpty() }?.second?.plus("场地") ?: "无场地")
            CalculatorFieldConditions.filter { field.optBoolean(it.first) }.forEach { add(it.second) }
            listOf("attackerSide" to "攻击方", "defenderSide" to "防守方").forEach { (side, label) ->
                val on = CalculatorSideConditions.filter { field.optJSONObject(side)?.optBoolean(it.first) == true }
                if (on.isNotEmpty()) add("$label · " + on.joinToString("、") { it.second })
            }
        }
    SummaryCard("场地", "open-field", onClick) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pills.forEach { Pill(it, pal.bg, pal.text, weight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun MoveSummary(
    move: Move?,
    learned: Set<Int>,
    noMoves: Boolean,
    request: JSONObject,
    compact: Boolean,
    onClick: () -> Unit,
) {
    SummaryCard("招式", "pick-move", onClick) {
        Text(
            move?.name ?: if (noMoves) "没有可用的攻击招式" else "未选择攻击招式",
            fontFamily = HeadingFont,
            fontWeight = HeadingWeight,
            fontSize = if (compact) 19.sp else 22.sp,
            color = if (move != null) pal.text else pal.n(700),
        )
        if (move != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeTag(move.type, icon = 22.dp, horizontal = 10.dp, vertical = 4.dp)
                Pill("${move.categoryLabel} · 威力 ${move.powerLabel}", pal.bg, pal.text, weight = FontWeight.SemiBold)
                if (request.getBoolean("critical")) Pill("会心", pal.a(200), pal.a(800))
                if (request.getInt("hits") > 0) Pill("命中 ${request.getInt("hits")} 次", pal.a(200), pal.a(800))
                if (move.id !in learned) Pill("假设组合", pal.a(200), pal.a(800))
            }
        }
    }
}

// —— Second-level menus ——

@Composable
private fun TypeChips(
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("属性", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        (listOf<Int?>(null) + TypeNames.indices).forEach { t ->
            TypeChoice(t, selected == t, expanded = false) { onSelect(t) }
        }
    }
}

@Composable
private fun CountRow(
    text: String,
    filtered: Boolean,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 12.sp, color = pal.n(700))
        if (filtered) GhostButton("清除筛选", onClear)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String?,
    on: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(on, role = Role.Switch) { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (detail != null) Text(detail, fontSize = 11.sp, color = pal.n(700))
        }
        Switch(
            on,
            null,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = pal.accent,
                    checkedThumbColor = pal.bg,
                    uncheckedTrackColor = pal.n(300),
                    uncheckedThumbColor = pal.bg,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .border(1.dp, pal.divider, shape)
            .background(pal.n(100))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (enabled) pal.text else pal.n(500))
    }
}

@Composable
private fun AnimatedVisibilityScope.PokemonSheet(
    title: String,
    color: Color,
    current: Pokemon,
    dex: Dex,
    team: List<String?>,
    favorites: Set<String>,
    onPick: (String) -> Unit,
    onForm: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    val list =
        remember(dex, query, scope, type, team, favorites) {
            val search = SearchQuery(query)
            val base =
                when (scope) {
                    1 -> team.filterNotNull().distinct().mapNotNull { dex.pokemonById[it] }
                    2 -> dex.pokemon.filter { it.key in favorites }
                    else -> dex.pokemon
                }
            base.filter { search.matches(it.searchText) && (type == null || type in it.types) }
        }
    MenuPanel(title, color, "选择宝可梦", true, onClose, actions = { GhostButton("查看图鉴", onOpen) }) {
        val variants = dex.forms[current.no].orEmpty()
        if (variants.size > 1) {
            item(key = "forms") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetLabel("${current.name} 的形态")
                    PillSegment(
                        variants.map { it.formLabel },
                        variants.indexOfFirst { it.id == current.id },
                        { onForm(variants[it].id) },
                        container = pal.surface,
                        height = 34.dp,
                        fontSize = 12.sp,
                        scrollable = variants.size > 3,
                    )
                }
            }
        }
        item(key = "search") { SearchField(query, { query = it }, "搜索名称、英文或编号", null) }
        item(key = "scope") { PillSegment(listOf("全部", "我的队伍", "收藏"), scope, { scope = it }, height = 36.dp, fontSize = 13.sp) }
        item(key = "types") { TypeChips(type) { type = it } }
        item(key = "count") {
            CountRow("共 ${list.size} 只", scope != 0 || type != null) {
                scope = 0
                type = null
            }
        }
        items(list, key = { it.id }) { p ->
            val on = p.id == current.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(if (on) pal.a(100) else pal.surface)
                    .clickable { onPick(p.id) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Blob(p, 46.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        "${p.number()} · ${p.english}",
                        fontSize = 11.sp,
                        color = pal.n(700),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TypeTagColumn(p.types)
                if (on) Text("当前", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.a(800))
            }
        }
        if (list.isEmpty()) {
            item(key = "empty") {
                EmptyResult(if (scope == 1 && team.all { it == null }) "队伍中还没有宝可梦" else "暂时没有匹配结果") {
                    query = ""
                    scope = 0
                    type = null
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.ConfigSheet(
    title: String,
    color: Color,
    p: Pokemon,
    side: JSONObject,
    stats: List<Int>?,
    dex: Dex,
    catalog: CalculatorCatalog?,
    onEdit: ((JSONObject) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    MenuPanel(title, color, "配置 · ${p.displayName}", true, onClose) {
        item(key = "tabs") { PillSegment(listOf("基础", "能力点", "能力等级", "状态"), tab, { tab = it }, height = 36.dp, fontSize = 13.sp) }
        if (catalog ==
            null
        ) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.accent, trackColor = pal.n(300)) }
            return@MenuPanel
        }
        val (up, down) = natureAdjust(side, catalog)
        when (tab) {
            0 -> {
                item(key = "basic") {
                    val itemOptions =
                        remember(catalog) {
                            listOf(SelectOption("", "无道具")) +
                                catalog.items.map { SelectOption(it.name, it.label, it.name) }
                        }
                    val abilityOptions =
                        remember(dex, p.abilities) {
                            p.abilities.distinct().mapNotNull { dex.abilitiesById[it] }.map {
                                SelectOption(
                                    it.id.toString(),
                                    it.name,
                                    it.english,
                                )
                            }
                        }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectField(
                                "特性",
                                dex.abilitiesById[side.getInt("ability")]?.name ?: "—",
                                abilityOptions,
                                Modifier.weight(1f),
                                container = pal.n(100),
                            ) { v ->
                                onEdit {
                                    it.put("ability", v.toInt())
                                }
                            }
                            SelectField(
                                "道具",
                                itemOptions
                                    .firstOrNull {
                                        it.id == side.getString("item")
                                    }?.label ?: "无道具",
                                itemOptions,
                                Modifier.weight(1f),
                                container = pal.n(100),
                            ) { v -> onEdit { it.put("item", v) } }
                        }
                        StatAdjustFields(up, down) { newUp, newDown ->
                            onEdit { s -> s.put("up", newUp).put("down", newDown).put("nature", catalog.natureFor(newUp, newDown)) }
                        }
                        NumberField("剩余 HP %（1～100）", side.getString("hp"), Modifier.fillMaxWidth()) { v -> onEdit { it.put("hp", v) } }
                    }
                }
            }

            1 -> {
                item(key = "points-head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val total = side.pointTotal()
                        Text(
                            "合计 $total / $POINT_TOTAL",
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (total >
                                    POINT_TOTAL
                                ) {
                                    pal.a(800)
                                } else {
                                    pal.text
                                },
                        )
                        Text("单项 0～32 · 每点 +1 实数值", fontSize = 11.sp, color = pal.n(700))
                    }
                }
                item(key = "points") {
                    fun natureFactor(stat: Int) =
                        when {
                            up.isEmpty() || down.isEmpty() || up == down -> 100

                            calcStats[stat] == up -> 110

                            calcStats[stat] ==
                                down -> 90

                            else -> 100
                        }

                    fun setPoints(
                        stat: Int,
                        value: Int,
                    ) = onEdit { s ->
                        val others = s.pointTotal() - s.points(stat)
                        s.getJSONArray("sp").put(stat, value.coerceIn(0, minOf(32, POINT_TOTAL - others).coerceAtLeast(0)).toString())
                    }
                    Column {
                        calcStats.indices.forEach { i ->
                            val points = side.points(i)
                            val label = StatLabels[i]
                            val actual =
                                stats?.getOrNull(i)?.takeIf { it > 0 } ?: actualStat(p.stats[i], points.coerceIn(0, 32), i, natureFactor(i))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(label, Modifier.width(44.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("实数值 $actual", Modifier.weight(1f), fontSize = 12.sp, color = pal.n(700))
                                StepButton("−", "减少${label}能力点", points > 0) { setPoints(i, points - 1) }
                                BasicTextField(
                                    side.getJSONArray("sp").getString(i),
                                    { v ->
                                        if (v.length <= 3 &&
                                            v.all(Char::isDigit)
                                        ) {
                                            onEdit { it.getJSONArray("sp").put(i, v) }
                                        }
                                    },
                                    Modifier
                                        .width(
                                            52.dp,
                                        ).clip(
                                            RoundedCornerShape(12.dp),
                                        ).background(pal.n(100))
                                        .border(1.dp, pal.divider, RoundedCornerShape(12.dp))
                                        .padding(vertical = 9.dp)
                                        .semantics { contentDescription = "${label}能力点" },
                                    singleLine = true,
                                    textStyle =
                                        TextStyle(
                                            color = pal.text,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        ),
                                    cursorBrush = SolidColor(pal.accent),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                StepButton(
                                    "+",
                                    "增加${label}能力点",
                                    points < 32 && side.pointTotal() < POINT_TOTAL,
                                ) { setPoints(i, points + 1) }
                            }
                            if (i < calcStats.lastIndex) HorizontalDivider(color = pal.divider)
                        }
                    }
                }
            }

            2 -> {
                item(key = "boosts") {
                    Column {
                        (1..5).forEach { i ->
                            val boost = side.getJSONArray("boosts").getInt(i)
                            val label = "${StatLabels[i]}等级"
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                StepButton("−", "降低$label", boost > -6) { onEdit { it.getJSONArray("boosts").put(i, boost - 1) } }
                                Text(
                                    if (boost ==
                                        0
                                    ) {
                                        "±0"
                                    } else {
                                        "%+d".format(boost)
                                    },
                                    Modifier.width(44.dp),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                StepButton("+", "提升$label", boost < 6) { onEdit { it.getJSONArray("boosts").put(i, boost + 1) } }
                            }
                            if (i < 5) HorizontalDivider(color = pal.divider)
                        }
                    }
                }
            }

            else -> {
                item(key = "status") {
                    val genders =
                        when (p.sex) {
                            1 -> {
                                listOf("M" to "雄性")
                            }

                            2 -> {
                                listOf("N" to "无性别")
                            }

                            3 -> {
                                listOf("F" to "雌性")
                            }

                            else -> {
                                listOf("M" to "雄性", "F" to "雌性")
                            }
                        }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetLabel("异常状态")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.forEach { (id, label) ->
                                ToggleTile(
                                    label,
                                    side.getString("status") == id,
                                    { onEdit { it.put("status", id) } },
                                    Modifier.widthIn(min = 64.dp),
                                    off = pal.surface,
                                )
                            }
                        }
                        SheetLabel("性别")
                        PillSegment(
                            genders.map {
                                it.second
                            },
                            genders.indexOfFirst { it.first == side.getString("gender") },
                            { i -> onEdit { it.put("gender", genders[i].first) } },
                            height = 36.dp,
                            fontSize = 13.sp,
                        )
                        val fainted = side.getString("alliesFainted").toIntOrNull() ?: 0
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("已倒下队友（0～5）", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            StepButton("−", "减少已倒下队友", fainted > 0) { onEdit { it.put("alliesFainted", "${fainted - 1}") } }
                            Text(
                                "$fainted",
                                Modifier.width(36.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            StepButton("+", "增加已倒下队友", fainted < 5) { onEdit { it.put("alliesFainted", "${fainted + 1}") } }
                        }
                        SwitchRow("特性条件已触发", "用于引火等特性；威吓等入场效果由内核按双方特性自动处理。", side.getBoolean("abilityOn")) {
                            onEdit { it.put("abilityOn", !it.getBoolean("abilityOn")) }
                        }
                        Text("实数值为能力调整与能力点结算后的基础能力。", fontSize = 11.sp, color = pal.n(700))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.FieldSheet(
    field: JSONObject,
    edit: ((JSONObject) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    MenuPanel("场地", pal.n(700), "天气、场地与双方状态", false, onClose) {
        item(key = "field") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val doubles = field.optString("gameType") == "Doubles"
                SheetLabel("对战规则")
                PillSegment(listOf("单打", "双打"), if (doubles) 1 else 0, { i ->
                    edit {
                        it.put(
                            "gameType",
                            if (i ==
                                1
                            ) {
                                "Doubles"
                            } else {
                                "Singles"
                            },
                        )
                    }
                }, height = 36.dp)
                SheetLabel("天气")
                PillSegment(
                    weathers.map { it.second },
                    weathers.indexOfFirst { it.first == field.optString("weather") }.coerceAtLeast(0),
                    { i -> edit { it.put("weather", weathers[i].first) } },
                    height = 36.dp,
                    fontSize = 12.sp,
                )
                SheetLabel("场地")
                PillSegment(
                    terrains.map { it.second },
                    terrains.indexOfFirst { it.first == field.optString("terrain") }.coerceAtLeast(0),
                    { i -> edit { it.put("terrain", terrains[i].first) } },
                    height = 36.dp,
                    fontSize = 12.sp,
                )
                SheetLabel("全场")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalculatorFieldConditions.forEach { (key, label) ->
                        ToggleTile(
                            label,
                            field.optBoolean(key),
                            { edit { it.put(key, !it.optBoolean(key)) } },
                            Modifier.weight(1f),
                            off = pal.surface,
                        )
                    }
                }
                // Both sides at once, each half a two-column grid, so a screen and its counter can be read together.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("attackerSide" to "攻击方", "defenderSide" to "防守方").forEach { (side, label) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SheetLabel(label)
                            CalculatorSideConditions.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { (key, name) ->
                                        val on = field.optJSONObject(side)?.optBoolean(key) == true
                                        ToggleTile(name, on, {
                                            edit { f ->
                                                val s = f.optJSONObject(side) ?: JSONObject()
                                                s.put(key, !on)
                                                f.put(side, s)
                                            }
                                        }, Modifier.weight(1f).semantics { contentDescription = "$label$name" }, off = pal.surface)
                                    }
                                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Text("天气特性不会自动改写这里的天气与场地。", fontSize = 11.sp, color = pal.n(700))
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.MoveSheet(
    dex: Dex,
    attacker: Pokemon,
    defender: Pokemon,
    request: JSONObject,
    learned: Set<Int>,
    allMoves: Boolean,
    catalog: CalculatorCatalog?,
    onToggleAll: () -> Unit,
    edit: ((JSONObject) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    var stab by rememberSaveable { mutableStateOf(false) }
    var superEffective by rememberSaveable { mutableStateOf(false) }
    val list =
        remember(dex, learned, allMoves, query, category, type, stab, superEffective, attacker.types, defender.types) {
            val search = SearchQuery(query)
            dex.attackingMoves.filter { m ->
                (allMoves || m.id in learned) && search.matches(m.searchText) && (category == 0 || m.category == category - 1) &&
                    (type == null || m.type == type) && (!stab || m.type in attacker.types) &&
                    (!superEffective || effectiveness(m.type, defender.types) > 1)
            }
        }
    val current = request.optInt("move", -1)
    val filtered = category != 0 || type != null || stab || superEffective

    fun clear() {
        category = 0
        type = null
        stab = false
        superEffective = false
    }
    MenuPanel(
        "招式",
        pal.a(700),
        dex.movesById[current]?.let {
            "${it.name} · ${it.categoryLabel} · 威力 ${it.powerLabel}"
        } ?: "选择攻击招式",
        true,
        onClose,
    ) {
        item(key = "switches") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(pal.surface)
                    .padding(horizontal = 14.dp),
            ) {
                SwitchRow("会心一击", null, request.getBoolean("critical")) { edit { it.put("critical", !it.getBoolean("critical")) } }
                HorizontalDivider(color = pal.divider)
                SwitchRow("全部开放攻击招式", "不在学习列表中的招式按假设组合计算", allMoves, onToggleAll)
            }
        }
        val hits = catalog?.moveHits?.get(current)
        if (hits != null && hits.last > 1) {
            item(key = "hits") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetLabel("命中次数")
                    val options = hits.toList()
                    val chosen = request.getInt("hits")
                    PillSegment(
                        listOf("自动") + options.map { "$it 次" },
                        if (chosen == 0) 0 else options.indexOf(chosen) + 1,
                        { i -> edit { it.put("hits", if (i == 0) 0 else options[i - 1]) } },
                        height = 36.dp,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item(key = "search") { SearchField(query, { query = it }, "搜索名称、英文或编号", null) }
        item(key = "category") { PillSegment(listOf("全部", "物理", "特殊"), category, { category = it }, height = 36.dp, fontSize = 13.sp) }
        item(key = "types") { TypeChips(type) { type = it } }
        item(key = "flags") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleTile("本系招式", stab, { stab = !stab }, Modifier.weight(1f), off = pal.surface)
                ToggleTile("对防守方效果绝佳", superEffective, { superEffective = !superEffective }, Modifier.weight(1f), off = pal.surface)
            }
        }
        item(key = "count") { CountRow("共 ${list.size} 个攻击招式", filtered, ::clear) }
        items(list, key = { it.id }) { m ->
            val on = m.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(if (on) pal.a(100) else pal.surface)
                    .clickable {
                        edit { q ->
                            q.put("move", m.id)
                            q.put("hits", 0)
                        }
                        onClose()
                    }.padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TypeTag(m.type, showName = false, icon = MoveTypeIcon)
                Text(
                    m.name,
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.id !in learned) Pill("假设", pal.a(200), pal.a(800), fontSize = 10.sp)
                val (bg, fg) = categoryColors(m.category)
                Text(
                    m.categoryLabel,
                    Modifier
                        .width(40.dp)
                        .clip(PillShape)
                        .background(bg)
                        .padding(vertical = 2.dp),
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(m.powerLabel, Modifier.width(36.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
        if (list.isEmpty()) {
            item(key = "empty") {
                EmptyResult(if (!allMoves && learned.isEmpty()) "此形态没有可用的攻击招式" else "暂时没有匹配结果") {
                    query = ""
                    clear()
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.ResultSheet(
    result: CalcOutcome?,
    onLicense: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    MenuPanel("计算结果", pal.n(700), "伤害分布与说明", false, onClose) {
        item(key = "rolls") {
            if (result !is CalcDamage) {
                Text("暂无可用的计算结果。", fontSize = 13.sp, color = pal.n(800))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.rolls.forEachIndexed { i, values ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(pal.surface)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SheetLabel("第 ${i + 1} 段 · ${values.size} 个等概率随机值（HP）")
                            values.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { v ->
                                        Text(
                                            "$v",
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(pal.bg)
                                                .padding(vertical = 6.dp),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("每段分别列出等概率随机值；多段伤害范围为各段范围之和。假定招式命中；不计气势披带、挺住与回合末效果。", fontSize = 12.sp, lineHeight = 18.sp, color = pal.n(800))
                Text(
                    "计算规则移植自 Smogon damage-calc 的 Champions 模式；种族值、属性、体重及招式数值使用本图鉴数据。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = pal.n(800),
                )
            }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("计算内核与开源许可", onLicense, Modifier.weight(1f), height = 44.dp)
                SecondaryButton("重置全部条件", onReset, Modifier.weight(1f), height = 44.dp)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    change: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), maxLines = 1)
        BasicTextField(
            value,
            { if (it.length <= 3 && it.all(Char::isDigit)) change(it) },
            Modifier
                .fillMaxWidth()
                .clip(
                    PillShape,
                ).background(pal.n(100))
                .border(1.dp, pal.divider, PillShape)
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .semantics { contentDescription = label },
            singleLine = true,
            textStyle = TextStyle(color = pal.text, fontSize = 14.sp),
            cursorBrush = SolidColor(pal.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
fun CalculatorLicense(onBack: () -> Unit) {
    val context = LocalContext.current
    val license by produceState("") {
        value =
            withContext(Dispatchers.IO) {
                context.assets
                    .open("calculator/LICENSE-damage-calc.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }
    }
    Column {
        AppHeader("伤害计算", "计算内核与开源许可", false, onBack)
        LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 32.dp)) {
            item {
                Text(
                    "Smogon damage-calc · Champions\n版本 eb9af6d94e7e\n\n计算规则以 Kotlin 原生移植自该版本，逐项对照原版结果；不代表所有机制已经过 Champions 实机验证。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                GhostButton("damage-calc 对应版本源码 ↗") {
                    openWeb(context, "https://github.com/smogon/damage-calc/tree/eb9af6d94e7ee0ce034d2d6f2d44d4b2d66ff981")
                }
                Text(license, fontSize = 12.sp, lineHeight = 17.sp, color = pal.n(800))
            }
        }
    }
}
