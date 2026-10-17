package dev.champions.dex.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.TypeNames
import dev.champions.dex.pal
import dev.champions.dex.typeIconColors
import dev.champions.dex.typeIconFilter

/** The type's Champions icon cut to a circle; only [TypeTag] draws it, so every type shows in the same colours. */
@Composable
private fun TypeIcon(
    type: Int,
    modifier: Modifier,
    size: Dp,
    description: String?,
) {
    val bitmap = rememberAsset("types/$type.png")
    val shape = modifier.size(size).clip(CircleShape)
    if (bitmap != null) {
        Image(bitmap, description, shape, contentScale = ContentScale.Fit, colorFilter = typeIconFilter())
    } else {
        Box(
            shape.background(typeIconColors(type).first).then(
                if (description !=
                    null
                ) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
        )
    }
}

/**
 * The one way a type is shown: its Champions icon, followed unless [showName] is off by its name in a capsule of the icon's
 * own colour, so icon and capsule read as one. [expanded] fills the width given with the content centred; otherwise the
 * capsule wraps its content. The icon sits as far from the start as from the top, so it follows the capsule's curve.
 * Without the name the tag is the icon alone, described by the type's name. [trailing] draws after the name in its colour.
 */
@Composable
fun TypeTag(
    type: Int,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    expanded: Boolean = false,
    fontSize: TextUnit = 11.sp,
    icon: Dp = 18.dp,
    horizontal: Dp = 9.dp,
    vertical: Dp = 3.dp,
    suffix: String = "",
    trailing: (@Composable (Color) -> Unit)? = null,
) {
    if (!showName) return TypeIcon(type, modifier, icon, TypeNames[type])
    val (bg, fg) = typeIconColors(type)
    Row(
        (if (expanded) modifier.fillMaxWidth() else modifier)
            .clip(
                PillShape,
            ).background(bg)
            .padding(start = vertical, end = horizontal, top = vertical, bottom = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(icon / 4, Alignment.CenterHorizontally),
    ) {
        TypeIcon(type, Modifier, icon, null)
        // Only an expanded tag shares out its width; a wrapping one may sit in a scrolling row, where a weight would leave the name no room.
        Text(
            TypeNames[type] + suffix,
            if (expanded) Modifier.weight(1f, fill = false) else Modifier,
            color = fg,
            fontSize = fontSize,
            lineHeight =
                fontSize * 1.3f,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(fg)
    }
}

/** A Pokémon's types across the whole width: a single type fills it, two types share it equally. */
@Composable
fun TypeTagRow(
    types: List<Int>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    icon: Dp = 18.dp,
    vertical: Dp = 3.dp,
    gap: Dp = 4.dp,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        types.distinct().forEach {
            TypeTag(it, Modifier.weight(1f), expanded = true, fontSize = fontSize, icon = icon, horizontal = vertical, vertical = vertical)
        }
    }
}

/** Types stacked at the end of a list row, every tag as wide as the longest name so rows line up. */
@Composable
fun TypeTagColumn(
    types: List<Int>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        types.distinct().forEach { TypeTag(it, Modifier.widthIn(min = 64.dp), horizontal = 3.dp) }
    }
}

/**
 * One type filter choice: "全部" when [type] is null, otherwise the type's [TypeTag], centred in the space given.
 * A ring around the choice marks the selected one, so the types keep their own colours.
 */
@Composable
fun TypeChoice(
    type: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(PillShape)
            .border(2.dp, if (selected) pal.text else Color.Transparent, PillShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (type != null) {
            TypeTag(type, expanded = expanded, fontSize = 12.sp, icon = 20.dp, horizontal = 12.dp, vertical = 6.dp)
        } else {
            Text(
                "全部",
                (if (expanded) Modifier.fillMaxWidth() else Modifier)
                    .clip(
                        PillShape,
                    ).background(pal.surface)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = pal.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Grid of 19 type buttons ("全部" + 18 types): four per row on a phone, more where the sheet is wider. */
@Composable
fun TypeGrid(
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val all = listOf<Int?>(null) + TypeNames.indices
    BoxWithConstraints {
        val columns =
            when {
                maxWidth >= 560.dp -> 7
                maxWidth >= 400.dp -> 5
                else -> 4
            }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            all.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { t -> TypeChoice(t, selected == t, Modifier.weight(1f)) { onSelect(t) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A labelled group of type tags; [suffix] adds e.g. a member count after the type name. */
data class TypeGroup(
    val label: String,
    val color: Color,
    val types: List<Int>,
    val suffix: (Int) -> String = { "" },
)

/** Shared layout for type-matchup summaries (Pokémon detail and team analysis). */
@Composable
fun TypeGroupsCard(
    title: String,
    groups: List<TypeGroup>,
    note: String,
    empty: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(title)
        DexCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), spacing = 12.dp) {
            val shown = groups.filter { it.types.isNotEmpty() }
            if (shown.isEmpty() && empty != null) Text(empty, fontSize = 12.sp, color = pal.n(700))
            shown.forEach { g ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.label, Modifier.width(62.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = g.color)
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        g.types.forEach { TypeTag(it, horizontal = 10.dp, vertical = 3.dp, suffix = g.suffix(it)) }
                    }
                }
            }
            Text(note, fontSize = 11.sp, color = pal.n(700))
        }
    }
}

@DexPreviews
@Composable
private fun TypeTagsPreview() =
    PreviewSurface {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TypeNames.indices.forEach { TypeTag(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeNames.indices.take(9).forEach { TypeTag(it, showName = false, icon = 26.dp) }
        }
        TypeTag(9, suffix = " ×2")
        TypeTagRow(PreviewData.charizard.types)
        TypeTagRow(PreviewData.pikachu.types)
        TypeTagColumn(PreviewData.garchomp.types)
    }

@DexPreviews
@Composable
private fun TypeGridPreview() = PreviewSurface { TypeGrid(9) {} }

@DexPreviews
@Composable
private fun TypeGroupsCardPreview() =
    PreviewSurface {
        TypeGroupsCard(
            "属性克制",
            listOf(
                TypeGroup("4倍弱点", pal.a(800), listOf(5)),
                TypeGroup("2倍弱点", pal.a(700), listOf(10, 12)),
                TypeGroup("抵抗", pal.g(700), listOf(1, 6, 8, 9, 17), suffix = { if (it == 6) " ¼" else "" }),
                TypeGroup("免疫", pal.n(700), listOf(4)),
            ),
            "按喷火龙的火、飞行属性计算，未计入特性。",
        )
        TypeGroupsCard("队伍弱点", emptyList(), "队伍为空。", empty = "还没有成员。")
    }
