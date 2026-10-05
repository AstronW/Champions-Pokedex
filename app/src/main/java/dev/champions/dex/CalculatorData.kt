package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject

internal val calcStats = listOf("hp", "atk", "def", "spa", "spd", "spe")
internal const val PointTotal = 66

internal fun calculatorSide(p: Pokemon) = JSONObject().apply {
    put("id", p.id); put("ability", p.abilities.first()); put("nature", "Serious"); put("item", "")
    put("sp", JSONArray(List(6) { "0" })); put("boosts", JSONArray(List(6) { 0 }))
    put("hp", "100"); put("status", ""); put("gender", when(p.sex) { 2 -> "N"; 3 -> "F"; else -> "M" }); put("abilityOn", false); put("alliesFainted", "0")
}

internal fun calculatorMoveFor(dex: Dex, pokemonId: String, preferredId: Int?): Int? {
    val moves = dex.learnedMoves[pokemonId].orEmpty().filter { it.isAvailableAttack }
    return moves.firstOrNull { it.id == preferredId }?.id ?: moves.firstOrNull()?.id
}

/** Typical spreads: the attacker invests in its attacking stat and speed, the defender in HP and the matching defence (66 points each). */
private fun applyDefaultSpreads(dex: Dex, q: JSONObject) {
    val physical = dex.movesById[q.optInt("move", -1)]?.category != 1
    q.getJSONObject("attacker").put("nature", if (physical) "Adamant" else "Modest")
        .put("sp", JSONArray(listOf(2, if (physical) 32 else 0, 0, if (physical) 0 else 32, 0, 32).map { "$it" }))
    q.getJSONObject("defender").put("nature", if (physical) "Impish" else "Calm")
        .put("sp", JSONArray(listOf(32, 0, if (physical) 32 else 2, 0, if (physical) 2 else 32, 0).map { "$it" }))
}

internal fun defaultCalculation(dex: Dex, initialId: String?): String {
    val attacker = dex.pokemonById[initialId] ?: dex.pokemonById["0006000"] ?: dex.pokemon.first()
    val defender = dex.pokemonById["0003000"] ?: dex.pokemon.last()
    val move = calculatorMoveFor(dex, attacker.id, 53)
    return JSONObject().apply {
        put("attacker", calculatorSide(attacker)); put("defender", calculatorSide(defender)); put("move", move ?: JSONObject.NULL)
        put("critical", false); put("hits", 0); put("field", JSONObject().put("gameType", "Singles"))
        applyDefaultSpreads(dex, this)
    }.toString()
}

internal fun JSONObject.points(i: Int) = getJSONArray("sp").optString(i).toIntOrNull() ?: 0
internal fun JSONObject.pointTotal() = (0..5).sumOf { points(it) }

/** Restore the learnset constraint, clearing hit overrides only when the move changes. */
internal fun limitToLearnedMoves(request: JSONObject, dex: Dex, pokemonId: String) {
    val previous = request.optInt("move", -1)
    val move = calculatorMoveFor(dex, pokemonId, previous)
    if (move != previous) request.put("move", move ?: JSONObject.NULL).put("hits", 0)
}

/** Selecting a species resets its side; switching form preserves its spread, item and conditions. */
internal fun selectCalculatorPokemon(request: JSONObject, dex: Dex, side: String, pokemonId: String, preserveSpread: Boolean, allMoves: Boolean) {
    val fresh = calculatorSide(dex.pokemonById.getValue(pokemonId))
    if (preserveSpread) {
        request.getJSONObject(side).put("id", pokemonId).put("ability", fresh.getInt("ability"))
            .put("gender", fresh.getString("gender")).put("abilityOn", false)
    } else request.put(side, fresh)
    if (side == "attacker" && !allMoves) limitToLearnedMoves(request, dex, pokemonId)
}

/** Swap both Pokémon and the conditions belonging to their respective sides. */
internal fun swapCalculatorSides(request: JSONObject) {
    val attacker = request.getJSONObject("attacker")
    request.put("attacker", request.getJSONObject("defender")).put("defender", attacker)
    val field = request.getJSONObject("field")
    val attackerSide = field.optJSONObject("attackerSide") ?: JSONObject()
    field.put("attackerSide", field.optJSONObject("defenderSide") ?: JSONObject()).put("defenderSide", attackerSide)
}

internal data class CalculatorItem(val name: String, val label: String)

/** Immutable UI metadata parsed once per ViewModel; the engine retains its own raw JSON contract. */
internal class CalculatorCatalog(json: JSONObject) {
    val natureEffects: Map<String, Pair<String, String>> = json.getJSONArray("natures").let { values ->
        (0 until values.length()).associate { i ->
            val nature = values.getJSONObject(i)
            nature.getString("name") to (nature.getString("plus") to nature.getString("minus"))
        }
    }
    val items: List<CalculatorItem> = json.getJSONArray("items").let { values ->
        (0 until values.length()).map { i ->
            val item = values.getJSONObject(i)
            CalculatorItem(item.getString("name"), item.getString("label"))
        }
    }
    val moveHits: Map<Int, IntRange> = json.getJSONObject("moveHits").let { values ->
        values.keys().asSequence().associate { id ->
            val hits = values.getJSONArray(id)
            id.toInt() to (hits.getInt(0)..hits.getInt(1))
        }
    }

    fun natureFor(up: String, down: String): String =
        if (up == down) "Serious"
        else natureEffects.entries.firstOrNull { it.value == (up to down) }?.key ?: "Serious"
}
