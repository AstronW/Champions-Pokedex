package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*

@Composable
fun Kicker(text: String, color: Color = pal.n(700), modifier: Modifier = Modifier, spacing: TextUnit = 1.sp) =
    Text(text, modifier, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = spacing, color = color)

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp, weight: FontWeight = FontWeight.Bold) =
    Text(text, modifier.clip(PillShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), color = fg,
        fontSize = fontSize, fontWeight = weight, maxLines = 1)

@Composable
fun SheetLabel(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))

@DexPreviews
@Composable
private fun LabelsPreview() = PreviewSurface {
    Kicker("#0006 · 喷火龙")
    SectionHeading("种族值")
    SheetLabel("排序")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("已收藏", pal.a(200), pal.a(900))
        Pill("未开放", pal.n(300), pal.n(800), fontSize = 10.sp)
        Pill("新", pal.g(200), pal.g(800))
    }
}
