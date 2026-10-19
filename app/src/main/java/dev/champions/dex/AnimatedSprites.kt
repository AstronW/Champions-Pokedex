package dev.champions.dex

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Animated Champions sprites (512 px WebP) fetched from PokeAPI/sprites for the detail page; the bundled PNGs stay
 * the offline fallback. URLs are pinned to one commit, so a cached file never goes stale and is never revalidated.
 * Files live in the app cache, trimmed oldest-used first once they pass [LIMIT_BYTES].
 */
object AnimatedSprites {
    // Commit "Champions (#294)", where generation-ix/champions switched to animated WebP with the same file IDs.
    private const val COMMIT = "ba403973fdf9d0ac59175813861e6a1f8ed5020c"
    private const val BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/$COMMIT/sprites/pokemon/versions/generation-ix/champions/"
    private const val LIMIT_BYTES = 256L * 1024 * 1024

    /** Animation needs [AnimatedImageDrawable]; older systems keep the bundled sprites. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // Downloads outlive the screen that asked, so leaving a page mid-download still fills the cache.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val network = Dispatchers.IO.limitedParallelism(3)
    private val inFlight = ConcurrentHashMap<String, Deferred<File?>>()

    /** Cache name of [p]'s animated sprite, or null when the form has no Champions picture. */
    fun name(
        p: Pokemon,
        shiny: Boolean,
    ): String? =
        when {
            shiny && p.shiny.isNotBlank() -> p.shiny.removeSuffix(".png") + ".webp"
            !shiny && p.sprite.isNotBlank() -> p.sprite.removeSuffix(".png") + ".webp"
            else -> null
        }

    // "shiny_6.webp" is stored upstream as "shiny/6.webp".
    private fun url(name: String) = BASE + name.replaceFirst("shiny_", "shiny/")

    private fun root(context: Context) = File(context.cacheDir, "animated-sprites")

    private fun dir(context: Context) = File(root(context), COMMIT.take(12))

    fun isCached(
        context: Context,
        name: String,
    ) = File(dir(context), name).isFile

    /** Cached file for [name], downloading it first if needed; null when offline or the download failed. */
    suspend fun file(
        context: Context,
        name: String,
    ): File? {
        val cached = File(dir(context), name)
        if (cached.isFile) {
            withContext(Dispatchers.IO) { cached.setLastModified(System.currentTimeMillis()) }
            return cached
        }
        val app = context.applicationContext
        // Started only once registered, so a quick failure cannot leave a finished job behind in the map.
        val job = inFlight.computeIfAbsent(name) { scope.async(network, CoroutineStart.LAZY) { download(app, name) } }
        job.invokeOnCompletion { inFlight.remove(name, job) }
        job.start()
        return job.await()
    }

    /** Fetches [names] one at a time behind the current picture, and only on unmetered networks. */
    suspend fun prefetch(
        context: Context,
        names: List<String>,
    ) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (connectivity == null || connectivity.isActiveNetworkMetered) return
        names.forEach { if (!isCached(context, it)) file(context, it) ?: return }
    }

    /** Bytes the cached animations take on disk. */
    suspend fun cacheSize(context: Context): Long =
        withContext(Dispatchers.IO) { root(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() } }

    /** Deletes every cached animation; pages already showing one keep it until they are left. */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) { root(context).deleteRecursively() }

    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun decode(file: File): AnimatedImageDrawable? =
        withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) as? AnimatedImageDrawable }.getOrNull()
        }

    private fun download(
        context: Context,
        name: String,
    ): File? {
        val dir = dir(context).apply { mkdirs() }
        val target = File(dir, name)
        val part = File(dir, "$name.part")
        return runCatching {
            val connection = URL(url(name)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            // A captive portal or error page must not be cached as a sprite.
            val header = ByteArray(12)
            if (part.inputStream().use { it.read(header) } < 12 || String(header, 0, 4) != "RIFF" ||
                String(header, 8, 4) != "WEBP"
            ) {
                return@runCatching null
            }
            if (!part.renameTo(target)) return@runCatching null
            trim(context)
            target
        }.getOrNull().also { if (it == null) part.delete() }
    }

    /** Drops other commits' folders, then the least recently shown files until the cache fits [LIMIT_BYTES]. */
    private fun trim(context: Context) {
        val current = dir(context)
        current.parentFile
            ?.listFiles()
            ?.filter { it != current }
            ?.forEach { it.deleteRecursively() }
        val files =
            current
                .listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") }
                .orEmpty()
                .sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= LIMIT_BYTES) break
            total -= file.length()
            file.delete()
        }
    }
}
