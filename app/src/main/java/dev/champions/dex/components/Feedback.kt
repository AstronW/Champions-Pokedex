package dev.champions.dex.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.pal

/** Sends a short message to the app-wide toast. */
val LocalToast = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun EmptyResult(
    text: String = "暂时没有匹配结果",
    onClear: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BallMark(Modifier.size(48.dp), pal.n(400))
        Spacer(Modifier.height(16.dp))
        Text(text, fontSize = 14.sp, color = pal.n(700))
        if (onClear != null) Box(Modifier.padding(top = 8.dp)) { GhostButton("清除筛选", onClear) }
    }
}

@DexPreviews
@Composable
private fun EmptyResultPreview() =
    PreviewSurface {
        EmptyResult()
        EmptyResult("没有符合条件的招式") {}
    }
