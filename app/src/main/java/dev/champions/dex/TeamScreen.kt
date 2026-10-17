package dev.champions.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.*

/** Team menus: add a Pokémon to an empty slot, replace a member (then return to its set), edit a member's set, or share the team. */
private const val TEAM_PICK = "pick:"
private const val TEAM_SWAP = "swap:"
private const val TEAM_SET = "set:"
private const val TEAM_SHARE = "share"

@Composable
fun TeamScreen(
    dex: Dex,
    model: DexViewModel,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var menu by rememberMenuState()
    val sets = remember(dex, model.team) { model.team.map { m -> m?.let { MemberSet(dex, it) } } }
    val members = sets.filterNotNull()
    val close = { menu = "" }
    val panel = menu.takeIf { it.startsWith(TEAM_SET) || it == TEAM_SHARE }.orEmpty()
    BackHandler(panel.isNotEmpty(), close)
    Column(Modifier.fillMaxSize()) {
        AppHeader("双打 · 6 只", DexRoot.TEAM.title, true, onMenu) {
            if (members.isNotEmpty()) {
                SecondaryButton("分享", {
                    menu = TEAM_SHARE
                }, height = 40.dp, leading = { Glyph(GlyphKind.SHARE, Modifier.size(16.dp), pal.text) })
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "head") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "我的队伍 · ${members.size} / $TEAM_SIZE",
                            Modifier.weight(1f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = pal.n(700),
                        )
                        Text(if (members.isEmpty()) "点击空位添加" else "点击成员配置道具与招式", fontSize = 12.sp, color = pal.n(700))
                    }
                }
                item(key = "slots") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sets.withIndex().chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (i, set) ->
                                    TeamSlot(
                                        set,
                                        Modifier.weight(1f),
                                        { menu = "$TEAM_PICK$i" },
                                        { menu = "$TEAM_SET$i" },
                                        { model.setTeamMember(i, null) },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "analysis") { TeamAnalysis(members) }
                item(key = "coverage") { TeamCoverage(members) }
            }
            MenuHost(panel) { open ->
                if (open == TEAM_SHARE) {
                    TeamShareSheet(members, dex, close)
                } else {
                    open.removePrefix(TEAM_SET).toInt().let { slot ->
                        sets[slot]?.let { set ->
                            MemberSheet(slot, set, dex, { model.setTeamMember(slot, it) }, { menu = "$TEAM_SWAP$slot" }, { key ->
                                close()
                                onOpen(key)
                            }, close)
                        }
                    }
                }
            }
        }
    }
    val slot = menu.substringAfter(':').toIntOrNull()
    if (slot != null && panel.isEmpty()) {
        val after = if (menu.startsWith(TEAM_SWAP)) "$TEAM_SET$slot" else ""
        PokemonPickerSheet(if (after.isEmpty()) "添加到队伍" else "更换成员", dex, {
            model.setTeamSlot(slot, it.id)
            menu = after
        }) { menu = after }
    }
}

@Composable
private fun TeamSlot(
    set: MemberSet?,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    if (set == null) {
        val dash = pal.n(400)
        Column(
            modifier
                .heightIn(min = 132.dp)
                .clip(RoundedCornerShape(30.dp))
                .drawBehind {
                    drawRoundRect(
                        dash,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        cornerRadius = CornerRadius(30.dp.toPx()),
                    )
                }.clickable(role = Role.Button, onClickLabel = "添加", onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Glyph(GlyphKind.PLUS, Modifier.size(22.dp), pal.n(700))
            Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        }
    } else {
        Box(
            modifier
                .height(132.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(typeTone(set.form.types.first()).blob)
                .clickable(onClickLabel = "配置道具与招式", onClick = onEdit)
                .testTag("team-slot"),
        ) {
            val p = set.form
            Column(
                Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PokemonArt(p, Modifier.fillMaxWidth().weight(1f))
                Text(
                    p.displayName,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) { p.types.distinct().forEach { TypeTag(it, showName = false, icon = 16.dp) } }
            }
            set.item?.let { item ->
                AssetImage(
                    "items/${item.sprite}.png",
                    item.name,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(pal.bg, CircleShape)
                        .padding(3.dp),
                )
            }
            BadgeIconButton("移除", onRemove, Modifier.align(Alignment.TopEnd)) { Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), pal.n(700)) }
        }
    }
}

/** A member's item, ability, stat adjustment and moves, with the matchups they produce. */
@Composable
private fun AnimatedVisibilityScope.MemberSheet(
    slot: Int,
    set: MemberSet,
    dex: Dex,
    onChange: (TeamMember) -> Unit,
    onSwap: () -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    val m = set.member
    val form = set.form
    val learnable = remember(dex, m.id) { dex.learnedMoves[m.id].orEmpty().filter { it.available } }
    val itemOptions =
        remember(dex, m.id) {
            listOf(SelectOption("", "无道具")) +
                dex.items.filter { it.holders.isEmpty() || m.id in it.holders }.map { SelectOption("${it.id}", it.name, it.english) }
        }
    val abilityOptions =
        remember(dex, m.id) {
            dex.pokemonById
                .getValue(m.id)
                .abilities
                .distinct()
                .mapNotNull {
                    dex.abilitiesById[it]
                }.map { SelectOption("${it.id}", it.name, it.english) }
        }

    fun setMove(
        k: Int,
        id: Int?,
    ) = onChange(
        m.copy(
            moves =
                m.moves.toMutableList().apply {
                    when {
                        id == null -> if (k < size) removeAt(k)
                        k < size -> this[k] = id
                        else -> add(id)
                    }
                },
        ),
    )
    MenuPanel("队伍 · 第 ${slot + 1} 位", pal.a(700), form.displayName, true, onClose, actions = { GhostButton("查看图鉴") { onOpen(form.key) } }) {
        item(key = "member") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Blob(form, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(form.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) { form.types.distinct().forEach { TypeTag(it, horizontal = 8.dp) } }
                }
                SecondaryButton("更换", onSwap, height = 40.dp)
            }
        }
        val variants = dex.forms[form.no].orEmpty()
        if (variants.size > 1) {
            item(key = "forms") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetLabel("形态")
                    PillSegment(
                        variants.map { it.formLabel },
                        variants.indexOfFirst { it.id == form.id },
                        { onChange(dex.withForm(m, variants[it].id)) },
                        container = pal.surface,
                        height = 34.dp,
                        fontSize = 12.sp,
                        scrollable = variants.size > 3,
                    )
                }
            }
        }
        item(key = "basic") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        "特性",
                        dex.abilitiesById[set.ability]?.name ?: "—",
                        abilityOptions,
                        Modifier.weight(1f),
                        container = pal.n(100),
                        enabled = !set.isMega,
                    ) {
                        onChange(m.copy(ability = it.toInt()))
                    }
                    SelectField(
                        "道具",
                        set.item?.name ?: "无道具",
                        itemOptions,
                        Modifier.weight(1f),
                        container = pal.n(100),
                        tag = "team-item",
                    ) {
                        onChange(m.copy(item = it.toIntOrNull()))
                    }
                }
                if (set.isMega) Text("携带超级进化石，按超级进化后的属性与特性分析。", fontSize = 11.sp, color = pal.n(700))
                StatAdjustFields(m.up, m.down) { up, down -> onChange(m.copy(up = up, down = down)) }
            }
        }
        item(key = "moves") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until TEAM_MOVE_SLOTS).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k ->
                            val current = set.moves.getOrNull(k)
                            val options =
                                listOf(SelectOption("", "无")) +
                                    learnable
                                        .filter { it.id == current?.id || it.id !in m.moves }
                                        .map {
                                            SelectOption(
                                                "${it.id}",
                                                it.name,
                                                "${TypeNames[it.type]} · ${it.categoryLabel} · 威力 ${it.powerLabel}",
                                            )
                                        }
                            SelectField(
                                "招式 ${k + 1}",
                                current?.name ?: "未选择",
                                options,
                                Modifier.weight(1f),
                                container = pal.n(100),
                                enabled =
                                    current != null || k == m.moves.size,
                                tag = "team-move-$k",
                            ) { setMove(k, it.toIntOrNull()) }
                        }
                    }
                }
                set.attacks.filter { it.cause != null }.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(a.move.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TypeTag(a.move.type)
                        Text("→", fontSize = 12.sp, color = pal.n(700))
                        TypeTag(a.type)
                        Text(a.cause.orEmpty(), fontSize = 11.sp, color = pal.n(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "taken") {
            val taken = TypeNames.indices.associateWith(set::taken)

            fun multiplier(t: Int) = " " + multiplierText(taken.getValue(t))
            TypeGroupsCard(
                "受到的属性伤害",
                listOf(
                    TypeGroup(
                        "弱点",
                        pal.a(700),
                        TypeNames.indices.filter { taken.getValue(it) > 1 }.sortedByDescending { taken.getValue(it) },
                        ::multiplier,
                    ),
                    TypeGroup(
                        "抵抗",
                        pal.g(700),
                        TypeNames.indices
                            .filter {
                                taken.getValue(it).let { v ->
                                    v > 0 && v < 1
                                }
                            }.sortedBy { taken.getValue(it) },
                        ::multiplier,
                    ),
                    TypeGroup("免疫", pal.n(800), TypeNames.indices.filter { taken.getValue(it) == 0.0 }),
                ),
                "含特性与道具：气球按未被击破、抗性树果按生效一次计算。",
            )
        }
        item(key = "hits") {
            val hits = TypeNames.indices.associateWith(set::hits)
            TypeGroupsCard(
                "招式打击面",
                if (set.attacks.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        TypeGroup("效果绝佳", pal.g(700), TypeNames.indices.filter { hits.getValue(it) > 1 }),
                        TypeGroup("被抵抗", pal.a(700), TypeNames.indices.filter { hits.getValue(it).let { v -> v > 0 && v < 1 } }),
                        TypeGroup("无效", pal.a(800), TypeNames.indices.filter { hits.getValue(it) == 0.0 }),
                    )
                },
                "对单属性取各攻击招式的最高倍率，招式属性已按特性改变。",
                empty = "选择攻击招式后显示打击面。",
            )
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
    TypeGroupsCard(
        "队伍属性分析",
        listOf(
            TypeGroup("共同弱点", pal.a(800), shared, count(weak)),
            TypeGroup(
                "弱点",
                pal.a(700),
                TypeNames.indices
                    .filter {
                        weak.getValue(it) > 0 && it !in shared
                    }.sortedByDescending { weak.getValue(it) },
                count(weak),
            ),
            TypeGroup(
                "抵抗/免疫",
                pal.g(700),
                TypeNames.indices
                    .filter {
                        resist.getValue(it) > 0
                    }.sortedByDescending { resist.getValue(it) },
                count(resist),
            ),
            TypeGroup("无人抵抗", pal.n(800), TypeNames.indices.filter { resist.getValue(it) == 0 && sets.isNotEmpty() }),
        ),
        "类型后的数字为命中弱点或被抵抗的成员数。共同弱点：两只以上成员怕且抵抗者更少。含特性与道具：气球按未被击破、抗性树果按生效一次计算，" +
            "携带超级进化石的成员按超级进化后的属性与特性计算。",
        empty = "添加成员后显示队伍的弱点与抵抗。",
    )
}

/** Offensive coverage against single types; the number after a type is how many members hit it super effectively. */
@Composable
private fun TeamCoverage(sets: List<MemberSet>) {
    val attackers = sets.filter { it.attacks.isNotEmpty() }
    val best = TypeNames.indices.associateWith { t -> attackers.maxOfOrNull { it.hits(t) } ?: 0.0 }
    val strong = TypeNames.indices.associateWith { t -> attackers.count { it.hits(t) > 1 } }
    val unset = sets.size - attackers.size
    TypeGroupsCard(
        "队伍打击面",
        if (attackers.isEmpty()) {
            emptyList()
        } else {
            listOf(
                TypeGroup(
                    "效果绝佳",
                    pal.g(700),
                    TypeNames.indices
                        .filter {
                            strong.getValue(it) > 0
                        }.sortedByDescending { strong.getValue(it) },
                ) { " ${strong.getValue(it)}" },
                TypeGroup("最多一般", pal.n(800), TypeNames.indices.filter { best.getValue(it) == 1.0 }),
                TypeGroup("均被抵抗", pal.a(700), TypeNames.indices.filter { best.getValue(it).let { v -> v > 0 && v < 1 } }),
                TypeGroup("无法命中", pal.a(800), TypeNames.indices.filter { best.getValue(it) == 0.0 }),
            )
        },
        "类型后的数字为能打出效果绝佳的成员数。按招式实际属性计算：冰冻皮肤、龙皮肤等特性会改变一般属性招式，携带超级进化石按超级进化后的特性计算；" +
            "固定伤害招式只计是否无效。" + if (unset > 0) "有 $unset 只成员未选择攻击招式。" else "",
        empty = if (sets.isEmpty()) "添加成员后显示队伍打击面。" else "为成员选择攻击招式后显示打击面。",
    )
}
