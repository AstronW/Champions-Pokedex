package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*

/** Segmented pill control. Scrolls horizontally when there are many options. */
@Composable
fun PillSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
                container: Color = pal.surface, height: Dp = 38.dp, fontSize: TextUnit = 13.sp, heading: Boolean = false,
                scrollable: Boolean = options.size > 5) {
    val scroll = scrollable
    Row((if (scroll) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth())
        .clip(PillShape).background(container).padding(3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box((if (scroll) Modifier else Modifier.weight(1f)).heightIn(min = height).clip(PillShape)
                .background(if (on) pal.accent else Color.Transparent)
                .selectable(on, role = Role.Tab) { onSelect(i) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) pal.bg else pal.n(800), fontSize = fontSize, maxLines = 1,
                    fontWeight = if (heading) HeadingWeight else FontWeight.Bold, fontFamily = if (heading) HeadingFont else BodyFont)
            }
        }
    }
}

@Composable
fun ToggleTile(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, off: Color = pal.bg) {
    Box(modifier.heightIn(min = 38.dp).clip(PillShape).background(if (on) pal.g(600) else off)
        .toggleable(on, role = Role.Switch) { onClick() }.padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (on) pal.n(100) else pal.n(800), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    Box(modifier.heightIn(min = height).clip(PillShape).background(pal.accent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center) { Text(text, color = pal.bg, fontSize = 15.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp, leading: (@Composable () -> Unit)? = null) {
    Row(modifier.heightIn(min = height).clip(PillShape).border(1.dp, pal.divider, PillShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        leading?.invoke()
        Text(text, color = pal.text, fontSize = 14.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        color = pal.accent, fontSize = 14.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight)
}

@DexPreviews
@Composable
private fun SegmentsPreview() = PreviewSurface {
    PillSegment(listOf("全部", "物理", "特殊", "变化"), 1, {})
    PillSegment(SortOrder.entries.map { it.label } + listOf("攻击", "特攻"), 2, {})
    PillSegment(listOf("基础", "属性"), 0, {}, heading = true, container = pal.n(200))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleTile("全部形态", true, {}, Modifier.weight(1f))
        ToggleTile("仅看收藏", false, {}, Modifier.weight(1f), off = pal.surface)
    }
}

@DexPreviews
@Composable
private fun ButtonsPreview() = PreviewSurface {
    PrimaryButton("显示 128 个结果", {}, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton("完成", {}, height = 40.dp)
        SecondaryButton("分享图片", {}, leading = { Glyph(GlyphKind.SHARE, Modifier.size(16.dp), pal.text) })
        GhostButton("重置") {}
    }
}
