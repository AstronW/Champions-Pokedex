package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

internal sealed interface CalcOutcome

internal data class CalcFailure(
    val message: String,
) : CalcOutcome

/** Stats are listed in [calcStats] order. [rolls] keeps each hit's 16 equally likely values separately. */
internal data class CalcDamage(
    val min: Int,
    val max: Int,
    val maxHP: Int,
    val currentHP: Int,
    val percentMin: Double,
    val percentMax: Double,
    val ohko: Double,
    val rolls: List<List<Int>>,
    val attackerStats: List<Int>,
    val defenderStats: List<Int>,
    val hits: Int,
) : CalcOutcome

private class CalcInputException(
    message: String,
) : Exception(message)

private val TypeEnglish =
    listOf(
        "Normal",
        "Fighting",
        "Flying",
        "Poison",
        "Ground",
        "Rock",
        "Bug",
        "Ghost",
        "Steel",
        "Fire",
        "Water",
        "Grass",
        "Electric",
        "Psychic",
        "Ice",
        "Dragon",
        "Dark",
        "Fairy",
    )
private val CategoryEnglish = listOf("Physical", "Special", "Status")
private val FixedGender = mapOf(1 to "M", 2 to "N", 3 to "F")

/** Moves upstream assumes at maximum happiness when they have no base power. */
private val HappinessMoves = setOf("Return", "Frustration", "Pika Papow", "Veevee Volley")

/**
 * Native damage calculator: validates the saved request (the JSON the calculator page edits), maps dex forms and moves
 * to the ported Champions rules in [DamageCalc.kt], and formats the result. Stateless and cheap, so it runs on demand.
 */
internal class CalculatorEngine(
    private val dex: Dex,
    private val catalog: CalculatorCatalog,
) {
    fun calculate(request: String): CalcOutcome = calculate(JSONObject(request))

    fun calculate(request: JSONObject): CalcOutcome =
        try {
            compute(request)
        } catch (e: CalcInputException) {
            CalcFailure(e.message.orEmpty())
        } catch (e: RuntimeException) {
            CalcFailure("此组合无法计算：${e.message ?: e.javaClass.simpleName}")
        }

    private fun fail(message: String): Nothing = throw CalcInputException(message)

    /** JavaScript `Number(value)` restricted to an integer range, with the original messages. */
    private fun integer(
        value: Any?,
        min: Int,
        max: Int,
        label: String,
    ): Int {
        if (value == null || value == JSONObject.NULL || value == "") fail("${label}不能为空")
        val n =
            when (value) {
                is Number -> value.toDouble()
                is Boolean -> if (value) 1.0 else 0.0
                is String -> value.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
                else -> Double.NaN
            }
        if (!n.isFinite() || floor(n) != n || n < min || n > max) fail("${label}须为 $min～$max 的整数")
        return n.toInt()
    }

    private fun Any?.truthy() =
        when (this) {
            null, JSONObject.NULL -> false
            is Boolean -> this
            is Number -> toDouble().let { it != 0.0 && !it.isNaN() }
            is String -> isNotEmpty()
            else -> true
        }

    private fun Any?.idNumber() =
        when (this) {
            is Number -> toInt()
            is String -> trim().toIntOrNull()
            else -> null
        }

    private fun JSONObject.text(key: String) = opt(key)?.takeIf { it.truthy() }?.toString() ?: ""

    private fun makePokemon(s: JSONObject): CalcPokemon {
        val id = s.opt("id")?.toString()
        val p = dex.pokemonById[id]
        val name = catalog.species[id]
        if (p == null || name == null) fail("此形态尚未适配计算器")
        val abilityId = s.opt("ability").idNumber()
        val ability = abilityId?.let { catalog.abilities[it] }
        if (ability == null || abilityId !in p.abilities) fail("请选择此形态的特性")
        val item = s.text("item")
        if (item.isNotEmpty() && !catalog.rules.isItem(item)) fail("道具不在 Champions 计算表中")
        val nature = catalog.natureById[toId(s.opt("nature")?.toString() ?: "undefined")] ?: fail("性格无效")
        FixedGender[p.sex]?.let { if (s.opt("gender") != it) fail("性别与当前形态不符") }
        val sp = IntArray(6)
        val boosts = IntArray(6)
        val spValues = s.optJSONArray("sp")
        val boostValues = s.optJSONArray("boosts")
        for (i in 0 until 6) {
            sp[i] = integer(spValues?.opt(i), 0, 32, "能力点")
            if (i > 0) boosts[i] = integer(boostValues?.opt(i), -6, 6, "能力等级")
        }
        val alliesFainted = integer(s.opt("alliesFainted"), 0, 5, "已倒下队友")
        val species = CalcSpecies(name, p.stats.toIntArray(), p.types.map { TypeEnglish[it] }, p.weight / 10.0)
        val maxHP = if (p.stats[0] == 1) 1 else p.stats[0] + sp[0] + 75
        val hp = integer(s.opt("hp"), 1, 100, "剩余 HP 百分比")
        return CalcPokemon(
            species,
            ability,
            s.opt("abilityOn").truthy(),
            alliesFainted,
            item,
            s.text("gender").ifEmpty { "M" },
            nature,
            sp,
            boosts,
            max(1, floor(maxHP * hp / 100.0).toInt()),
            s.text("status"),
        )
    }

    private fun makeMove(
        q: JSONObject,
        attacker: CalcPokemon,
    ): CalcMove {
        val m = q.opt("move").idNumber()?.let { dex.movesById[it] }
        val name = m?.let { catalog.moves[it.id] }
        if (m == null || !m.available || name == null) fail("该招式未开放或尚未适配")
        if (m.category == 2) fail("变化招式不直接计算伤害，请选择攻击招式")
        val data = catalog.moveData[m.id] ?: fail("缺少招式机制数据")
        val requested = q.opt("hits")
        if (requested.truthy()) catalog.moveHits.getValue(m.id).let { integer(requested, it.first, it.last, "命中次数") }
        val hitsOption = if (requested.truthy()) integer(requested, 1, 10, "命中次数") else 0
        val hits =
            when {
                data.multihit == null -> 1
                data.multiaccuracy && data.multihitFixed -> hitsOption.takeIf { it > 0 } ?: data.multihit.last
                data.multihitFixed -> data.multihit.last
                hitsOption > 0 -> hitsOption
                attacker.ability == "Skill Link" -> data.multihit.last
                else -> data.multihit.first + 1
            }
        val category = CategoryEnglish[m.category]
        // Power 1 marks a dynamic power in the dex; the upstream rule decides it instead of using 1 BP.
        var bp = if (m.power > 1 || m.power == 0) m.power else data.basePower
        if (bp == 0 && name in HappinessMoves) bp = 102
        return CalcMove(
            name = name,
            bp = bp,
            type = if (name == "Struggle") "???" else TypeEnglish[m.type],
            category = category,
            flags = (data.flags + listOfNotNull("contact".takeIf { m.contact })).toMutableSet(),
            secondaries = data.secondaries,
            target = data.target ?: "any",
            recoil = data.recoil,
            hasCrashDamage = data.hasCrashDamage,
            isCrit = q.opt("critical").truthy() || data.willCrit,
            priority = m.priority,
            dropsStats = selfDrop(category, data.selfBoosts),
            ignoreDefensive = data.ignoreDefensive,
            overrideDefensiveStat = data.overrideDefensiveStat,
            breaksProtect = data.breaksProtect,
            hits = hits,
        )
    }

    private fun makeSide(s: JSONObject?): CalcSide {
        if (s == null) return CalcSide()

        fun on(key: String) = s.opt(key).truthy()
        return CalcSide(
            spikes = s.opt("spikes").takeIf { it.truthy() }.idNumber() ?: 0,
            isSR = on("isSR"),
            isReflect = on("isReflect"),
            isLightScreen = on("isLightScreen"),
            isProtected = on("isProtected"),
            isCharge = on("isCharge"),
            isTailwind = on("isTailwind"),
            isHelpingHand = on("isHelpingHand"),
            isPowerTrick = on("isPowerTrick"),
            isFriendGuard = on("isFriendGuard"),
            isAuroraVeil = on("isAuroraVeil"),
            isSwitching = s.opt("isSwitching") as? String,
        )
    }

    private fun makeField(f: JSONObject?): CalcField {
        if (f == null) return CalcField()

        fun on(key: String) = f.opt(key).truthy()
        return CalcField(
            gameType =
                f.text("gameType").ifEmpty {
                    "Singles"
                },
            weather = f.text("weather").ifEmpty { null },
            terrain = f.text("terrain").ifEmpty { null },
            isMagicRoom = on("isMagicRoom"),
            isWonderRoom = on("isWonderRoom"),
            isGravity = on("isGravity"),
            isFairyAura = on("isFairyAura"),
            isDarkAura = on("isDarkAura"),
            attackerSide = makeSide(f.optJSONObject("attackerSide")),
            defenderSide = makeSide(f.optJSONObject("defenderSide")),
        )
    }

    private fun compute(q: JSONObject): CalcOutcome {
        val attacker = makePokemon(q.optJSONObject("attacker") ?: fail("此形态尚未适配计算器"))
        val defender = makePokemon(q.optJSONObject("defender") ?: fail("此形态尚未适配计算器"))
        val move = makeMove(q, attacker)
        val damage = calculateDamage(catalog.rules, attacker, defender, move, makeField(q.optJSONObject("field")))
        val (min, max) = damage.range() ?: fail("此组合没有有效的计算结果")
        // Independent per-hit rolls, never zipped into 16 invented totals.
        val rolls: List<List<Int>> =
            when (damage) {
                is Damage.Fixed -> listOf(listOf(damage.value))
                is Damage.Rolls -> if (damage.values.size < 16) damage.values.map { listOf(it) } else listOf(damage.values)
                is Damage.Matrix -> damage.rows.map { (it as Damage.Rolls).values }
            }
        // Sums are visited in ascending order, as JavaScript iterates integer keys, so the floating totals match exactly.
        var distribution = TreeMap(mapOf(0 to 1.0))
        for (row in rolls) {
            val next = TreeMap<Int, Double>()
            for ((sum, p) in distribution) for (v in row) next[sum + v] = (next[sum + v] ?: 0.0) + p / row.size
            distribution = next
        }
        var ohko = 0.0
        for ((sum, p) in distribution) if (sum >= defender.curHP()) ohko += p
        val maxHP = defender.maxHP()
        return CalcDamage(
            min = min,
            max = max,
            maxHP = maxHP,
            currentHP = defender.curHP(),
            percentMin = floor(min * 1000.0 / maxHP) / 10,
            percentMax = floor(max * 1000.0 / maxHP) / 10,
            ohko = (ohko * 1000).roundToLong() / 10.0,
            rolls = rolls,
            attackerStats = attacker.rawStats.toList(),
            defenderStats = defender.rawStats.toList(),
            hits = move.hits,
        )
    }
}

internal fun CalcDamage.toJson(): JSONObject =
    JSONObject().apply {
        put("min", min)
        put("max", max)
        put("maxHP", maxHP)
        put("currentHP", currentHP)
        put("percentMin", percentMin)
        put("percentMax", percentMax)
        put("ohko", ohko)
        put("rolls", JSONArray(rolls.map { JSONArray(it) }))
        put("attackerStats", JSONObject(calcStats.zip(attackerStats).toMap()))
        put("defenderStats", JSONObject(calcStats.zip(defenderStats).toMap()))
        put("hits", hits)
    }
