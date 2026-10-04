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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }

class DexRepository(private val context: Context) {
    suspend fun load(): Dex = withContext(Dispatchers.IO) {
        val j = context.assets.open("dex.json").bufferedReader().use { JSONObject(it.readText()) }
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
        val learns = j.getJSONObject("learnsets")
        val wiki = j.getJSONArray("wiki").mapObjects {
            WikiPage(it.getString("key"), it.getString("file"), it.getString("title"), it.getString("revision"), it.getString("url"))
        }.associateBy { it.key }
        val version = j.getJSONObject("version")
        Dex(pokemon, moves.sortedBy { it.id }, abilities.sortedBy { it.id }, learns.keys().asSequence().associateWith { learns.getJSONArray(it).ints() }, wiki,
            version.getInt("species"), version.getString("champout"), version.getString("pipeline"), version.getString("spritesCommit"))
    }
}

class DexViewModel(application: Application) : AndroidViewModel(application) {
    val calculatorEngine = CalculatorEngine { path -> application.assets.open(path).bufferedReader().use { it.readText() } }
    private val prefs = application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE)
    var dex by mutableStateOf<Dex?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var favorites by mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()); private set
    var appearance by mutableStateOf(runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM)); private set
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduce_motion", false)); private set

    init { load() }
    fun load() {
        viewModelScope.launch {
            error = null
            try { dex = DexRepository(getApplication()).load() }
            catch (e: Exception) { error = "图鉴数据加载失败，请重试。"; return@launch }
            // Prepare the calculator engine in the background once the catalogue is on screen.
            launch(Dispatchers.Default) { runCatching { calculatorEngine.warmUp() } }
        }
    }
    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit { putStringSet("favorites", favorites) }
    }
    /** Six team slots of Pokémon form IDs; empty slots are null. */
    var team by mutableStateOf(prefs.getString("team", "").orEmpty().split(',').let { s -> List(6) { s.getOrNull(it)?.takeIf(String::isNotBlank) } }); private set
    private fun saveTeam(value: List<String?>) { team = value; prefs.edit { putString("team", value.joinToString(",") { it.orEmpty() }) } }
    fun setTeamSlot(index: Int, id: String?) = saveTeam(team.toMutableList().also { it[index] = id })
    /** Returns false when every slot is taken. */
    fun addToTeam(id: String): Boolean {
        val slot = team.indexOf(null)
        if (slot < 0) return false
        setTeamSlot(slot, id); return true
    }
    fun changeAppearance(value: Appearance) { appearance = value; prefs.edit { putString("appearance", value.name) } }
    fun changeMotion(value: Boolean) { reduceMotion = value; prefs.edit { putBoolean("reduce_motion", value) } }
}

/** Decode away from the UI thread. Cache is bounded by bytes, rather than item count. */
object SpriteCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val decodeMutex = Mutex()
    suspend fun load(context: Context, path: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(path) ?: decodeMutex.withLock {
            cache.get(path) ?: runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.also { cache.put(path, it) }
            }.getOrNull()
        }
    }
}
