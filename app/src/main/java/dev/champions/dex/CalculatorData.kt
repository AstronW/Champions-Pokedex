package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject

internal val calcStats = listOf("hp", "atk", "def", "spa", "spd", "spe")
internal const val PointTotal = 66
internal fun statLabel(stat: String) = StatLabels[calcStats.indexOf(stat)]

/** Field toggles the calculator page offers; every key must be one [CalculatorEngine] reads. */
internal val CalculatorFieldConditions = listOf("isGravity" to "重力", "isWonderRoom" to "奇妙空间", "isMagicRoom" to "魔法空间")
/** Per-side toggles. Battery, Power Spot, Steely Spirit and Flower Gift are left out: the Champions rules never read them. */
internal val CalculatorSideConditions = listOf("isReflect" to "反射壁", "isLightScreen" to "光墙", "isAuroraVeil" to "极光幕", "isProtected" to "守住",
    "isTailwind" to "顺风", "isHelpingHand" to "帮助", "isFriendGuard" to "友情防守", "isCharge" to "充电")

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

/** Champions item with the upstream effects the formula reads (type boost, resist berry, Fling power, Mega Stone pairs). */
internal data class CalculatorItem(
    val name: String, val label: String, val boostType: String? = null, val resistType: String? = null, val fling: Int = 0,
    val megaStone: Map<String, String> = emptyMap(),
)

/** Upstream move fields the formula reads; type, category, priority, contact and fixed power come from the dex. */
internal data class CalcMoveData(
    val basePower: Int, val flags: Set<String>, val multihit: IntRange?, val multihitFixed: Boolean, val multiaccuracy: Boolean,
    val secondaries: Boolean, val recoil: Boolean, val hasCrashDamage: Boolean, val willCrit: Boolean, val ignoreDefensive: Boolean,
    val breaksProtect: Boolean, val target: String?, val overrideDefensiveStat: String?, val selfBoosts: Map<String, Int>,
)

private fun JSONObject.strings(): Map<String, String> =
    keys().asSequence().filterNot { isNull(it) }.associateWith { getString(it) }

/** Immutable calculator data (catalog.json) parsed once per ViewModel: UI options plus the upstream rules for [CalculatorEngine]. */
internal class CalculatorCatalog(json: JSONObject) {
    val natureEffects: Map<String, Pair<String, String>> = json.getJSONArray("natures").objects { nature ->
        nature.getString("name") to (nature.getString("plus") to nature.getString("minus"))
    }.toMap()
    val natureById: Map<String, CalcNature> = natureEffects.entries.associate { (name, stats) ->
        toId(name) to CalcNature(calcStats.indexOf(stats.first), calcStats.indexOf(stats.second))
    }
    val items: List<CalculatorItem> = json.getJSONArray("items").objects { item ->
        CalculatorItem(item.getString("name"), item.getString("label"), item.optString("boostType").ifEmpty { null },
            item.optString("resistType").ifEmpty { null }, item.optInt("fling"), item.optJSONObject("megaStone")?.strings().orEmpty())
    }
    val moveHits: Map<Int, IntRange> = json.getJSONObject("moveHits").let { values ->
        values.keys().asSequence().associate { id ->
            val hits = values.getJSONArray(id)
            id.toInt() to (hits.getInt(0)..hits.getInt(1))
        }
    }
    /** Dex form ID → upstream species name. */
    val species: Map<String, String> = json.getJSONObject("species").strings()
    val moves: Map<Int, String> = json.getJSONObject("moves").strings().mapKeys { it.key.toInt() }
    val abilities: Map<Int, String> = json.getJSONObject("abilities").strings().mapKeys { it.key.toInt() }
    val moveData: Map<Int, CalcMoveData> = json.getJSONObject("moveData").let { values ->
        values.keys().asSequence().associate { id ->
            val m = values.getJSONObject(id)
            val hits = m.opt("multihit")
            val flags = m.optJSONArray("flags")
            id.toInt() to CalcMoveData(
                basePower = m.getInt("basePower"),
                flags = flags?.strings()?.toSet().orEmpty(),
                multihit = when (hits) { is JSONArray -> hits.getInt(0)..hits.getInt(1); is Number -> hits.toInt()..hits.toInt(); else -> null },
                multihitFixed = hits is Number, multiaccuracy = m.optBoolean("multiaccuracy"), secondaries = m.optBoolean("secondaries"),
                recoil = m.optBoolean("recoil"), hasCrashDamage = m.optBoolean("hasCrashDamage"), willCrit = m.optBoolean("willCrit"),
                ignoreDefensive = m.optBoolean("ignoreDefensive"), breaksProtect = m.optBoolean("breaksProtect"),
                target = m.optString("target").ifEmpty { null }, overrideDefensiveStat = m.optString("overrideDefensiveStat").ifEmpty { null },
                selfBoosts = m.optJSONObject("selfBoosts")?.let { b -> b.keys().asSequence().associateWith(b::getInt) }.orEmpty(),
            )
        }
    }
    val rules = ChampionsRules(json.getJSONObject("types").let { types ->
        types.keys().asSequence().associateWith { attack ->
            types.getJSONObject(attack).let { row -> row.keys().asSequence().associateWith(row::getDouble) }
        }
    }, items)

    fun natureFor(up: String, down: String): String =
        if (up == down) "Serious"
        else natureEffects.entries.firstOrNull { it.value == (up to down) }?.key ?: "Serious"
}
