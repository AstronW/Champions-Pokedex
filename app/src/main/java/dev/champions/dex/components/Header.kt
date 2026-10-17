package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.pal

@Composable
fun AppHeader(
    kicker: String,
    title: String,
    isRoot: Boolean,
    onNav: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // At least 56 dp; with large system text the kicker and title grow the bar instead of being clipped.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(pal.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundIconButton(if (isRoot) "导航" else "返回", onNav) { Glyph(if (isRoot) GlyphKind.MENU else GlyphKind.BACK, color = pal.text) }
        Column(Modifier.weight(1f)) {
            if (kicker.isNotEmpty()) {
                Text(
                    kicker,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = pal.a(700),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                Modifier.semantics {
                    heading()
                },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

@DexPreviews
@LargeTextPreview
@Composable
private fun AppHeaderPreview() =
    PreviewSurface(padding = PaddingValues(0.dp)) {
        AppHeader("资料", "宝可梦", true, {})
        AppHeader("#0006", "超级喷火龙Ｘ", false, {}) { FavoriteButton(true, {}) }
    }
