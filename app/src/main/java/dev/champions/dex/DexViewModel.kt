package dev.champions.dex

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.champions.dex.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DexViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val calculatorCatalog by lazy {
        application.assets
            .open("calculator/catalog.json")
            .bufferedReader()
            .use { CalculatorCatalog(JSONObject(it.readText())) }
    }

    internal suspend fun loadCalculatorCatalog(): CalculatorCatalog = withContext(Dispatchers.IO) { calculatorCatalog }

    private val prefs = application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE)
    var dex by mutableStateOf<Dex?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Share of catalogue sprites decoded while the start screen is up; null while the data itself loads. */
    var loadProgress by mutableStateOf<Float?>(null)
        private set
    var favorites by mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet())
        private set
    var appearance by mutableStateOf(
        runCatching {
            Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!)
        }.getOrDefault(Appearance.SYSTEM),
    )
        private set
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduce_motion", false))
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            error = null
            loadProgress = null
            val loaded =
                try {
                    DexRepository(getApplication()).load()
                } catch (e: Exception) {
                    error = "图鉴数据加载失败，请重试。"
                    return@launch
                }
            // Every form sprite and type icon is decoded before the catalogue appears, so its first frames and scrolls never wait for images.
            loadProgress = 0f
            SpriteCache.preload(
                getApplication(),
                TypeNames.indices.map { "types/$it.png" } + MEGA_SYMBOL +
                    loaded.pokemon.filter { it.sprite.isNotBlank() }.map { "pokemon/${it.sprite}" },
            ) { loadProgress = it }
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
    var team by mutableStateOf(decodeTeam(prefs.getString("team_sets", null), prefs.getString("team", null)))
        private set

    private fun saveTeam(value: List<TeamMember?>) {
        team = value
        prefs.edit {
            putString("team_sets", encodeTeam(value))
            remove("team")
        }
    }

    fun setTeamMember(
        index: Int,
        member: TeamMember?,
    ) = saveTeam(team.toMutableList().also { it[index] = member })

    /** Puts a fresh set for the catalogue form [id] in the slot; a Mega form starts as its holder with the stone. */
    fun setTeamSlot(
        index: Int,
        id: String,
    ) = setTeamMember(index, dex?.newTeamMember(id))

    /** Returns false when every slot is taken. */
    fun addToTeam(id: String): Boolean {
        val slot = team.indexOf(null)
        if (slot < 0) return false
        setTeamSlot(slot, id)
        return true
    }

    fun changeAppearance(value: Appearance) {
        appearance = value
        prefs.edit { putString("appearance", value.name) }
    }

    fun changeMotion(value: Boolean) {
        reduceMotion = value
        prefs.edit { putBoolean("reduce_motion", value) }
    }
}
