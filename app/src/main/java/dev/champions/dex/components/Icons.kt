package dev.champions.dex.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.champions.dex.*

@Composable
fun BallMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        val radius = size.minDimension * .43f
        val width = size.minDimension * .085f
        drawCircle(color, radius, style = Stroke(width))
        drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), width)
        drawCircle(color, radius * .29f, style = Stroke(width))
    }
}

enum class GlyphKind { MENU, BACK, STAR, FILTER, SWAP, CLOSE, PLUS, CHEVRON, SEARCH, SHARE }

@Composable
fun Glyph(
    kind: GlyphKind,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    filled: Boolean = false,
) {
    Canvas(modifier.size(22.dp)) {
        val u = size.minDimension / 24f

        fun p(
            x: Float,
            y: Float,
        ) = Offset(x * u, y * u)
        val stroke = Stroke(2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
        ) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)

        fun poly(
            vararg pts: Float,
            close: Boolean = false,
            fill: Boolean = false,
        ) {
            val path =
                Path().apply {
                    moveTo(pts[0] * u, pts[1] * u)
                    for (i in 2 until pts.size step 2) lineTo(pts[i] * u, pts[i + 1] * u)
                    if (close) close()
                }
            if (fill) drawPath(path, color, style = Fill)
            drawPath(path, color, style = stroke)
        }
        when (kind) {
            GlyphKind.MENU -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 14f, 12f)
                line(4f, 18f, 20f, 18f)
            }

            GlyphKind.BACK -> {
                poly(15f, 18f, 9f, 12f, 15f, 6f)
            }

            GlyphKind.CHEVRON -> {
                poly(9f, 18f, 15f, 12f, 9f, 6f)
            }

            GlyphKind.STAR -> {
                poly(
                    12f,
                    2f,
                    15.09f,
                    8.26f,
                    22f,
                    9.27f,
                    17f,
                    14.14f,
                    18.18f,
                    21.02f,
                    12f,
                    17.77f,
                    5.82f,
                    21.02f,
                    7f,
                    14.14f,
                    2f,
                    9.27f,
                    8.91f,
                    8.26f,
                    close = true,
                    fill = filled,
                )
            }

            GlyphKind.FILTER -> {
                line(21f, 4f, 14f, 4f)
                line(10f, 4f, 3f, 4f)
                line(21f, 12f, 12f, 12f)
                line(8f, 12f, 3f, 12f)
                line(21f, 20f, 16f, 20f)
                line(12f, 20f, 3f, 20f)
                line(14f, 2f, 14f, 6f)
                line(8f, 10f, 8f, 14f)
                line(16f, 18f, 16f, 22f)
            }

            GlyphKind.SWAP -> {
                poly(8f, 3f, 4f, 7f, 8f, 11f)
                line(4f, 7f, 20f, 7f)
                poly(16f, 21f, 20f, 17f, 16f, 13f)
                line(20f, 17f, 4f, 17f)
            }

            GlyphKind.CLOSE -> {
                line(18f, 6f, 6f, 18f)
                line(6f, 6f, 18f, 18f)
            }

            GlyphKind.PLUS -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }

            GlyphKind.SEARCH -> {
                drawCircle(color, 8f * u, p(11f, 11f), style = stroke)
                line(21f, 21f, 16.7f, 16.7f)
            }

            GlyphKind.SHARE -> {
                listOf(p(18f, 5f), p(6f, 12f), p(18f, 19f)).forEach { drawCircle(color, 3f * u, it, style = stroke) }
                line(8.6f, 13.5f, 15.4f, 17.5f)
                line(15.4f, 6.5f, 8.6f, 10.5f)
            }
        }
    }
}

@Composable
fun RoundIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A small round badge (e.g. remove) drawn at [visual] size inside a [touch]-sized target, so it stays easy to hit. */
@Composable
fun BadgeIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visual: Dp = 26.dp,
    touch: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(touch)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visual).background(pal.bg, CircleShape), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun FavoriteButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    RoundIconButton(if (selected) "取消收藏" else "收藏", onClick, modifier, size) {
        Glyph(
            GlyphKind.STAR,
            Modifier.size(
                if (size <
                    40.dp
                ) {
                    18.dp
                } else {
                    22.dp
                },
            ),
            if (selected) pal.accent else pal.n(500),
            filled = selected,
        )
    }
}

@DexPreviews
@Composable
private fun GlyphsPreview() =
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphKind.entries.forEach { Glyph(it, color = pal.text) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(GlyphKind.STAR, color = pal.accent, filled = true)
            BallMark(Modifier.size(32.dp))
            BallMark(Modifier.size(32.dp), pal.n(400))
        }
    }

@DexPreviews
@Composable
private fun IconButtonsPreview() =
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("导航", {}) { Glyph(GlyphKind.MENU, color = pal.text) }
            RoundIconButton("分享", {}, Modifier.background(pal.surface, CircleShape)) { Glyph(GlyphKind.SHARE, color = pal.text) }
            Box(Modifier.background(pal.surface, CircleShape)) {
                BadgeIconButton("移除", {}) { Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), pal.n(700)) }
            }
            FavoriteButton(false, {})
            FavoriteButton(true, {})
            FavoriteButton(true, {}, size = 36.dp)
        }
    }
