package dev.champions.dex

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DexRepository(private val context: Context) {
    suspend fun load(): Dex = withContext(Dispatchers.IO) {
        val j = context.assets.open("dex.json").bufferedReader().use { JSONObject(it.readText()) }
        val mechanics = context.assets.open("mechanics.json").bufferedReader().use { parseMechanics(JSONObject(it.readText())) }
        parseDex(j, mechanics)
    }
}

internal fun parseDex(j: JSONObject, mechanics: Mechanics): Dex {
    val pokemon = j.getJSONArray("pokemon").objects {
        Pokemon(it.getString("id"), it.getInt("no"), it.getInt("formId"), it.getString("name"),
            it.getString("english"), it.getString("form"), it.getString("formEnglish"),
            it.getJSONArray("types").ints(), it.getJSONArray("stats").ints(), it.getJSONArray("abilities").ints(),
            it.getInt("weight"), it.getInt("sex"), it.getString("sprite"), it.getString("shiny"))
    }
    val moves = j.getJSONArray("moves").objects {
        Move(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"),
            it.getBoolean("available"), it.getInt("type"), it.getInt("category"), it.getInt("power"),
            it.getInt("accuracy"), it.getInt("pp"), it.getInt("priority"), it.getBoolean("contact"))
    }
    val abilities = j.getJSONArray("abilities").objects {
        Ability(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"))
    }
    val items = j.getJSONArray("items").objects {
        Item(it.getInt("id"), it.getString("name"), it.getString("english"), it.getString("description"), it.getString("sprite"), it.getInt("sort"),
            it.getJSONArray("holders").strings())
    }
    val learns = j.getJSONObject("learnsets")
    val wiki = j.getJSONArray("wiki").objects {
        WikiPage(it.getString("key"), it.getString("title"), it.getString("revision"), it.getString("url"))
    }.associateBy { it.key }
    val version = j.getJSONObject("version")
    return Dex(pokemon, moves.sortedBy { it.id }, abilities.sortedBy { it.id }, learns.keys().asSequence().associateWith { learns.getJSONArray(it).ints() }, wiki,
        version.getInt("species"), version.getString("champout"), version.getString("pipeline"), version.getString("spritesCommit"), mechanics, items.sortedBy { it.sort })
}
