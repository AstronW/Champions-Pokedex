package dev.champions.dex.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.champions.dex.AnimatedSprites
import dev.champions.dex.LocalReduceMotion
import dev.champions.dex.Pokemon
import dev.champions.dex.SpriteCache
import dev.champions.dex.pal
import dev.champions.dex.typeTone
import kotlin.math.roundToInt

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

/**
 * Detail-page art: the bundled sprite at once, replaced by the animated Champions sprite from [AnimatedSprites] once
 * it has downloaded and decoded. Once it shows, [prefetch] (cache names) is fetched behind it. [onLoading] reports
 * whether a download is under way, so the caller can place its own indicator.
 */
@Composable
fun AnimatedPokemonArt(
    pokemon: Pokemon,
    modifier: Modifier = Modifier,
    shiny: Boolean = false,
    prefetch: List<String> = emptyList(),
    onLoading: (Boolean) -> Unit = {},
) {
    val name = AnimatedSprites.name(pokemon, shiny)
    if (name == null || !AnimatedSprites.supported || LocalInspectionMode.current) {
        PokemonArt(pokemon, modifier, shiny)
    } else {
        AnimatedArt(name, pokemon, shiny, prefetch, onLoading, modifier)
    }
}

private class Shown(
    val name: String,
    val drawable: AnimatedImageDrawable,
)

// The bundled sprite, shown until the animation is ready and kept when it cannot be fetched.
private data class Bundled(
    val pokemon: Pokemon,
    val shiny: Boolean,
)

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun AnimatedArt(
    name: String,
    pokemon: Pokemon,
    shiny: Boolean,
    prefetch: List<String>,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<Shown?>(null) }
    val loading by rememberUpdatedState(onLoading)
    val queue by rememberUpdatedState(prefetch)
    LaunchedEffect(name) {
        // A cached animation decodes within a frame or two, so the previous one stays up instead of a bundled flash.
        if (shown?.name != name && !AnimatedSprites.isCached(context, name)) {
            shown = null
            loading(true)
        }
        // Not in a finally: a cancelled load must not clear the flag the next picture has just raised.
        shown = AnimatedSprites.file(context, name)?.let { AnimatedSprites.decode(it) }?.let { Shown(name, it) }
        loading(false)
        AnimatedSprites.prefetch(context, queue - name)
    }
    val state: Any = shown ?: Bundled(pokemon, shiny)
    val description = pokemon.form.ifBlank { pokemon.name }
    Crossfade(state, modifier, tween(if (LocalReduceMotion.current) 0 else 220), label = "art") {
        when (it) {
            is Shown -> AnimatedDrawableImage(it.drawable, description, Modifier.fillMaxSize())
            is Bundled -> PokemonArt(it.pokemon, Modifier.fillMaxSize(), it.shiny)
        }
    }
}

/** Plays [drawable] in place; under reduced motion it holds the first frame. */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun AnimatedDrawableImage(
    drawable: AnimatedImageDrawable,
    description: String?,
    modifier: Modifier,
) {
    val painter = remember(drawable) { AnimatedDrawablePainter(drawable) }
    val reduceMotion = LocalReduceMotion.current
    DisposableEffect(drawable, reduceMotion) {
        drawable.callback = painter.callback
        if (!reduceMotion) drawable.start()
        onDispose {
            drawable.stop()
            drawable.callback = null
        }
    }
    Image(painter, description, modifier, contentScale = ContentScale.Fit)
}

/** Draws an [AnimatedImageDrawable], redrawing whenever it reports a new frame. */
@RequiresApi(Build.VERSION_CODES.P)
private class AnimatedDrawablePainter(
    private val drawable: AnimatedImageDrawable,
) : Painter() {
    private var frame by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    // Drawable keeps only a weak reference to its callback, so the painter holds it.
    val callback =
        object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                frame++
            }

            override fun scheduleDrawable(
                who: Drawable,
                what: Runnable,
                time: Long,
            ) {
                handler.postAtTime(what, time)
            }

            override fun unscheduleDrawable(
                who: Drawable,
                what: Runnable,
            ) {
                handler.removeCallbacks(what)
            }
        }

    override val intrinsicSize = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())

    override fun DrawScope.onDraw() {
        frame
        drawIntoCanvas {
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(it.nativeCanvas)
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
            // Previews and offline devices show the bundled sprite in place of the animation.
            AnimatedPokemonArt(PreviewData.charizard, Modifier.size(72.dp))
            AssetImage(MEGA_SYMBOL, "可超级进化", Modifier.height(24.dp).aspectRatio(56f / 74f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewData.pokemon.forEach { Blob(it, 52.dp) }
        }
    }
