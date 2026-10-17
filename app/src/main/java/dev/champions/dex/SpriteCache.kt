package dev.champions.dex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decode away from the UI thread. Cache is bounded by bytes, rather than item count, and large enough to hold
 * every form sprite so a list never decodes the same image twice while scrolling.
 */
object SpriteCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
    }
    // A few decoders in parallel keep up with a fling; one shared lock made every row wait for the rows before it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val decoder = Dispatchers.IO.limitedParallelism(3)
    // Hardware bitmaps are uploaded to the GPU while decoding, not on the render thread during the first draw.
    private val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }

    /** Already decoded image, so recycled rows can draw it in their first frame instead of a placeholder. */
    fun cached(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(context: Context, path: String): ImageBitmap? = cache.get(path) ?: withContext(decoder) {
        cache.get(path) ?: runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()?.also { cache.put(path, it) }
        }.getOrNull()
    }

    /** Decodes [paths] ahead of their first use; [onProgress] receives the share finished so far. */
    suspend fun preload(context: Context, paths: List<String>, onProgress: (Float) -> Unit = {}) = coroutineScope {
        val done = AtomicInteger()
        paths.forEach { launch { load(context, it); onProgress(done.incrementAndGet() / paths.size.toFloat()) } }
    }
}
