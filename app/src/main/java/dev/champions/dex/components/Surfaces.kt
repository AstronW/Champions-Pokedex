package dev.champions.dex.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.LocalReduceMotion
import dev.champions.dex.StatLabels
import dev.champions.dex.pal

val PillShape = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(32.dp)

@Composable
fun DexCard(
    modifier: Modifier = Modifier,
    color: Color = pal.surface,
    padding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Animated horizontal bar. */
@Composable
fun Bar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    track: Color = pal.n(200),
    fromEnd: Boolean = false,
) {
    val animated by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        tween(if (LocalReduceMotion.current) 0 else 450),
        label = "bar",
    )
    Box(
        modifier
            .height(height)
            .clip(PillShape)
            .background(track),
        contentAlignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(PillShape)
                .background(color),
        )
    }
}

@Composable
fun statColor(v: Int) =
    when {
        v >= 120 -> pal.g(600)
        v >= 100 -> pal.g(400)
        v >= 80 -> pal.a(400)
        else -> pal.a(300)
    }

@DexPreviews
@Composable
private fun DexCardPreview() =
    PreviewSurface {
        DexCard {
            SectionHeading("种族值")
            PreviewData.garchomp.stats.forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(StatLabels[i], Modifier.width(36.dp), fontSize = 12.sp, color = pal.n(700))
                    Text("$v", Modifier.width(32.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Bar(v / 180f, statColor(v), Modifier.weight(1f))
                }
            }
        }
        DexCard(color = pal.a(100), spacing = 4.dp) {
            Text("右侧对齐的条", fontSize = 12.sp, color = pal.n(700))
            Bar(.62f, pal.accent, Modifier.fillMaxWidth(), height = 8.dp, fromEnd = true)
        }
    }
