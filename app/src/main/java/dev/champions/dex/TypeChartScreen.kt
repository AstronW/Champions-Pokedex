package dev.champions.dex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.*
import kotlin.math.roundToInt

private fun multiplierLabel(m: Double) =
    when (m) {
        2.0 -> "效果绝佳 ×2"
        .5 -> "效果不好 ×½"
        0.0 -> "没有效果 ×0"
        else -> "效果一般 ×1"
    }

@Composable
fun TypeChartScreen(onMenu: () -> Unit) {
    var attack by rememberSaveable { mutableStateOf<Int?>(null) }
    var defend by rememberSaveable { mutableStateOf<Int?>(null) }
    // Tapping the current choice again (cell, row or column header) clears it.
    val pick = { at: Int?, dt: Int? ->
        if ((at == null || at == attack) && dt == defend) {
            if (at != null) attack = null
            defend = null
        } else {
            if (at != null) attack = at
            defend = dt
        }
    }
    val clear = {
        attack = null
        defend = null
    }
    val hint = "行 = 攻击属性，列 = 防守属性。点击格子高亮所在行与列，点击行首只看该攻击属性；再次点击取消。"
    val matrix: @Composable () -> Unit = {
        TypeMatrix(
            attack,
            defend,
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(pal.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            pick,
        )
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("", DexRoot.TYPES.title, true, onMenu)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A wide, short window cannot show the chart and its results one above the other, so the results take their own column.
            val chartPane = minOf(560.dp, maxWidth * .6f)
            if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.width(chartPane).fillMaxHeight().testTag("type-chart-list"),
                        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { matrix() }
                        item { TypeLegend(attack != null || defend != null, clear) }
                        item { Text(hint, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = pal.n(700)) }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight().testTag("type-result-list"),
                        contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        attack?.let { at -> defend?.let { dt -> item { Matchup(at, dt) } } }
                        attack?.let { at -> item { TypeDetailCard(at) } }
                        if (attack == null) {
                            item {
                                DexCard(spacing = 6.dp) {
                                    Kicker("属性详情")
                                    Text(
                                        defend?.let { "已选防守属性 ${TypeNames[it]}，再点一行查看攻防组合。" } ?: "点击表格中的格子或行首，查看攻防组合与属性详情。",
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = pal.n(800),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("type-chart-list"),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
}

@Composable
private fun TypeLegend(
    selected: Boolean,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(pal.g(400) to "×2", pal.a(300) to "×½", pal.n(800) to "×0").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Text(l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
            }
        }
        Spacer(Modifier.weight(1f))
        if (selected) GhostButton("清除选择", onClear)
    }
}

@Composable
private fun Matchup(
    attack: Int,
    defend: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                PillShape,
            ).background(pal.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("type-matchup"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TypeTag(attack)
        Text("→", fontSize = 13.sp, color = pal.n(700))
        TypeTag(defend)
        Text(
            multiplierLabel(effectiveness(attack, defend)),
            Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TypeDetailCard(attack: Int) {
    DexCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeTag(attack, fontSize = 13.sp, icon = 22.dp, horizontal = 14.dp, vertical = 4.dp)
            Text("属性详情", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        }
        val all = TypeNames.indices
        listOf(
            "攻击时 · 效果绝佳 ×2" to all.filter { effectiveness(attack, it) == 2.0 },
            "攻击时 · 效果不好 ×½" to all.filter { effectiveness(attack, it) == .5 },
            "攻击时 · 无效 ×0" to all.filter { effectiveness(attack, it) == 0.0 },
            "防守时 · 弱点" to all.filter { effectiveness(it, attack) == 2.0 },
            "防守时 · 抵抗 / 免疫" to all.filter { effectiveness(it, attack) < 1.0 },
        ).forEach { (label, list) ->
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
private fun TypeMatrix(
    attack: Int?,
    defend: Int?,
    modifier: Modifier,
    onPick: (Int?, Int?) -> Unit,
) {
    val n = TypeNames.size
    val measurer = rememberTextMeasurer()
    val iconBgs = TypeNames.indices.map { typeIconColors(it).first }
    val iconFilter = typeIconFilter()
    val icons = TypeNames.indices.map { rememberAsset("types/$it.png") }
    val colors = listOf(pal.g(400), pal.a(300), pal.n(800), pal.n(100))
    val text = pal.text
    val light = pal.n(100)
    val cellStyle = TextStyle(fontFamily = BodyFont, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
    val marks = remember(measurer) { listOf("2", "½", "0").map { measurer.measure(it, cellStyle) } }
    val pick by rememberUpdatedState(onPick)
    val summary =
        when {
            attack != null && defend != null -> "${TypeNames[attack]} 攻击 ${TypeNames[defend]}：${multiplierLabel(
                effectiveness(attack, defend),
            )}"

            attack != null -> "已选攻击属性 ${TypeNames[attack]}"

            defend != null -> "已选防守属性 ${TypeNames[defend]}"

            else -> "属性克制表，点击格子查看攻防组合"
        }
    BoxWithConstraints(modifier) {
        val gap = 1.5.dp
        val head = 20.dp
        val cell = (maxWidth - head - gap * n) / n
        // Header icons are as wide as a cell (at most the header column), the same size along both axes.
        val icon = minOf(cell, head)
        val headRow = icon + 2.dp
        Canvas(
            Modifier
                .size(maxWidth, headRow + (cell + gap) * n)
                .semantics { contentDescription = summary }
                .testTag("type-matrix")
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val g = gap.toPx()
                        val c = cell.toPx()
                        val step = c + g
                        val col = ((o.x - head.toPx() - g) / step).toInt().takeIf { o.x > head.toPx() + g && it < n }
                        val row = ((o.y - headRow.toPx() - g) / step).toInt().takeIf { o.y > headRow.toPx() + g && it < n }
                        when {
                            row != null && col != null -> pick(row, col)
                            row != null -> pick(row, null)
                            col != null -> pick(null, col)
                        }
                    }
                },
        ) {
            val g = gap.toPx()
            val c = cell.toPx()
            val step = c + g
            val x0 = head.toPx() + g
            val y0 = headRow.toPx() + g
            val radius = CornerRadius(5.dp.toPx())

            fun DrawScope.centered(
                layout: TextLayoutResult,
                cx: Float,
                cy: Float,
                color: androidx.compose.ui.graphics.Color,
                alpha: Float,
            ) = drawText(layout, color, Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f), alpha)

            // Cut to a circle like every other type icon; a plain type-coloured dot stands in until the icon has loaded.
            fun DrawScope.typeIcon(
                t: Int,
                cx: Float,
                cy: Float,
                alpha: Float,
            ) {
                val r = icon.toPx() / 2
                val bitmap = icons[t] ?: return drawCircle(iconBgs[t], r, Offset(cx, cy), alpha)
                clipPath(Path().apply { addOval(Rect(Offset(cx, cy), r)) }) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
                        dstSize =
                            IntSize(
                                (2 * r).roundToInt(),
                                (
                                    2 *
                                        r
                                ).roundToInt(),
                            ),
                        alpha = alpha,
                        colorFilter = iconFilter,
                    )
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
                    val kind =
                        when (effectiveness(at, dt)) {
                            2.0 -> 0
                            .5 -> 1
                            0.0 -> 2
                            else -> 3
                        }
                    drawRoundRect(colors[kind], Offset(x, y), Size(c, c), radius, alpha = alpha)
                    if (kind < 3) centered(marks[kind], x + c / 2, y + c / 2, if (kind == 2) light else text, alpha)
                    if (at == attack &&
                        dt == defend
                    ) {
                        drawRoundRect(
                            text,
                            Offset(x + 1.dp.toPx(), y + 1.dp.toPx()),
                            Size(c - 2.dp.toPx(), c - 2.dp.toPx()),
                            radius,
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
