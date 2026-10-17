package dev.champions.dex.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.champions.dex.*

/** Decoded asset image, or null until it has loaded. */
@Composable
fun rememberAsset(path: String): ImageBitmap? {
    val context = LocalContext.current
    // Previews draw before any effect could finish, so they decode at once.
    val preview = LocalInspectionMode.current
    val state = remember(path) { mutableStateOf(SpriteCache.cached(path) ?: if (preview) decodeNow(context, path) else null) }
    if (state.value == null && path.isNotBlank()) LaunchedEffect(path) { state.value = SpriteCache.load(context, path) }
    return state.value
}

/** Plain software decode for previews, which cannot draw the hardware bitmaps [SpriteCache] makes. */
private fun decodeNow(
    context: Context,
    path: String,
): ImageBitmap? =
    if (path.isBlank()) {
        null
    } else {
        runCatching {
            context.assets
                .open(path)
                .use(BitmapFactory::decodeStream)
                ?.asImageBitmap()
        }.getOrNull()
    }

@Composable
fun AssetImage(
    path: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberAsset(path)
    if (bitmap != null) {
        Image(bitmap, description, modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            BallMark(Modifier.fillMaxSize(.6f), pal.text.copy(alpha = .13f))
        }
    }
}

@Composable
fun PokemonArt(
    pokemon: Pokemon,
    modifier: Modifier = Modifier,
    shiny: Boolean = false,
) {
    val file = if (shiny && pokemon.shiny.isNotBlank()) pokemon.shiny else pokemon.sprite
    if (file.isNotBlank()) {
        AssetImage("pokemon/$file", pokemon.form.ifBlank { pokemon.name }, modifier)
    } else {
        Box(modifier.semantics { contentDescription = "暂无形态图片" }, contentAlignment = Alignment.Center) {
            BallMark(Modifier.fillMaxSize(.5f), pal.text.copy(alpha = .16f))
        }
    }
}

/** Sprite on a soft circle tinted by the first type. */
@Composable
fun Blob(
    p: Pokemon,
    size: Dp,
    modifier: Modifier = Modifier,
    imageScale: Float = .9f,
) {
    Box(modifier.size(size).clip(CircleShape).background(typeTone(p.types.first()).blob), contentAlignment = Alignment.Center) {
        PokemonArt(p, Modifier.size(size * imageScale))
    }
}

const val MEGA_SYMBOL = "Mega_Evolution_symbol.png"

@DexPreviews
@Composable
private fun ImagesPreview() =
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PokemonArt(PreviewData.charizard, Modifier.size(72.dp))
            PokemonArt(PreviewData.charizard, Modifier.size(72.dp), shiny = true)
            // A form without its own picture shows the placeholder, never another form's sprite.
            PokemonArt(PreviewData.charizard.copy(sprite = ""), Modifier.size(72.dp))
            AssetImage(MEGA_SYMBOL, "可超级进化", Modifier.height(24.dp).aspectRatio(56f / 74f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewData.pokemon.forEach { Blob(it, 52.dp) }
        }
    }
