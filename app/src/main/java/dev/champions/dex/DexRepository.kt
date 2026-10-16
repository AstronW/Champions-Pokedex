package dev.champions.dex

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }

class DexRepository(private val context: Context) {
    suspend fun load(): Dex = withContext(Dispatchers.IO) {
        val j = context.assets.open("dex.json").bufferedReader().use { JSONObject(it.readText()) }
        val mechanics = context.assets.open("mechanics.json").bufferedReader().use { parseMechanics(JSONObject(it.readText())) }
        parseDex(j, mechanics)
    }
}

internal fun parseDex(j: JSONObject, mechanics: Mechanics): Dex {
    val pokemon = j.getJSONArray("pokemon").mapObjects {
        Pokemon(it.getString("id"), it.getInt("no"), it.getInt("formId"), it.getString("name"),
            it.getString("english"), it.getString("form"), it.getString("formEnglish"),
            it.getJSONArray("types").ints(), it.getJSONArray("stats").ints(), it.getJSONArray("abilities").ints(),
            it.getInt("weight"), it.getInt("sex"), it.getString("sprite"), it.getString("shiny"))
    }
    val moves = j.getJSONArray("moves").mapObjects {
        Move(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"),
            it.getBoolean("available"), it.getInt("type"), it.getInt("category"), it.getInt("power"),
            it.getInt("accuracy"), it.getInt("pp"), it.getInt("priority"), it.getBoolean("contact"))
    }
    val abilities = j.getJSONArray("abilities").mapObjects {
        Ability(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"))
    }
    val items = j.getJSONArray("items").mapObjects {
        Item(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"), it.getString("sprite"), it.getInt("sort"),
            it.getJSONArray("holders").let { h -> (0 until h.length()).map(h::getString) })
    }
    val learns = j.getJSONObject("learnsets")
    val wiki = j.getJSONArray("wiki").mapObjects {
        WikiPage(it.getString("key"), it.getString("title"), it.getString("revision"), it.getString("url"))
    }.associateBy { it.key }
    val version = j.getJSONObject("version")
    return Dex(pokemon, moves.sortedBy { it.id }, abilities.sortedBy { it.id }, learns.keys().asSequence().associateWith { learns.getJSONArray(it).ints() }, wiki,
        version.getInt("species"), version.getString("champout"), version.getString("pipeline"), version.getString("spritesCommit"), mechanics, items.sortedBy { it.sort })
}

class DexViewModel(application: Application) : AndroidViewModel(application) {
    private val calculatorCatalog by lazy {
        application.assets.open("calculator/catalog.json").bufferedReader().use { CalculatorCatalog(JSONObject(it.readText())) }
    }
    internal suspend fun loadCalculatorCatalog(): CalculatorCatalog = withContext(Dispatchers.IO) { calculatorCatalog }
    private val prefs = application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE)
    var dex by mutableStateOf<Dex?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    /** Share of catalogue sprites decoded while the start screen is up; null while the data itself loads. */
    var loadProgress by mutableStateOf<Float?>(null); private set
    var favorites by mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()); private set
    var appearance by mutableStateOf(runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM)); private set
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduce_motion", false)); private set

    init { load() }
    fun load() {
        viewModelScope.launch {
            error = null; loadProgress = null
            val loaded = try { DexRepository(getApplication()).load() }
            catch (e: Exception) { error = "图鉴数据加载失败，请重试。"; return@launch }
            // Every form sprite and type icon is decoded before the catalogue appears, so its first frames and scrolls never wait for images.
            loadProgress = 0f
            SpriteCache.preload(getApplication(), TypeNames.indices.map { "types/$it.png" } + MegaSymbol +
                loaded.pokemon.filter { it.sprite.isNotBlank() }.map { "pokemon/${it.sprite}" }) { loadProgress = it }
            team.map { m -> m?.let(loaded::normalized) }.let { if (it != team) saveTeam(it) }
            dex = loaded
            // The calculator data (a small JSON file) is parsed ahead of time so the calculator opens with a result.
            launch(Dispatchers.IO) { runCatching { calculatorCatalog } }
        }
    }
    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit { putStringSet("favorites", favorites) }
    }
    /** Six team slots with their sets; empty slots are null. Teams saved before sets existed are read from the old ID list. */
    var team by mutableStateOf(decodeTeam(prefs.getString("team_sets", null), prefs.getString("team", null))); private set
    private fun saveTeam(value: List<TeamMember?>) { team = value; prefs.edit { putString("team_sets", encodeTeam(value)); remove("team") } }
    fun setTeamMember(index: Int, member: TeamMember?) = saveTeam(team.toMutableList().also { it[index] = member })
    /** Puts a fresh set for the catalogue form [id] in the slot; a Mega form starts as its holder with the stone. */
    fun setTeamSlot(index: Int, id: String) = setTeamMember(index, dex?.newTeamMember(id))
    /** Returns false when every slot is taken. */
    fun addToTeam(id: String): Boolean {
        val slot = team.indexOf(null)
        if (slot < 0) return false
        setTeamSlot(slot, id); return true
    }
    fun changeAppearance(value: Appearance) { appearance = value; prefs.edit { putString("appearance", value.name) } }
    fun changeMotion(value: Boolean) { reduceMotion = value; prefs.edit { putBoolean("reduce_motion", value) } }
}

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
