package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*

private val DexRowShape = RoundedCornerShape(24.dp)

fun Pokemon.number() = "#" + no.toString().padStart(4, '0')

/** [mega] marks a species with a Mega form by the Mega Evolution symbol; [badge] is any other note beside the number. */
@Composable
fun PokemonRow(
    p: Pokemon,
    mega: Boolean,
    badge: String?,
    metricLabel: String,
    metric: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                DexRowShape,
            ).background(
                typeTone(p.types.first()).blob,
            ).clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PokemonArt(p, Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.number(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                if (mega) AssetImage(MEGA_SYMBOL, "可超级进化", Modifier.height(18.dp).aspectRatio(56f / 74f))
                if (badge != null) Pill(badge, pal.bg.copy(alpha = .75f), pal.a(800), fontSize = 10.sp)
            }
            Text(
                p.displayName,
                fontFamily = HeadingFont,
                fontWeight = HeadingWeight,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TypeTagRow(p.types)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FavoriteButton(favorite, onFavorite, size = 36.dp)
            Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.End) {
                Text(metricLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = pal.n(700))
                Text(metric, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun categoryColors(category: Int): Pair<Color, Color> =
    when (category) {
        0 -> pal.a(200) to pal.a(800)
        1 -> pal.g(200) to pal.g(800)
        else -> pal.n(300) to pal.n(800)
    }

/** Move lists show the type as an icon only; inset by the row's vertical padding, it follows the pill's curve. */
val MoveTypeIcon = 26.dp

@Composable
fun MoveRow(
    move: Move,
    showAccuracy: Boolean,
    favorite: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                PillShape,
            ).background(pal.surface)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TypeTag(move.type, showName = false, icon = MoveTypeIcon)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                move.name,
                Modifier.weight(1f, false),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (move.available) pal.text else pal.n(600),
            )
            if (favorite) Glyph(GlyphKind.STAR, Modifier.size(12.dp), pal.accent, filled = true)
            if (!move.available) Text("未开放", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.a(700))
        }
        val (bg, fg) = categoryColors(move.category)
        Text(
            move.categoryLabel,
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
        Text(
            move.powerLabel,
            Modifier.width(if (showAccuracy) 36.dp else 44.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
        if (showAccuracy) {
            Text(
                move.accuracyLabel.removeSuffix("%"),
                Modifier.width(36.dp),
                fontSize = 13.sp,
                color = pal.n(700),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun MoveHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        @Composable fun h(
            t: String,
            m: Modifier,
            a: TextAlign = TextAlign.Start,
        ) = Text(t, m, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = pal.n(700), textAlign = a)
        // Wider than the icon column so larger text stays on one line; it overhangs evenly on both sides.
        h("属性", Modifier.width(MoveTypeIcon).requiredWidth(MoveTypeIcon + 8.dp), TextAlign.Center)
        h("招式", Modifier.weight(1f))
        h("分类", Modifier.width(40.dp), TextAlign.Center)
        h("威力", Modifier.width(36.dp), TextAlign.End)
        h("命中", Modifier.width(36.dp), TextAlign.End)
    }
}

@Composable
fun AbilityRow(
    name: String,
    description: String,
    trailing: String?,
    favorite: Boolean = false,
    onClick: () -> Unit,
    chevron: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(26.dp),
            ).background(pal.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (favorite) Glyph(GlyphKind.STAR, Modifier.size(12.dp), pal.accent, filled = true)
            }
            Text(
                description.replace('\n', ' ').ifBlank { "游戏未提供此条目的说明。" },
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = pal.n(800),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        if (chevron) Glyph(GlyphKind.CHEVRON, Modifier.size(18.dp), pal.n(600))
    }
}

@DexPreviews
@LargeTextPreview
@Composable
private fun PokemonRowPreview() =
    PreviewSurface {
        PokemonRow(
            PreviewData.charizard,
            true,
            null,
            "总和",
            "${PreviewData.charizard.total}",
            favorite = true,
            onClick = {},
            onFavorite = {},
        )
        PokemonRow(PreviewData.megaCharizardX, false, "超级进化", "速度", "100", favorite = false, onClick = {}, onFavorite = {})
        PokemonRow(PreviewData.pikachu, false, null, "编号", "25", favorite = false, onClick = {}, onFavorite = {})
    }

@DexPreviews
@LargeTextPreview
@Composable
private fun MoveRowPreview() =
    PreviewSurface {
        MoveHeaderRow()
        PreviewData.moves.forEachIndexed { i, m -> MoveRow(m, showAccuracy = true, favorite = i == 0) {} }
        MoveRow(PreviewData.earthquake, showAccuracy = false) {}
    }

@DexPreviews
@Composable
private fun AbilityRowPreview() =
    PreviewSurface {
        AbilityRow(PreviewData.blaze.name, PreviewData.blaze.description, "特性 1", favorite = true, onClick = {})
        AbilityRow(PreviewData.solarPower.name, PreviewData.solarPower.description, "隐藏特性", onClick = {}, chevron = true)
        AbilityRow("未知特性", "", null, onClick = {})
    }
