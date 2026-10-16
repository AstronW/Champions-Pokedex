package dev.champions.dex.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*

/** Sends a short message to the app-wide toast. */
val LocalToast = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun EmptyResult(text: String = "暂时没有匹配结果", onClear: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BallMark(Modifier.size(48.dp), pal.n(400))
        Spacer(Modifier.height(16.dp))
        Text(text, fontSize = 14.sp, color = pal.n(700))
        if (onClear != null) Box(Modifier.padding(top = 8.dp)) { GhostButton("清除筛选", onClear) }
    }
}

@DexPreviews
@Composable
private fun EmptyResultPreview() = PreviewSurface {
    EmptyResult()
    EmptyResult("没有符合条件的招式") {}
}
